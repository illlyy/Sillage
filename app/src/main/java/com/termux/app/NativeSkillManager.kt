package com.termux.app

import android.content.Context
import androidx.compose.runtime.Immutable
import com.termux.shared.termux.TermuxConstants
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Immutable
data class NativeOfficialSkill(val id: String, val name: String, val description: String)

@Immutable
data class NativeInstalledSkill(val name: String, val description: String, val path: String, val managed: Boolean)

object NativeSkillManager {
    private val managedRoot: File get() = File(File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "skills")

    fun official(context: Context): List<NativeOfficialSkill> {
        val json = context.assets.open("official-skills.json").bufferedReader().use { it.readText() }
        val array = JSONObject(json).optJSONArray("skills") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isNotEmpty()) add(NativeOfficialSkill(id, item.optString("name", id), item.optString("shortDescription", item.optString("description"))))
            }
        }
    }

    fun installed(): List<NativeInstalledSkill> {
        val result = mutableListOf<NativeInstalledSkill>()
        val seen = hashSetOf<String>()
        scan(managedRoot, result, seen, 0)
        scan(File(File(TermuxConstants.TERMUX_HOME_DIR, ".agents"), "skills"), result, seen, 0)
        return result.sortedBy { it.name.lowercase() }
    }

    fun installOfficial(context: Context, skillId: String): File {
        require(skillId.matches(Regex("[a-z0-9][a-z0-9._-]{0,80}"))) { "Invalid skill id" }
        val root = managedRoot.canonicalFile.apply { mkdirs() }
        val destination = File(root, skillId).canonicalFile
        require(destination.parentFile == root) { "Invalid destination" }
        val staging = File(root, ".installing-$skillId-${System.nanoTime()}").canonicalFile
        val prefix = "skills-main/skills/.curated/$skillId/"
        var extracted = false
        try {
            ZipInputStream(context.assets.open("official-skills.zip")).use { zip ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.name.startsWith(prefix) || entry.name == prefix) continue
                    val relative = entry.name.substring(prefix.length)
                    val output = File(staging, relative).canonicalFile
                    require(output.absolutePath.startsWith(staging.absolutePath + File.separator)) { "Unsafe archive entry" }
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { stream ->
                            while (true) { val count = zip.read(buffer); if (count < 0) break; if (count > 0) stream.write(buffer, 0, count) }
                        }
                        extracted = true
                    }
                }
            }
            require(extracted && File(staging, "SKILL.md").isFile) { "Skill not found in official catalog" }
            deleteTree(destination, root)
            if (!staging.renameTo(destination)) { copyTree(staging, destination); deleteTree(staging, root) }
            return destination
        } catch (error: Throwable) {
            runCatching { deleteTree(staging, root) }
            throw error
        }
    }

    fun uninstall(path: String) {
        val root = managedRoot.canonicalFile
        val skillFile = File(path).canonicalFile
        val directory = if (skillFile.name == "SKILL.md") skillFile.parentFile else skillFile
        require(directory.parentFile == root) { "Only managed skills can be removed" }
        deleteTree(directory, root)
    }

    private fun scan(directory: File, output: MutableList<NativeInstalledSkill>, seen: MutableSet<String>, depth: Int) {
        if (!directory.isDirectory || depth > 8) return
        val file = File(directory, "SKILL.md")
        if (file.isFile) runCatching {
            val canonical = file.canonicalPath
            if (seen.add(canonical)) {
                val summary = readSummary(file)
                val root = managedRoot.canonicalFile
                output.add(NativeInstalledSkill(summary.first, summary.second, canonical, file.parentFile.canonicalFile.parentFile == root))
            }
        }
        directory.listFiles { child -> child.isDirectory }?.forEach { scan(it, output, seen, depth + 1) }
    }

    private fun readSummary(file: File): Pair<String, String> {
        var name = file.parentFile.name; var description = ""; var frontmatter = false
        file.bufferedReader().useLines { lines ->
            lines.take(60).forEachIndexed { index, raw ->
                val line = raw.trim()
                if (index == 0 && line == "---") { frontmatter = true; return@forEachIndexed }
                if (frontmatter && line == "---") return@useLines
                if (frontmatter && line.startsWith("name:")) name = yamlValue(line.substring(5), name)
                if (frontmatter && line.startsWith("description:")) description = yamlValue(line.substring(12), "")
            }
        }
        return name to description
    }

    private fun yamlValue(value: String, fallback: String): String = value.trim().removeSurrounding("\"").removeSurrounding("'").ifBlank { fallback }

    private fun deleteTree(target: File, root: File) {
        if (!target.exists()) return
        val canonical = target.canonicalFile
        require(canonical.absolutePath.startsWith(root.absolutePath + File.separator)) { "Unsafe delete" }
        canonical.walkBottomUp().forEach { if (!it.delete() && it.exists()) throw IllegalStateException("Unable to delete ${it.absolutePath}") }
    }

    private fun copyTree(source: File, destination: File) {
        source.walkTopDown().forEach { item ->
            val relative = item.relativeTo(source)
            val output = File(destination, relative.path)
            if (item.isDirectory) output.mkdirs() else { output.parentFile?.mkdirs(); item.copyTo(output, overwrite = true) }
        }
    }
}
