package com.termux.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Minimal OpenAI Responses <-> Chat Completions compatibility layer for Codex. */
final class ChatCompletionsAdapter {
    static final class ChatResult {
        final byte[] body;
        final String contentType;
        ChatResult(byte[] body, String contentType) { this.body=body; this.contentType=contentType; }
    }
    private static final class ToolCall {
        String id="", name="", arguments="";
    }
    private static final class CachedResponse {
        String id="", model="", reasoning="";
        final LinkedHashMap<String,JSONObject> calls=new LinkedHashMap<>();
    }
    private static final class ConversionState {
        JSONArray pendingToolCalls=new JSONArray();
        final StringBuilder pendingReasoning=new StringBuilder();
        JSONObject lastAssistant;
    }
    private static final int MAX_CACHED_RESPONSES=512;
    private static final Object HISTORY_LOCK=new Object();
    private static final LinkedHashMap<String,CachedResponse> RESPONSE_HISTORY=new LinkedHashMap<>(16,0.75f,true);
    private static final Map<String,String> CALL_RESPONSE_INDEX=new HashMap<>();

    static byte[] responsesRequestToChat(byte[] source) throws Exception {
        JSONObject in=new JSONObject(new String(source,StandardCharsets.UTF_8));
        JSONObject out=new JSONObject();
        copy(in,out,"model"); copy(in,out,"temperature"); copy(in,out,"top_p"); copy(in,out,"parallel_tool_calls"); copy(in,out,"service_tier"); copy(in,out,"prompt_cache_key");
        String model=in.optString("model");
        if(in.has("max_output_tokens")) out.put(usesMaxCompletionTokens(model)?"max_completion_tokens":"max_tokens",in.get("max_output_tokens"));
        JSONObject reasoning=in.optJSONObject("reasoning");
        if(reasoning!=null&&!reasoning.optString("effort").isEmpty()) out.put("reasoning_effort",transportReasoningEffort(reasoning.optString("effort")));
        JSONArray messages=new JSONArray();
        String instructions=in.optString("instructions"); if(!instructions.isEmpty()) messages.put(new JSONObject().put("role","system").put("content",instructions));
        Object input=in.opt("input");
        if(input instanceof String) messages.put(new JSONObject().put("role","user").put("content",input));
        else if(input instanceof JSONObject) convertInput(enrichInputWithHistory(in,new JSONArray().put(input)),messages);
        else if(input instanceof JSONArray) convertInput(enrichInputWithHistory(in,(JSONArray)input),messages);
        out.put("messages",messages);
        JSONArray tools=in.optJSONArray("tools"); if(tools!=null) out.put("tools",convertTools(tools));
        Object choice=in.opt("tool_choice"); if(choice!=null) out.put("tool_choice",convertToolChoice(choice));
        out.put("stream",true); out.put("stream_options",new JSONObject().put("include_usage",true));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String transportReasoningEffort(String effort) {
        return "ultra".equalsIgnoreCase(effort == null ? "" : effort.trim()) ? "max" : (effort == null ? "" : effort.trim().toLowerCase(java.util.Locale.US));
    }

    static String toolReasoningSummary(byte[] chatRequest) {
        int toolTurns=0,reasoningTurns=0,placeholders=0;
        try { JSONArray messages=new JSONObject(new String(chatRequest,StandardCharsets.UTF_8)).optJSONArray("messages"); if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject message=messages.optJSONObject(i);if(message==null||!"assistant".equals(message.optString("role")))continue;JSONArray calls=message.optJSONArray("tool_calls");if(calls==null||calls.length()==0)continue;toolTurns++;String reasoning=message.optString("reasoning_content").trim();if(!reasoning.isEmpty())reasoningTurns++;if("tool call".equals(reasoning))placeholders++;} } catch(Exception ignored) {}
        return "assistantToolTurns="+toolTurns+" reasoningContent="+reasoningTurns+" placeholders="+placeholders;
    }

