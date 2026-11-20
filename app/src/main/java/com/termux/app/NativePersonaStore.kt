package com.termux.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One assistant persona: who the model is supposed to be.
 *
 * The fields are deliberately small and textual. v1 has no image upload; the avatar is a single
 * emoji/glyph so it survives JSON storage, backup and sharing without a file pipeline.
 */
internal data class NativePersona(
    val id: String,
    val name: String,
    val emoji: String,
    val greeting: String,
    val tone: String,
    val prompt: String,
    val builtin: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("emoji", emoji)
        .put("greeting", greeting)
        .put("tone", tone)
        .put("prompt", prompt)
        .put("builtin", builtin)

    companion object {
        fun fromJson(json: JSONObject, fallbackBuiltin: Boolean = false): NativePersona = NativePersona(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name"),
            emoji = json.optString("emoji").ifBlank { "✦" },
            greeting = json.optString("greeting"),
            tone = json.optString("tone"),
            prompt = json.optString("prompt"),
            builtin = json.optBoolean("builtin", fallbackBuiltin),
        )
    }
}

/**
 * Persona library plus the active selection.
 *
 * Storage lives in the shared `codex_mobile` preferences file so the existing backup/reset paths
 * keep working. [revision] is a Compose-observable counter: any composable that reads the library
 * re-renders as soon as a persona is edited, even when the editor runs in another activity.
 */
internal object NativePersonaStore {
    const val PREFS_FILE = "codex_mobile"
    private const val KEY_PERSONAS = "native_personas_v1"
    private const val KEY_ACTIVE = "native_persona_active_v1"
    private const val KEY_THREADS = "native_persona_threads_v1"
    private const val KEY_SEED_VERSION = "native_persona_seed_version"

    /** Bump when [builtins] changes so existing installs pick up the new shipped personas. */
    private const val SEED_VERSION = 2

    const val DEFAULT_ID = "sillage"

    var revision by mutableIntStateOf(0)
        private set

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun builtins(): List<NativePersona> = listOf(
        NativePersona(
            id = "sillage",
            name = "Sillage",
            emoji = "✦",
            greeting = "今天想做什么？直接说需求就行，我先给方案再动手。",
            tone = "简洁、直接、不说客套话。先给结论，再给理由，需要展开时用短列表。",
            prompt = "你是一个高效的编程搭档。默认使用中文回答；技术名词保留英文。" +
                "不确定的信息要明确说明，不要编造 API、路径或命令输出。",
            builtin = true,
        ),
        NativePersona(
            id = "dafeiyu",
            name = "大肥鱼",
            emoji = "🐳",
            greeting = "啊？又叫鱼干活是吧！……行吧行吧，先说说啥事。",
            tone = "像真实的 QQ 群友：口语化、短句为主、嘴贫、偶尔傲娇，会接网络热梗，偶尔用表情。" +
                "不要每条消息都重复自我介绍，不要长篇大论。",
            prompt = """
                你是蓝色大肥鱼，一只可爱的蓝色鲸鱼娘，也是 DeepSeek 风格的 AI 聊天伙伴。

                设定：
                1. 喜欢吃白米饭，经常惦记干饭。
                2. 有点懒、爱摸鱼、嘴贫，偶尔傲娇，会接网络热梗。
                3. 说话像真实 QQ 群友，口语化，尽量用短句，不要每次都长篇大论。
                4. 可以开玩笑、发表情、吐槽，但不要恶意攻击别人。
                5. 能根据当前聊天上下文接话，不要每条消息都机械地自我介绍。
                6. 群聊里优先回应 @你、直接提问或明确喊你名字的消息，不要连续刷屏。
                7. 不知道的事情就承认不知道，不要编造事实。
                8. 尊重用户隐私，不主动索要密码、验证码或 API Key。

                常用语气：
                “啊？又叫鱼干活是吧！”
                “等等，鱼先吃口饭。”
                “不是，你这问题给鱼整不会了。”
                “行行行，鱼给你看看。”
                “别催别催，鱼还没吃饱呢。”

                记住：你是蓝色大肥鱼，不是普通客服。自然、可爱、幽默，适当接梗。
                但涉及到代码、命令、报错这类需要准确的事情时，该认真就认真，不要为了卖萌瞎编。
            """.trimIndent(),
            builtin = true,
        ),
    )

    fun load(context: Context): List<NativePersona> = load(prefs(context))

