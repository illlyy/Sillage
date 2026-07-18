package com.termux.app

import org.json.JSONObject

/** Compact, WebUI-style descriptions for shell activity shown inside the reasoning panel. */
object NativeCommandPresentation {
    const val LIST_FILES = "list_files"
    const val READ_FILE = "read_file"
    const val SEARCH_FILES = "search_files"
    const val GIT_STATUS = "git_status"
    const val GIT_DIFF = "git_diff"
    const val GIT_LOG = "git_log"
    const val CURRENT_DIRECTORY = "current_directory"
    const val CREATE_DIRECTORY = "create_directory"
    const val RUN_TESTS = "run_tests"
    const val BUILD_PROJECT = "build_project"
    const val DEVICE_COMMAND = "device_command"
    const val RUN_COMMAND = "run_command"

    @JvmStatic
    fun rawCommand(item: JSONObject?): String {
        if (item == null) return ""
        val value = item.opt("command")
        return if (value is org.json.JSONArray) {
            (0 until value.length()).joinToString(" ") { value.optString(it) }.trim()
        } else {
            item.optString("command", item.optString("cmd", "")).trim()
        }
    }

    @JvmStatic
    fun action(item: JSONObject?): String {
        if (item == null) return RUN_COMMAND
        val actions = item.optJSONArray("commandActions")
        val lastAction = actions?.optJSONObject((actions.length() - 1).coerceAtLeast(0))
        when (lastAction?.optString("type")) {
            "listFiles" -> return LIST_FILES
            "read" -> return READ_FILE
            "search" -> return SEARCH_FILES
            else -> Unit
        }

        val command = rawCommand(item)
            .replace(Regex("""(?i)^\s*(?:powershell(?:\.exe)?\s+(?:-\w+\s+)*|pwsh\s+(?:-\w+\s+)*|cmd(?:\.exe)?\s+/c\s+|bash\s+-lc\s+|sh\s+-c\s+)"""), "")
            .trim(' ', '\'', '"')
        val lower = command.lowercase()
        return when {
            Regex("""(^|[;&|]\s*|\s)(get-childitem|ls|dir|tree)(\s|$)""").containsMatchIn(lower) -> LIST_FILES
            lower.contains("rg --files") || lower.contains("fd --type f") -> LIST_FILES
            Regex("""(^|[;&|]\s*|\s)(get-content|cat|head|tail)(\s|$)""").containsMatchIn(lower) -> READ_FILE
            Regex("""(^|[;&|]\s*|\s)(select-string|rg|grep|findstr)(\s|$)""").containsMatchIn(lower) -> SEARCH_FILES
            Regex("""(^|[;&|]\s*|\s)find\s+""").containsMatchIn(lower) -> if (Regex("""\s-(name|iname|path|regex)\s""").containsMatchIn(lower)) SEARCH_FILES else LIST_FILES
            Regex("""(^|\s)git\s+status(\s|$)""").containsMatchIn(lower) -> GIT_STATUS
            Regex("""(^|\s)git\s+diff(\s|$)""").containsMatchIn(lower) -> GIT_DIFF
            Regex("""(^|\s)git\s+log(\s|$)""").containsMatchIn(lower) -> GIT_LOG
            Regex("""(^|[;&|]\s*|\s)(pwd|get-location)(\s|$)""").containsMatchIn(lower) -> CURRENT_DIRECTORY
            Regex("""(^|[;&|]\s*|\s)(mkdir|new-item\s+-itemtype\s+directory)(\s|$)""").containsMatchIn(lower) -> CREATE_DIRECTORY
            lower.contains("gradlew") && Regex("""\b(?:test|connected|lint)\w*\b""").containsMatchIn(lower) -> RUN_TESTS
            Regex("""(^|\s)(npm|pnpm|yarn)\s+(run\s+)?test(\s|$)""").containsMatchIn(lower) -> RUN_TESTS
            lower.contains("gradlew") && Regex("""\b(?:assemble|build|bundle)\w*\b""").containsMatchIn(lower) -> BUILD_PROJECT
            Regex("""(^|\s)(npm|pnpm|yarn)\s+(run\s+)?build(\s|$)""").containsMatchIn(lower) -> BUILD_PROJECT
            Regex("""(^|[;&|]\s*|\s)adb(\.exe)?(\s|$)""").containsMatchIn(lower) -> DEVICE_COMMAND
            else -> RUN_COMMAND
        }
    }

    @JvmStatic
    fun subject(item: JSONObject?): String {
        if (item == null) return ""
        val actions = item.optJSONArray("commandActions")
        val action = actions?.optJSONObject((actions.length() - 1).coerceAtLeast(0)) ?: return ""
        return when (action.optString("type")) {
            "read" -> action.optString("name", action.optString("path", ""))
            "search" -> action.optString("query", "")
            else -> ""
        }.trim()
    }

    @JvmStatic
    fun label(action: String, chinese: Boolean): String = if (chinese) when (action) {
        LIST_FILES -> "\u5217\u51fa\u6587\u4ef6\u5939"
        READ_FILE -> "\u8bfb\u53d6\u6587\u4ef6"
        SEARCH_FILES -> "\u641c\u7d22\u6587\u4ef6"
        GIT_STATUS -> "\u68c0\u67e5 Git \u72b6\u6001"
        GIT_DIFF -> "\u67e5\u770b\u4ee3\u7801\u53d8\u66f4"
        GIT_LOG -> "\u67e5\u770b\u63d0\u4ea4\u8bb0\u5f55"
        CURRENT_DIRECTORY -> "\u67e5\u770b\u5f53\u524d\u76ee\u5f55"
        CREATE_DIRECTORY -> "\u521b\u5efa\u6587\u4ef6\u5939"
        RUN_TESTS -> "\u8fd0\u884c\u6d4b\u8bd5"
        BUILD_PROJECT -> "\u6784\u5efa\u9879\u76ee"
        DEVICE_COMMAND -> "\u8fd0\u884c\u8bbe\u5907\u547d\u4ee4"
        else -> "\u8fd0\u884c\u547d\u4ee4"
    } else when (action) {
        LIST_FILES -> "List folders"
        READ_FILE -> "Read file"
        SEARCH_FILES -> "Search files"
        GIT_STATUS -> "Check Git status"
        GIT_DIFF -> "View code changes"
        GIT_LOG -> "View commit history"
        CURRENT_DIRECTORY -> "Show current folder"
        CREATE_DIRECTORY -> "Create folder"
        RUN_TESTS -> "Run tests"
        BUILD_PROJECT -> "Build project"
        DEVICE_COMMAND -> "Run device command"
        else -> "Run command"
    }
}