    static boolean usesMaxCompletionTokens(String model) {
        String normalized=model==null?"":model.trim().toLowerCase(java.util.Locale.US);
        int slash=normalized.lastIndexOf('/'); if(slash>=0) normalized=normalized.substring(slash+1);
        return normalized.startsWith("gpt-5")||normalized.startsWith("o1")||normalized.startsWith("o3")||normalized.startsWith("o4");
    }

    private static void convertInput(JSONArray input, JSONArray messages) throws Exception {
        ConversionState state=new ConversionState();
        for(int i=0;i<input.length();i++) {
            JSONObject item=input.optJSONObject(i); if(item==null) continue; String type=item.optString("type");
            if("reasoning".equals(type)) {
                String value=extractReasoningText(item);
                if(state.pendingToolCalls.length()>0) appendReasoning(state.pendingReasoning,value);
                else if(!attachReasoning(state.lastAssistant,value)) appendReasoning(state.pendingReasoning,value);
            } else if("function_call".equals(type)||"custom_tool_call".equals(type)) {
                appendReasoning(state.pendingReasoning,extractReasoningText(item));
                JSONObject fn=new JSONObject().put("name",item.optString("name")).put("arguments","custom_tool_call".equals(type)?new JSONObject().put("input",item.optString("input")).toString():item.optString("arguments","{}"));
                JSONObject tc=new JSONObject().put("id",item.optString("call_id",item.optString("id","call_"+shortId()))).put("type","function").put("function",fn);
                state.pendingToolCalls.put(tc);
            } else if("function_call_output".equals(type)||"custom_tool_call_output".equals(type)) {
                flushPendingToolCalls(messages,state);
                messages.put(new JSONObject().put("role","tool").put("tool_call_id",item.optString("call_id")).put("content",stringValue(item.opt("output"))));
            } else if("message".equals(type)||"agent_message".equals(type)||item.has("role")) {
                flushPendingToolCalls(messages,state);
                String role=normalizeRole(item.optString("role","user"));
                JSONObject m=new JSONObject().put("role",role).put("content",convertContent(item.opt("content")));
                if(item.has("tool_calls")) m.put("tool_calls",item.get("tool_calls"));
                if(item.has("reasoning_content")) m.put("reasoning_content",item.get("reasoning_content"));
                if("assistant".equals(role)) {
                    attachPendingReasoning(m,state.pendingReasoning);
                    state.lastAssistant=m;
                } else {
                    state.pendingReasoning.setLength(0);
                    state.lastAssistant=null;
                }
                messages.put(m);
            }
        }
        flushPendingToolCalls(messages,state);
        ensureToolCallReasoning(messages);
    }

    private static void flushPendingToolCalls(JSONArray messages,ConversionState state) throws Exception {
        if(state.pendingToolCalls.length()==0)return;
        JSONObject assistant=new JSONObject().put("role","assistant").put("content",JSONObject.NULL).put("tool_calls",state.pendingToolCalls);
        attachPendingReasoning(assistant,state.pendingReasoning);
        if(assistant.optString("reasoning_content").trim().isEmpty())assistant.put("reasoning_content","tool call");
        messages.put(assistant); state.lastAssistant=assistant; state.pendingToolCalls=new JSONArray();
    }

    private static void attachPendingReasoning(JSONObject assistant,StringBuilder pending) throws Exception {
        String value=pending.toString().trim(); pending.setLength(0); if(!value.isEmpty())assistant.put("reasoning_content",value);
    }

    private static boolean attachReasoning(JSONObject assistant,String value) throws Exception {
        if(assistant==null||value==null||value.trim().isEmpty()||!"assistant".equals(assistant.optString("role")))return false;
        String existing=assistant.optString("reasoning_content"); assistant.put("reasoning_content",existing.isEmpty()?value.trim():existing+"\n"+value.trim()); return true;
    }

