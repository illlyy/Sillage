package com.termux.app

import android.content.Context
import androidx.compose.runtime.Immutable
import com.termux.shared.termux.TermuxConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Immutable
data class NativeDrawerProject(val path: String, val name: String)

/** Lightweight local project metadata. Source folders remain user-owned and are never deleted. */
object NativeDrawerProjectStore {
    private const val PREFS = "native_drawer_projects_v1"
    private const val ALIASES = "aliases"
    private const val REGISTERED = "registered"
    private const val HIDDEN = "hidden"
    private val lock = Any()

    fun defaultRoot(): File = File(TermuxConstants.TERMUX_HOME_DIR, "projects")

    fun registered(context: Context): List<NativeDrawerProject> = synchronized(lock) {
        val prefs = prefs(context)
        val aliases = jsonObject(prefs.getString(ALIASES, "{}"))
        val hidden = stringSet(prefs.getString(HIDDEN, "[]"))
        stringSet(prefs.getString(REGISTERED, "[]"))
            .filterNot(hidden::contains)
            .map { path -> NativeDrawerProject(path, aliases.optString(path).ifBlank { displayName(path) }) }
    }

    fun displayName(context: Context, path: String): String = synchronized(lock) {
        jsonObject(prefs(context).getString(ALIASES, "{}")).optString(path).ifBlank { displayName(path) }
    }

    fun hiddenPaths(context: Context): Set<String> = synchronized(lock) {
        stringSet(prefs(context).getString(HIDDEN, "[]"))
    }

    fun create(context: Context, requestedName: String): NativeDrawerProject = synchronized(lock) {
        val name = requestedName.trim()
        require(name.isNotEmpty()) { "Project name is required" }
        require(name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Project name cannot contain path separators" }
        val root = defaultRoot().apply { mkdirs() }
        var folder = File(root, name)
        var suffix = 2
        while (folder.exists()) folder = File(root, "$name $suffix").also { suffix++ }
        require(folder.mkdirs()) { "Unable to create project folder" }
        val path = folder.canonicalPath
        update(context) { aliases, registered, hidden ->
            aliases.put(path, name)
            registered.add(path)
            hidden.remove(path)
        }
        NativeDrawerProject(path, name)
    }

    fun rename(context: Context, path: String, requestedName: String) = synchronized(lock) {
        val name = requestedName.trim()
        require(name.isNotEmpty()) { "Project name is required" }
        update(context) { aliases, registered, hidden ->
            aliases.put(path, name)
            registered.add(path)
            hidden.remove(path)
        }
    }

    fun remove(context: Context, path: String) = synchronized(lock) {
        update(context) { aliases, registered, hidden ->
            aliases.remove(path)
            registered.remove(path)
            hidden.add(path)
        }
    }

    private fun update(
        context: Context,
        block: (JSONObject, MutableSet<String>, MutableSet<String>) -> Unit,
    ) {
        val prefs = prefs(context)
        val aliases = jsonObject(prefs.getString(ALIASES, "{}"))
        val registered = stringSet(prefs.getString(REGISTERED, "[]")).toMutableSet()
        val hidden = stringSet(prefs.getString(HIDDEN, "[]")).toMutableSet()
        block(aliases, registered, hidden)
        prefs.edit()
            .putString(ALIASES, aliases.toString())
            .putString(REGISTERED, JSONArray(registered.toList()).toString())
            .putString(HIDDEN, JSONArray(hidden.toList()).toString())
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun jsonObject(value: String?) = runCatching { JSONObject(value.orEmpty()) }.getOrDefault(JSONObject())
    private fun stringSet(value: String?): Set<String> = runCatching {
        val array = JSONArray(value.orEmpty())
        buildSet { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }.getOrDefault(emptySet())
    private fun displayName(path: String): String = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { "Project" }
}
