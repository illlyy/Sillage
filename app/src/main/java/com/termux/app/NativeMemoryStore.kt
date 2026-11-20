package com.termux.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class NativeMemoryTier { CORE, NORMAL }

/**
 * One long-term memory record.
 *
 * `CORE` memories are small, always-injected facts (the user's name, stack, preferences).
 * `NORMAL` memories are everything else: they only appear as a one-line index entry and the
 * assistant has to call `fcode-memory recall` to read the body. That split is what keeps a
 * growing memory store from eating the whole context window.
 */
internal data class NativeMemory(
    val id: String,
    val tier: NativeMemoryTier,
    val createdAt: Long,
    val dateLabel: String,
    val tags: List<String>,
    val title: String,
    val body: String,
)

/**
 * Append-only TSV store shared with the `fcode-memory` shell tool.
 *
 * TSV rather than JSON so the CLI can append with `printf` from any shell and the app can parse
 * line by line without loading a library. Fields escape `\`, TAB and newline; see [escape].
 */
internal object NativeMemoryStore {
    private const val VERSION = "v1"
    private const val FILE_NAME = "memories.tsv"
    private const val TOOL_NAME = "fcode-memory"

    var revision by mutableIntStateOf(0)
        private set

    fun root(): File = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".fcode/memory")

    fun file(): File = File(root(), FILE_NAME)

    fun dateLabel(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

    private fun dayLabel(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    fun escape(raw: String): String = buildString(raw.length) {
        raw.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(char)
            }
        }
    }

    fun unescape(raw: String): String = buildString(raw.length) {
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length) {
                when (raw[index + 1]) {
                    'n' -> { append('\n'); index += 2 }
                    't' -> { append('\t'); index += 2 }
                    '\\' -> { append('\\'); index += 2 }
                    else -> { append(char); index++ }
                }
            } else {
                append(char)
                index++
            }
        }
    }

    fun load(context: Context): List<NativeMemory> = load(file())

    fun load(source: File): List<NativeMemory> {
        if (!source.isFile) return emptyList()
        val result = ArrayList<NativeMemory>()
        runCatching {
            source.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                if (parts.size < 7 || parts[0] != VERSION) return@forEachLine
                val millis = parts[3].toLongOrNull() ?: return@forEachLine
                result += NativeMemory(
                    id = parts[1],
                    tier = if (parts[2] == "core") NativeMemoryTier.CORE else NativeMemoryTier.NORMAL,
                    createdAt = millis,
                    dateLabel = parts[4],
                    tags = parts[5].split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    title = unescape(parts[6]),
                    body = unescape(parts.getOrElse(7) { "" }),
                )
            }
        }
        return result.sortedByDescending { it.createdAt }
    }

    fun append(context: Context, title: String, body: String, tags: List<String>, core: Boolean): NativeMemory {
        val millis = System.currentTimeMillis()
        val memory = NativeMemory(
            id = "m" + millis.toString(36) + (1000..9999).random(),
            tier = if (core) NativeMemoryTier.CORE else NativeMemoryTier.NORMAL,
            createdAt = millis,
            dateLabel = dateLabel(millis),
            tags = tags.map { it.trim() }.filter { it.isNotEmpty() },
            title = title.trim(),
            body = body.trim(),
        )
        append(context, memory)
        return memory
    }

    fun append(context: Context, memory: NativeMemory) {
        root().mkdirs()
        val line = buildString {
            append(VERSION).append('\t')
            append(memory.id).append('\t')
            append(if (memory.tier == NativeMemoryTier.CORE) "core" else "normal").append('\t')
            append(memory.createdAt).append('\t')
            append(memory.dateLabel).append('\t')
            append(memory.tags.joinToString(",")).append('\t')
            append(escape(memory.title)).append('\t')
            append(escape(memory.body)).append('\n')
        }
        file().appendText(line, Charsets.UTF_8)
        revision++
    }

    fun delete(context: Context, id: String) {
        rewrite(load(context).filterNot { it.id == id })
    }

    fun setTier(context: Context, id: String, tier: NativeMemoryTier) {
        rewrite(load(context).map { if (it.id == id) it.copy(tier = tier) else it })
    }

    fun update(context: Context, memory: NativeMemory) {
        rewrite(load(context).map { if (it.id == memory.id) memory else it })
    }

    private fun rewrite(memories: List<NativeMemory>) {
        val target = file()
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        temp.parentFile?.mkdirs()
        val builder = StringBuilder()
        memories.sortedBy { it.createdAt }.forEach { memory ->
            builder.append(VERSION).append('\t')
                .append(memory.id).append('\t')
                .append(if (memory.tier == NativeMemoryTier.CORE) "core" else "normal").append('\t')
                .append(memory.createdAt).append('\t')
                .append(memory.dateLabel).append('\t')
                .append(memory.tags.joinToString(",")).append('\t')
                .append(escape(memory.title)).append('\t')
                .append(escape(memory.body)).append('\n')
        }
        temp.writeText(builder.toString(), Charsets.UTF_8)
        target.delete()
        temp.renameTo(target)
        revision++
    }

    fun core(context: Context, limit: Int = 8): List<NativeMemory> =
        load(context).filter { it.tier == NativeMemoryTier.CORE }.take(limit)

    fun recent(context: Context, limit: Int = 20): List<NativeMemory> = load(context).take(limit)

    fun search(context: Context, query: String, limit: Int = 5): List<NativeMemory> {
        if (query.isBlank()) return emptyList()
        val needle = query.trim()
        return load(context)
            .asSequence()
            .filter { memory ->
                memory.title.contains(needle, ignoreCase = true) ||
                    memory.body.contains(needle, ignoreCase = true) ||
                    memory.tags.any { it.contains(needle, ignoreCase = true) }
            }
            .take(limit)
            .toList()
    }

    /** Short line used in the prompt index, e.g. `- [2026-11-18] 构建失败 #build #gradle`. */
    fun indexLine(memory: NativeMemory): String {
        val tags = if (memory.tags.isEmpty()) "" else " " + memory.tags.joinToString(" ") { "#$it" }
        return "- [${dayLabel(memory.createdAt)}] ${memory.title}$tags"
    }

    /**
     * Installs the `fcode-memory` CLI once per version. Writing it lazily keeps installs that never
     * use personas free of side effects, and the marker avoids rewriting on every turn.
     */
    fun ensureTool(context: Context) {
        runCatching {
            val bin = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH)
            if (!bin.isDirectory) return
            val script = File(bin, TOOL_NAME)
            val marker = File(root(), ".tool-$VERSION")
            if (script.canExecute() && marker.isFile) return
            root().mkdirs()
            script.writeText(toolScript(), Charsets.UTF_8)
            script.setExecutable(true, false)
            marker.writeText(VERSION, Charsets.UTF_8)
        }
    }

    fun toolScript(): String = """
        #!/system/bin/sh
        # fcode-memory — long-term memory for the assistant.
        # Storage is a tab separated append-only file shared with the app UI.
        ROOT="${TermuxConstants.TERMUX_HOME_DIR_PATH}/.fcode/memory"
        FILE="${'$'}ROOT/memories.tsv"
        if ! command -v awk >/dev/null 2>&1; then
          echo "fcode-memory: awk is unavailable; install the Termux base environment" >&2
          exit 3
        fi
        mkdir -p "${'$'}ROOT" 2>/dev/null
        [ -f "${'$'}FILE" ] || : > "${'$'}FILE"

        print_entry() {
          awk -F'\t' '
          function unesc(s,   out, i, c, n) {
            out = ""; n = length(s)
            for (i = 1; i <= n; i++) {
              c = substr(s, i, 1)
              if (c == "\\") {
                i++
                c = substr(s, i, 1)
                if (c == "n") out = out "\n"
                else if (c == "t") out = out "\t"
                else out = out c
              } else out = out c
            }
            return out
          }
          {
            printf "id=%s tier=%s date=%s tags=%s\n", ${'$'}2, ${'$'}3, ${'$'}5, ${'$'}6
            printf "%s\n", unesc(${'$'}7)
            if (${'$'}8 != "") printf "%s\n", unesc(${'$'}8)
            printf "\n"
          }'
        }

        cmd="${'$'}{1:-index}"
        [ ${'$'}# -gt 0 ] && shift

        case "${'$'}cmd" in
          save)
            title=""; tags=""; tier="normal"; body=""
            while [ ${'$'}# -gt 0 ]; do
              case "${'$'}1" in
                --title) title="${'$'}2"; shift 2 ;;
                --tags) tags="${'$'}2"; shift 2 ;;
                --core) tier="core"; shift ;;
                --body) body="${'$'}2"; shift 2 ;;
                *) shift ;;
              esac
            done
            if [ -z "${'$'}body" ]; then body="$(cat)"; fi
            if [ -z "${'$'}title" ]; then
              echo "usage: fcode-memory save --title TITLE [--tags a,b] [--core] [--body TEXT]  (body may come from stdin)" >&2
              exit 2
            fi
            id="m$(date +%s)$$"
            escaped_title="$(printf '%s' "${'$'}title" | awk 'BEGIN{ORS=""}{gsub(/\\/,"\\\\"); gsub(/\t/,"\\t"); if (NR>1) printf "\\n"; printf "%s", ${'$'}0}')"
            escaped_body="$(printf '%s' "${'$'}body" | awk 'BEGIN{ORS=""}{gsub(/\\/,"\\\\"); gsub(/\t/,"\\t"); if (NR>1) printf "\\n"; printf "%s", ${'$'}0}')"
            date_label="$(date '+%Y-%m-%d %H:%M')"
            millis="$(date +%s)000"
            printf 'v1\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "${'$'}id" "${'$'}tier" "${'$'}millis" "${'$'}date_label" "${'$'}tags" "${'$'}escaped_title" "${'$'}escaped_body" >> "${'$'}FILE"
            echo "saved ${'$'}id"
            ;;
          recall|search)
            query="${'$'}{1:-}"; limit="${'$'}{2:-5}"
            if [ -z "${'$'}query" ]; then
              echo "usage: fcode-memory recall QUERY [LIMIT]" >&2
              exit 2
            fi
            grep -i -F -e "${'$'}query" "${'$'}FILE" | tail -n "${'$'}limit" | print_entry
            ;;
          list)
            limit="${'$'}{1:-10}"
            tail -n "${'$'}limit" "${'$'}FILE" | print_entry
            ;;
          index)
            limit="${'$'}{1:-25}"
            tail -n "${'$'}limit" "${'$'}FILE" | awk -F'\t' '
            function unesc(s,   out, i, c, n) {
              out = ""; n = length(s)
              for (i = 1; i <= n; i++) {
                c = substr(s, i, 1)
                if (c == "\\") {
                  i++
                  c = substr(s, i, 1)
                  if (c == "n") out = out "\n"
                  else if (c == "t") out = out "\t"
                  else out = out c
                } else out = out c
              }
              return out
            }
            {
              split(${'$'}5, d, " ")
              if (${'$'}3 == "core") printf "[core] "
              printf "- [%s] %s", d[1], unesc(${'$'}7)
              if (${'$'}6 != "") {
                nt = split(${'$'}6, tg, ",")
                for (i = 1; i <= nt; i++) printf " #%s", tg[i]
              }
              printf "\n"
            }'
            ;;
          *)
            echo "fcode-memory commands:" >&2
            echo "  save --title T [--tags a,b] [--core] [--body TEXT]   (stdin body supported)" >&2
            echo "  recall QUERY [N]    search memories" >&2
            echo "  list [N]            newest memories with bodies" >&2
            echo "  index [N]           compact list of titles" >&2
            exit 2
            ;;
        esac
    """.trimIndent() + "\n"
}