    private static void appendReasoning(StringBuilder target,String value) {
        if(value==null||value.trim().isEmpty())return; String normalized=value.trim(); if(target.toString().equals(normalized))return; if(target.length()>0)target.append('\n'); target.append(normalized);
    }

    private static void ensureToolCallReasoning(JSONArray messages) throws Exception {
        for(int i=0;i<messages.length();i++) { JSONObject m=messages.optJSONObject(i); if(m==null||!"assistant".equals(m.optString("role")))continue; JSONArray calls=m.optJSONArray("tool_calls"); if(calls!=null&&calls.length()>0&&m.optString("reasoning_content").trim().isEmpty())m.put("reasoning_content","tool call"); }
    }

    private static String extractReasoningText(JSONObject item) {
        String direct=stringValue(item.opt("reasoning_content")).trim(); if(!direct.isEmpty())return direct;
        Object reasoning=item.opt("reasoning"); String fromReasoning=extractText(reasoning); if(!fromReasoning.isEmpty())return fromReasoning;
        String summary=extractText(item.opt("summary")); if(!summary.isEmpty())return summary;
        return "reasoning".equals(item.optString("type"))?extractText(item.opt("content")):"";
    }

    private static String extractText(Object value) {
        if(value==null||value==JSONObject.NULL)return ""; if(value instanceof String)return (String)value;
        StringBuilder out=new StringBuilder();
        if(value instanceof JSONObject) { JSONObject object=(JSONObject)value; for(String key:new String[]{"text","content","reasoning_content"}) { String text=stringValue(object.opt(key)); if(!text.isEmpty()){if(out.length()>0)out.append('\n');out.append(text);} } }
        else if(value instanceof JSONArray) { JSONArray array=(JSONArray)value; for(int i=0;i<array.length();i++){String text=extractText(array.opt(i));if(!text.isEmpty()){if(out.length()>0)out.append('\n');out.append(text);}} }
        return out.toString();
    }

    private static JSONArray enrichInputWithHistory(JSONObject request,JSONArray input) throws Exception {
        String previousId=request.optString("previous_response_id"),model=request.optString("model"); CachedResponse previous=cachedResponse(previousId,model);
        JSONArray normalized=new JSONArray();
        for(int i=0;i<input.length();i++) {
            JSONObject item=input.optJSONObject(i); if(item==null){normalized.put(input.opt(i));continue;} String type=item.optString("type"),callId=callId(item);
            if(("function_call".equals(type)||"custom_tool_call".equals(type))&&!callId.isEmpty()) {
                CachedResponse cached=previous!=null&&previous.calls.containsKey(callId)?previous:cachedResponseForCall(callId,model);
                JSONObject cachedCall=cached==null?null:cached.calls.get(callId);
                if(cachedCall!=null) {
                    JSONObject merged=new JSONObject(cachedCall.toString()); Iterator<String> keys=item.keys();
                    while(keys.hasNext()){String key=keys.next();Object value=item.opt(key);if(isMeaningful(value)||!merged.has(key))merged.put(key,value);}
                    if(cached.reasoning!=null&&!cached.reasoning.isEmpty()&&merged.optString("reasoning_content").isEmpty())merged.put("reasoning_content",cached.reasoning);
                    item=merged;
                }
            }
            normalized.put(item);
        }
        LinkedHashMap<String,Boolean> outputs=new LinkedHashMap<>(); Set<String> existingCalls=new HashSet<>();
        for(int i=0;i<normalized.length();i++){JSONObject item=normalized.optJSONObject(i);if(item==null)continue;String type=item.optString("type"),callId=callId(item);if(("function_call".equals(type)||"custom_tool_call".equals(type))&&!callId.isEmpty())existingCalls.add(callId);else if(("function_call_output".equals(type)||"custom_tool_call_output".equals(type))&&!callId.isEmpty())outputs.put(callId,Boolean.TRUE);}
        Set<String> missingCalls=new HashSet<>(outputs.keySet()); missingCalls.removeAll(existingCalls); if(missingCalls.isEmpty())return normalized;
        LinkedHashMap<String,CachedResponse> responseByOutput=new LinkedHashMap<>();
        for(String callId:outputs.keySet()){CachedResponse cached=previous!=null&&previous.calls.containsKey(callId)?previous:cachedResponseForCall(callId,model);if(cached!=null)responseByOutput.put(callId,cached);}
        if(responseByOutput.isEmpty())return normalized;
        JSONArray enriched=new JSONArray(); Set<String> injectedResponses=new HashSet<>();
        for(int i=0;i<normalized.length();i++) {
            JSONObject item=normalized.optJSONObject(i); String callId=item==null?"":callId(item),type=item==null?"":item.optString("type");
            boolean isOutput="function_call_output".equals(type)||"custom_tool_call_output".equals(type);
            CachedResponse cached=isOutput?responseByOutput.get(callId):null;
            if(cached!=null&&injectedResponses.add(cached.id)) {
                for(Map.Entry<String,JSONObject> entry:cached.calls.entrySet())if(missingCalls.contains(entry.getKey())){JSONObject restored=new JSONObject(entry.getValue().toString());if(!cached.reasoning.isEmpty())restored.put("reasoning_content",cached.reasoning);enriched.put(restored);}
            }
            enriched.put(item==null?normalized.opt(i):item);
        }
        return enriched;
    }

