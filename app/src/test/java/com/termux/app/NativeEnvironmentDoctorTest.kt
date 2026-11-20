package com.termux.app

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NativeEnvironmentDoctorTest {
    private fun check(id: String, state: NativeEnvState) = NativeEnvCheck(
        id = id,
        group = NativeEnvGroup.RUNTIME,
        state = state,
        titleZh = id,
        titleEn = id,
        detailZh = "",
        detailEn = "",
    )

    @Test
    fun orderingPutsFailuresBeforeEverythingElse() {
        val ordered = NativeEnvironmentDoctor.ordered(
            listOf(
                check("ok", NativeEnvState.OK),
                check("info", NativeEnvState.INFO),
                check("warn", NativeEnvState.WARN),
                check("fail", NativeEnvState.FAIL),
            ),
        )

        assertEquals(listOf("fail", "warn", "ok", "info"), ordered.map { it.id })
    }

    @Test
    fun countsAreCompleteEvenWhenAStateIsAbsent() {
        val counts = NativeEnvironmentDoctor.counts(
            listOf(check("a", NativeEnvState.OK), check("b", NativeEnvState.OK)),
        )

        assertEquals(2, counts[NativeEnvState.OK])
        assertEquals(0, counts[NativeEnvState.FAIL])
        assertEquals(0, counts[NativeEnvState.WARN])
        assertEquals(0, counts[NativeEnvState.INFO])
    }

    @Test
    fun workspaceFallsBackToAppHomeWhenTheConfiguredRootIsMissing() {
        val preferences = preferences()
        preferences.edit()
            .putBoolean("custom_project_root_enabled", true)
            .putString("custom_project_root", "/definitely/not/a/directory")
            .commit()

        assertEquals(
            File(TermuxConstants.TERMUX_HOME_DIR_PATH).absolutePath,
            NativeEnvironmentDoctor.currentWorkspace(preferences).absolutePath,
        )
    }

    @Test
    fun workspaceUsesTheConfiguredRootWhenItExists() {
        val root = File(RuntimeEnvironment.getApplication().cacheDir, "workspace-probe").apply { mkdirs() }
        val preferences = preferences()
        preferences.edit()
            .putBoolean("custom_project_root_enabled", true)
            .putString("custom_project_root", root.absolutePath)
            .commit()

        assertEquals(root.absolutePath, NativeEnvironmentDoctor.currentWorkspace(preferences).absolutePath)
    }

    @Test
    fun disablingTheCustomRootIgnoresTheStoredPath() {
        val root = File(RuntimeEnvironment.getApplication().cacheDir, "workspace-probe-2").apply { mkdirs() }
        val preferences = preferences()
        preferences.edit()
            .putBoolean("custom_project_root_enabled", false)
            .putString("custom_project_root", root.absolutePath)
            .commit()

        assertEquals(
            File(TermuxConstants.TERMUX_HOME_DIR_PATH).absolutePath,
            NativeEnvironmentDoctor.currentWorkspace(preferences).absolutePath,
        )
    }

    @Test
    fun mirrorHostComesFromTheFirstDebLine() {
        val sources = File(RuntimeEnvironment.getApplication().cacheDir, "sources.list").apply {
            writeText(
                """
                # comment
                deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main
                deb https://packages.termux.dev/apt/termux-main stable main
                """.trimIndent(),
            )
        }

        assertEquals(
            "mirrors.tuna.tsinghua.edu.cn",
            NativeEnvironmentDoctor.aptMirrorHost(sources),
        )
    }

    @Test
    fun missingSourcesFileYieldsNoHost() {
        val missing = File(RuntimeEnvironment.getApplication().cacheDir, "no-sources.list")
        assertTrue(NativeEnvironmentDoctor.aptMirrorHost(missing).isEmpty())
    }

    @Test
    fun processEnvironmentAlwaysDeclaresLibraryPathAndLocale() {
        val environment = NativeProcessEnv.base()

        assertEquals(TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH, environment["LD_LIBRARY_PATH"])
        assertEquals(NativeProcessEnv.DEFAULT_LANG, environment["LANG"])
        assertEquals(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, environment["TMPDIR"])
        assertTrue(environment["PATH"].orEmpty().startsWith(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH))
    }

    @Test
    fun overridesWinOverTheSharedBase() {
        val builder = ProcessBuilder("true")
        NativeProcessEnv.apply(builder, mapOf("TMPDIR" to "/custom/tmp"))

        assertEquals("/custom/tmp", builder.environment()["TMPDIR"])
        assertEquals(NativeProcessEnv.DEFAULT_LANG, builder.environment()["LANG"])
    }

    private fun preferences() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("env-doctor-test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
}
