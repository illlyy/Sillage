package com.termux.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Small authenticated client for the loopback Mihomo controller. */
final class MihomoControllerClient {
    static final class ProxyNode {
        final String name;
        final String type;
        int delay;
        final boolean alive;
        ProxyNode(String name, String type, int delay, boolean alive) {
            this.name = name; this.type = type; this.delay = delay; this.alive = alive;
        }
    }

    static final class ProxyGroup {
        final String name;
        final String type;
        String selected;
        final List<ProxyNode> nodes;
        ProxyGroup(String name, String type, String selected, List<ProxyNode> nodes) {
            this.name = name; this.type = type; this.selected = selected; this.nodes = nodes;
        }
    }

    private final MihomoManager manager;

    MihomoControllerClient(MihomoManager manager) { this.manager = manager; }

    String version() throws Exception {
        return request("GET", "/version", null).optString("version", "");
    }

    List<ProxyGroup> groups() throws Exception {
        JSONObject proxies = request("GET", "/proxies", null).getJSONObject("proxies");
        ArrayList<String> names = new ArrayList<>();
        Iterator<String> keys = proxies.keys();
        while (keys.hasNext()) names.add(keys.next());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        ArrayList<ProxyGroup> groups = new ArrayList<>();
        for (String name : names) {
            JSONObject raw = proxies.optJSONObject(name);
            if (raw == null) continue;
            JSONArray all = raw.optJSONArray("all");
            if (all == null || all.length() == 0) continue;
            ArrayList<ProxyNode> nodes = new ArrayList<>();
            for (int i = 0; i < all.length(); i++) {
                String nodeName = all.optString(i, "");
                if (nodeName.isEmpty()) continue;
                JSONObject nodeRaw = proxies.optJSONObject(nodeName);
                String type = nodeRaw == null ? "" : nodeRaw.optString("type", "");
                boolean alive = nodeRaw == null || nodeRaw.optBoolean("alive", true);
                nodes.add(new ProxyNode(nodeName, type, lastDelay(nodeRaw), alive));
            }
            groups.add(new ProxyGroup(name, raw.optString("type", ""), raw.optString("now", ""), nodes));
        }
        return groups;
    }

    void select(String group, String node) throws Exception {
        request("PUT", "/proxies/" + segment(group), new JSONObject().put("name", node));
    }

    Map<String, Integer> testGroup(String group) throws Exception {
        String path = "/group/" + segment(group) + "/delay?url=" +
            URLEncoder.encode("https://www.gstatic.com/generate_204", "UTF-8") + "&timeout=6000";
        JSONObject result = request("GET", path, null);
        HashMap<String, Integer> delays = new HashMap<>();
        Iterator<String> keys = result.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            int delay = result.optInt(key, -1);
            if (delay > 0) delays.put(key, delay);
        }
        return delays;
    }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        if (!manager.isRunning()) throw new IllegalStateException("Mihomo is not running");
        URL target = new URL("http://127.0.0.1:" + manager.controllerPort() + path);
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Authorization", "Bearer " + manager.controllerSecret());
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = read(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("Controller HTTP " + code + (text.isEmpty() ? "" : ": " + text));
        return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static int lastDelay(JSONObject raw) {
        if (raw == null) return -1;
        JSONArray history = raw.optJSONArray("history");
        if (history == null) return -1;
        for (int i = history.length() - 1; i >= 0; i--) {
            JSONObject item = history.optJSONObject(i);
            int delay = item == null ? -1 : item.optInt("delay", -1);
            if (delay > 0) return delay;
        }
        return -1;
    }

    private static String segment(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }
}