    private static String callId(JSONObject item) { return item.optString("call_id",item.optString("id")); }

    private static boolean isMeaningful(Object value) {
        if(value==null||value==JSONObject.NULL)return false; if(value instanceof String)return !((String)value).isEmpty(); return true;
    }

    private static CachedResponse cachedResponse(String responseId,String model) {
        if(responseId==null||responseId.isEmpty())return null; synchronized(HISTORY_LOCK){CachedResponse cached=RESPONSE_HISTORY.get(responseId);return modelMatches(cached,model)?cached:null;}
    }

    private static CachedResponse cachedResponseForCall(String callId,String model) {
        synchronized(HISTORY_LOCK){String responseId=CALL_RESPONSE_INDEX.get(callId);CachedResponse cached=responseId==null?null:RESPONSE_HISTORY.get(responseId);return modelMatches(cached,model)?cached:null;}
    }

    private static boolean modelMatches(CachedResponse cached,String model) { return cached!=null&&(model==null||model.isEmpty()||cached.model.isEmpty()||model.equalsIgnoreCase(cached.model)); }

    private static Object convertContent(Object content) throws Exception {
        if(content instanceof String) return content;
        if(!(content instanceof JSONArray)) return "";
        JSONArray source=(JSONArray)content, parts=new JSONArray(); StringBuilder textOnly=new StringBuilder(); boolean rich=false;
        for(int i=0;i<source.length();i++) {
            JSONObject p=source.optJSONObject(i); if(p==null) continue; String type=p.optString("type");
            if("input_text".equals(type)||"output_text".equals(type)||"text".equals(type)) { String t=p.optString("text"); textOnly.append(t); parts.put(new JSONObject().put("type","text").put("text",t)); }
            else if("encrypted_content".equals(type)) { String t=p.optString("encrypted_content",p.optString("text")); textOnly.append(t); parts.put(new JSONObject().put("type","text").put("text",t)); }
            else if("input_image".equals(type)||"image_url".equals(type)) { rich=true; Object url=p.opt("image_url"); if(url==null) url=p.opt("url"); parts.put(new JSONObject().put("type","image_url").put("image_url",url)); }
        }
        return rich?parts:textOnly.toString();
    }
    private static JSONArray convertTools(JSONArray tools) throws Exception {
        JSONArray result=new JSONArray();
        for(int i=0;i<tools.length();i++) { JSONObject t=tools.optJSONObject(i); if(t==null) continue; String type=t.optString("type");
            if("function".equals(type)) { JSONObject fn=new JSONObject().put("name",t.optString("name")).put("description",t.optString("description")); fn.put("parameters",t.optJSONObject("parameters")!=null?t.optJSONObject("parameters"):new JSONObject().put("type","object").put("properties",new JSONObject())); result.put(new JSONObject().put("type","function").put("function",fn)); }
            else if("custom".equals(type)) { String name=t.optString("name","custom_tool"); JSONObject params=new JSONObject().put("type","object").put("properties",new JSONObject().put("input",new JSONObject().put("type","string"))).put("required",new JSONArray().put("input")); result.put(new JSONObject().put("type","function").put("function",new JSONObject().put("name",name).put("description",t.optString("description")).put("parameters",params))); }
        }
        return result;
    }
    private static Object convertToolChoice(Object value) throws Exception {
        if(!(value instanceof JSONObject)) return value;
        JSONObject v=(JSONObject)value; if("function".equals(v.optString("type"))) return new JSONObject().put("type","function").put("function",new JSONObject().put("name",v.optString("name")));
        return value;
    }