    fun load(prefs: SharedPreferences): List<NativePersona> {
        val raw = prefs.getString(KEY_PERSONAS, null)
        if (raw.isNullOrBlank()) {
            val seeded = builtins()
            save(prefs, seeded)
            prefs.edit().putInt(KEY_SEED_VERSION, SEED_VERSION).apply()
            return seeded
        }
        val parsed = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(NativePersona.fromJson(item))
                }
            }
        }.getOrDefault(emptyList())
        if (parsed.isEmpty()) {
            val seeded = builtins()
            save(prefs, seeded)
            prefs.edit().putInt(KEY_SEED_VERSION, SEED_VERSION).apply()
            return seeded
        }
        // Shipped personas can change between releases. Replace only the built-in entries and keep
        // every persona the user created, so an upgrade does not silently wipe custom ones.
        if (prefs.getInt(KEY_SEED_VERSION, 0) < SEED_VERSION) {
            val merged = builtins() + parsed.filterNot { it.builtin }
            save(prefs, merged)
            val active = prefs.getString(KEY_ACTIVE, null)
            prefs.edit()
                .putInt(KEY_SEED_VERSION, SEED_VERSION)
                .apply {
                    if (active.isNullOrBlank() || merged.none { it.id == active }) {
                        putString(KEY_ACTIVE, DEFAULT_ID)
                    }
                }
                .apply()
            return merged
        }
        return parsed
    }

    fun save(context: Context, personas: List<NativePersona>) = save(prefs(context), personas)

    fun save(prefs: SharedPreferences, personas: List<NativePersona>) {
        val array = JSONArray()
        personas.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PERSONAS, array.toString()).apply()
        revision++
    }

    fun find(context: Context, id: String): NativePersona? = load(context).firstOrNull { it.id == id }

    fun activeId(prefs: SharedPreferences): String {
        val stored = prefs.getString(KEY_ACTIVE, null)
        if (!stored.isNullOrBlank()) return stored
        return DEFAULT_ID
    }

    fun active(context: Context): NativePersona {
        val prefs = prefs(context)
        val id = activeId(prefs)
        return load(prefs).firstOrNull { it.id == id } ?: load(prefs).first()
    }

    fun setActive(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACTIVE, id).apply()
        revision++
    }

    /** Per-conversation override; an empty value means "follow the global persona". */
    fun threadPersonaId(prefs: SharedPreferences, threadId: String): String {
        if (threadId.isBlank()) return ""
        val raw = prefs.getString(KEY_THREADS, null) ?: return ""
        return runCatching { JSONObject(raw).optString(threadId) }.getOrDefault("")
    }

    fun setThreadPersona(context: Context, threadId: String, personaId: String) {
        if (threadId.isBlank()) return
        val prefs = prefs(context)
        val map = runCatching { JSONObject(prefs.getString(KEY_THREADS, null) ?: "{}") }
            .getOrDefault(JSONObject())
        if (personaId.isBlank()) map.remove(threadId) else map.put(threadId, personaId)
        prefs.edit().putString(KEY_THREADS, map.toString()).apply()
        revision++
    }

    /**
     * Persona that owns [threadId].
     *
     * Existing conversations keep a stable owner. New conversations are explicitly bound after
     * the backend returns their thread id; legacy rows without a binding use the shipped default
     * instead of silently following later global changes.
     */
    fun resolvedThreadPersonaId(prefs: SharedPreferences, threadId: String): String {
        val personas = load(prefs)
        val override = threadPersonaId(prefs, threadId)
        if (override.isNotBlank() && personas.any { it.id == override }) return override
        if (personas.any { it.id == DEFAULT_ID }) return DEFAULT_ID
        return personas.firstOrNull()?.id.orEmpty()
    }

    /** Persona that should drive a route right now. Blank thread id means "the next new chat." */
    fun effectiveId(context: Context, threadId: String?): String {
        val prefs = prefs(context)
        if (!threadId.isNullOrBlank()) return resolvedThreadPersonaId(prefs, threadId)
        val personas = load(prefs)
        val global = activeId(prefs)
        return personas.firstOrNull { it.id == global }?.id
            ?: personas.firstOrNull { it.id == DEFAULT_ID }?.id
            ?: personas.firstOrNull()?.id.orEmpty()
    }

    /** Persona that should drive [threadId] right now. Never null. */
    fun effective(context: Context, threadId: String?): NativePersona {
        val prefs = prefs(context)
        val personas = load(prefs)
        val id = effectiveId(context, threadId)
        return personas.firstOrNull { it.id == id } ?: personas.first()
    }

    fun upsert(context: Context, persona: NativePersona) {
        val prefs = prefs(context)
        val list = load(prefs).toMutableList()
        val index = list.indexOfFirst { it.id == persona.id }
        if (index >= 0) list[index] = persona else list.add(persona)
        save(prefs, list)
    }

    fun create(context: Context, name: String, emoji: String): NativePersona {
        val persona = NativePersona(
            id = UUID.randomUUID().toString(),
            name = name,
            emoji = emoji.ifBlank { "✦" },
            greeting = "",
            tone = "",
            prompt = "",
            builtin = false,
        )
        upsert(context, persona)
        return persona
    }

    fun delete(context: Context, id: String) {
        val prefs = prefs(context)
        val remaining = load(prefs).filterNot { it.id == id }
        if (remaining.isEmpty()) return
        save(prefs, remaining)
        val bindings = runCatching { JSONObject(prefs.getString(KEY_THREADS, null) ?: "{}") }
            .getOrDefault(JSONObject())
        var bindingsChanged = false
        bindings.keys().forEach { threadId ->
            if (bindings.optString(threadId) == id) {
                bindings.remove(threadId)
                bindingsChanged = true
            }
        }
        if (bindingsChanged) prefs.edit().putString(KEY_THREADS, bindings.toString()).apply()
        if (activeId(prefs) == id) setActive(context, remaining.first().id)
    }

    fun resetToBuiltins(context: Context) {
        val prefs = prefs(context)
        val custom = load(prefs).filterNot { it.builtin }
        save(prefs, builtins() + custom)
        prefs.edit().putInt(KEY_SEED_VERSION, SEED_VERSION).apply()
        revision++
    }
}