    static int agentMessageCount(byte[] responsesRequest) {
        try {
            Object input=new JSONObject(new String(responsesRequest,StandardCharsets.UTF_8)).opt("input");
            if(input instanceof JSONObject) return "agent_message".equals(((JSONObject)input).optString("type"))?1:0;
            if(!(input instanceof JSONArray)) return 0;
            int count=0; JSONArray array=(JSONArray)input;
            for(int i=0;i<array.length();i++) { JSONObject item=array.optJSONObject(i); if(item!=null&&"agent_message".equals(item.optString("type")))count++; }
            return count;
        } catch(Exception ignored) { return 0; }
    }

    static Set<String> customToolNames(byte[] responsesRequest) {
        Set<String> names=new HashSet<>(); try { JSONArray tools=new JSONObject(new String(responsesRequest,StandardCharsets.UTF_8)).optJSONArray("tools"); if(tools!=null)for(int i=0;i<tools.length();i++){JSONObject t=tools.optJSONObject(i);if(t!=null&&"custom".equals(t.optString("type")))names.add(t.optString("name"));} } catch(Exception ignored) {} return names;
    }
    static ChatResult chatResponseToResponses(byte[] source, String contentType, String fallbackModel) throws Exception { return chatResponseToResponses(source,contentType,fallbackModel,new HashSet<>()); }
    static ChatResult chatResponseToResponses(byte[] source, String contentType, String fallbackModel, Set<String> customTools) throws Exception {
        String raw=new String(source,StandardCharsets.UTF_8); String model=fallbackModel; String responseId="resp_"+shortId(); StringBuilder text=new StringBuilder(); StringBuilder reasoning=new StringBuilder(); Map<Integer,ToolCall> tools=new LinkedHashMap<>(); JSONObject usage=null;
        if(contentType!=null&&contentType.toLowerCase().contains("text/event-stream")||raw.contains("data:")) {
            String[] lines=raw.split("\\r?\\n");
            for(String line:lines) { if(!line.startsWith("data:")) continue; String data=line.substring(5).trim(); if(data.isEmpty()||"[DONE]".equals(data)) continue;
                JSONObject chunk; try{chunk=new JSONObject(data);}catch(Exception ignored){continue;} if(!chunk.optString("model").isEmpty())model=chunk.optString("model"); if(chunk.optJSONObject("usage")!=null)usage=chunk.optJSONObject("usage");
                JSONArray choices=chunk.optJSONArray("choices"); if(choices==null||choices.length()==0)continue; JSONObject delta=choices.optJSONObject(0).optJSONObject("delta"); if(delta==null)continue;
                appendValue(text,delta.opt("content")); appendValue(reasoning,delta.opt("reasoning_content")); mergeToolCalls(tools,delta.optJSONArray("tool_calls"));
            }
        } else {
            JSONObject root=new JSONObject(raw); if(!root.optString("model").isEmpty())model=root.optString("model"); usage=root.optJSONObject("usage"); JSONArray choices=root.optJSONArray("choices");
            if(choices!=null&&choices.length()>0){JSONObject message=choices.optJSONObject(0).optJSONObject("message"); if(message!=null){appendValue(text,message.opt("content"));appendValue(reasoning,message.opt("reasoning_content"));mergeToolCalls(tools,message.optJSONArray("tool_calls"));}}
        }
        JSONObject response=buildResponse(responseId,model,text.toString(),reasoning.toString(),tools,usage,customTools);
        recordResponse(response);
        return new ChatResult(buildEventStream(response).getBytes(StandardCharsets.UTF_8),"text/event-stream; charset=utf-8");
    }

    private static void recordResponse(JSONObject response) {
        try {
            CachedResponse cached=new CachedResponse(); cached.id=response.optString("id"); cached.model=response.optString("model"); if(cached.id.isEmpty())return;
            StringBuilder reasoning=new StringBuilder(); JSONArray output=response.optJSONArray("output");
            if(output!=null)for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null)continue;String type=item.optString("type");if("reasoning".equals(type))appendReasoning(reasoning,extractReasoningText(item));else if("function_call".equals(type)||"custom_tool_call".equals(type)){String callId=item.optString("call_id",item.optString("id"));if(!callId.isEmpty())cached.calls.put(callId,new JSONObject(item.toString()));}}
            if(cached.calls.isEmpty())return; cached.reasoning=reasoning.toString();
            synchronized(HISTORY_LOCK){
                CachedResponse replaced=RESPONSE_HISTORY.remove(cached.id); if(replaced!=null)removeCallIndexes(replaced);
                RESPONSE_HISTORY.put(cached.id,cached); for(String callId:cached.calls.keySet())CALL_RESPONSE_INDEX.put(callId,cached.id);
                while(RESPONSE_HISTORY.size()>MAX_CACHED_RESPONSES){Iterator<Map.Entry<String,CachedResponse>> iterator=RESPONSE_HISTORY.entrySet().iterator();if(!iterator.hasNext())break;CachedResponse removed=iterator.next().getValue();iterator.remove();removeCallIndexes(removed);}
            }
        } catch(Exception ignored) {}
    }

    private static void removeCallIndexes(CachedResponse cached) { for(String callId:cached.calls.keySet())if(cached.id.equals(CALL_RESPONSE_INDEX.get(callId)))CALL_RESPONSE_INDEX.remove(callId); }

    static void clearHistoryForTests() { synchronized(HISTORY_LOCK){RESPONSE_HISTORY.clear();CALL_RESPONSE_INDEX.clear();} }

    private static JSONObject buildResponse(String id,String model,String text,String reasoning,Map<Integer,ToolCall> tools,JSONObject chatUsage,Set<String> customTools) throws Exception {
        JSONArray output=new JSONArray();
        if(!reasoning.isEmpty()) output.put(new JSONObject().put("id","rs_"+shortId()).put("type","reasoning").put("summary",new JSONArray().put(new JSONObject().put("type","summary_text").put("text",reasoning))));
        if(!text.isEmpty()) output.put(messageItem(text,true));
        for(ToolCall t:tools.values()) {
            String callId=t.id.isEmpty()?"call_"+shortId():t.id;
            if(customTools.contains(t.name)) { String input=t.arguments; try{input=new JSONObject(t.arguments).optString("input",t.arguments);}catch(Exception ignored){} output.put(new JSONObject().put("id","ctc_"+shortId()).put("type","custom_tool_call").put("status","completed").put("call_id",callId).put("name",t.name).put("input",input)); }
            else output.put(new JSONObject().put("id","fc_"+shortId()).put("type","function_call").put("status","completed").put("call_id",callId).put("name",t.name).put("arguments",t.arguments));
        }
        JSONObject usage=new JSONObject(); int input=chatUsage==null?0:chatUsage.optInt("prompt_tokens"); int out=chatUsage==null?0:chatUsage.optInt("completion_tokens"); usage.put("input_tokens",input).put("output_tokens",out).put("total_tokens",chatUsage==null?input+out:chatUsage.optInt("total_tokens",input+out)); usage.put("input_tokens_details",new JSONObject().put("cached_tokens",0)); usage.put("output_tokens_details",new JSONObject().put("reasoning_tokens",0));
        return new JSONObject().put("id",id).put("object","response").put("created_at",System.currentTimeMillis()/1000).put("status","completed").put("error",JSONObject.NULL).put("incomplete_details",JSONObject.NULL).put("instructions",JSONObject.NULL).put("model",model).put("output",output).put("parallel_tool_calls",true).put("temperature",1).put("tool_choice","auto").put("tools",new JSONArray()).put("top_p",1).put("usage",usage);
    }
    private static JSONObject messageItem(String text,boolean completed) throws Exception { return new JSONObject().put("id","msg_"+shortId()).put("type","message").put("status",completed?"completed":"in_progress").put("role","assistant").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",text).put("annotations",new JSONArray()))); }
    private static String buildEventStream(JSONObject completed) throws Exception {
        StringBuilder s=new StringBuilder(); int seq=0; JSONObject initial=new JSONObject(completed.toString()).put("status","in_progress").put("output",new JSONArray());
        event(s,new JSONObject().put("type","response.created").put("sequence_number",seq++).put("response",initial));
        event(s,new JSONObject().put("type","response.in_progress").put("sequence_number",seq++).put("response",initial));
        JSONArray output=completed.getJSONArray("output");
        for(int i=0;i<output.length();i++){JSONObject item=output.getJSONObject(i), added=new JSONObject(item.toString()).put("status","in_progress"); if("message".equals(item.optString("type")))added.put("content",new JSONArray()); if("function_call".equals(item.optString("type")))added.put("arguments",""); if("custom_tool_call".equals(item.optString("type")))added.put("input",""); if("reasoning".equals(item.optString("type"))){added.remove("status");added.put("summary",new JSONArray());}
            event(s,new JSONObject().put("type","response.output_item.added").put("sequence_number",seq++).put("output_index",i).put("item",added));
            if("message".equals(item.optString("type"))){JSONObject part=item.getJSONArray("content").getJSONObject(0),empty=new JSONObject(part.toString()).put("text",""); event(s,new JSONObject().put("type","response.content_part.added").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("content_index",0).put("part",empty)); String text=part.optString("text"); if(!text.isEmpty())event(s,new JSONObject().put("type","response.output_text.delta").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("content_index",0).put("delta",text).put("logprobs",new JSONArray())); event(s,new JSONObject().put("type","response.output_text.done").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("content_index",0).put("text",text).put("logprobs",new JSONArray())); event(s,new JSONObject().put("type","response.content_part.done").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("content_index",0).put("part",part));}
            else if("reasoning".equals(item.optString("type"))){String summary=item.getJSONArray("summary").getJSONObject(0).optString("text"); JSONObject part=new JSONObject().put("type","summary_text").put("text",""); event(s,new JSONObject().put("type","response.reasoning_summary_part.added").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("summary_index",0).put("part",part)); if(!summary.isEmpty())event(s,new JSONObject().put("type","response.reasoning_summary_text.delta").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("summary_index",0).put("delta",summary)); event(s,new JSONObject().put("type","response.reasoning_summary_text.done").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("summary_index",0).put("text",summary)); event(s,new JSONObject().put("type","response.reasoning_summary_part.done").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("summary_index",0).put("part",new JSONObject().put("type","summary_text").put("text",summary)));}
            else if("function_call".equals(item.optString("type"))){String args=item.optString("arguments"); if(!args.isEmpty())event(s,new JSONObject().put("type","response.function_call_arguments.delta").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("delta",args)); event(s,new JSONObject().put("type","response.function_call_arguments.done").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("arguments",args));}
            else if("custom_tool_call".equals(item.optString("type"))){String input=item.optString("input"); if(!input.isEmpty())event(s,new JSONObject().put("type","response.custom_tool_call_input.delta").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("delta",input)); event(s,new JSONObject().put("type","response.custom_tool_call_input.done").put("sequence_number",seq++).put("item_id",item.getString("id")).put("output_index",i).put("input",input));}
            event(s,new JSONObject().put("type","response.output_item.done").put("sequence_number",seq++).put("output_index",i).put("item",item));
        }
        event(s,new JSONObject().put("type","response.completed").put("sequence_number",seq).put("response",completed)); return s.toString();
    }
    private static void event(StringBuilder out,JSONObject event){String type=event.optString("type");out.append("event: ").append(type).append("\n").append("data: ").append(event.toString()).append("\n\n");}
    private static void mergeToolCalls(Map<Integer,ToolCall> result,JSONArray array){if(array==null)return;for(int i=0;i<array.length();i++){JSONObject value=array.optJSONObject(i);if(value==null)continue;int index=value.optInt("index",i);ToolCall call=result.get(index);if(call==null){call=new ToolCall();result.put(index,call);}if(!value.optString("id").isEmpty())call.id=value.optString("id");JSONObject fn=value.optJSONObject("function");if(fn!=null){if(!fn.optString("name").isEmpty())call.name=fn.optString("name");call.arguments+=fn.optString("arguments");}}}
    private static void appendValue(StringBuilder out,Object value){if(value==null||value==JSONObject.NULL)return;if(value instanceof String)out.append(value);else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null)out.append(p.optString("text"));}}}
    private static String normalizeRole(String role){return "developer".equals(role)?"system":role;}
    private static String stringValue(Object v){return v==null||v==JSONObject.NULL?"":v instanceof String?(String)v:v.toString();}
    private static void copy(JSONObject from,JSONObject to,String key)throws Exception{if(from.has(key))to.put(key,from.get(key));}
    static void selfTest() throws Exception {
        JSONObject request=new JSONObject().put("model","demo").put("instructions","be useful").put("input",new JSONArray().put(new JSONObject().put("type","message").put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text","hi"))))).put("tools",new JSONArray().put(new JSONObject().put("type","function").put("name","shell_command").put("parameters",new JSONObject().put("type","object"))).put(new JSONObject().put("type","custom").put("name","apply_patch")));
        byte[] chat=responsesRequestToChat(request.toString().getBytes(StandardCharsets.UTF_8)); String chatText=new String(chat,StandardCharsets.UTF_8); if(!chatText.contains("messages")||!chatText.contains("apply_patch")) throw new IllegalStateException("request conversion failed");
        JSONObject convertedRequest=new JSONObject(chatText); if(!"system".equals(convertedRequest.getJSONArray("messages").getJSONObject(0).optString("role"))) throw new IllegalStateException("developer instructions must be normalized to system");
        String chunk="data: {\"model\":\"demo\",\"choices\":[{\"delta\":{\"content\":\"ok\",\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"apply_patch\",\"arguments\":\"{\\\"input\\\":\\\"patch\\\"}\"}}]}}]}\n\ndata: [DONE]\n\n";
        ChatResult result=chatResponseToResponses(chunk.getBytes(StandardCharsets.UTF_8),"text/event-stream","demo",customToolNames(request.toString().getBytes(StandardCharsets.UTF_8))); String events=new String(result.body,StandardCharsets.UTF_8); if(!events.contains("response.completed")||!events.contains("custom_tool_call")||!events.contains("patch"))throw new IllegalStateException("response conversion failed");
    }

    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,24);}
}
