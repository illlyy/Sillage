package com.termux.app

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FcodeAboutInfoTest {
    @Test
    fun publicProjectMetadataPointsToTheOfficialRepository() {
        val repository = URI(FcodeAboutInfo.REPOSITORY_URL)

        assertEquals("Sillage", FcodeAboutInfo.APP_NAME)
        assertEquals("Fcode", FcodeAboutInfo.PROJECT_NAME)
        assertEquals("ILY_op", FcodeAboutInfo.AUTHOR)
        assertEquals("https", repository.scheme)
        assertEquals("github.com", repository.host)
        assertTrue(repository.path.endsWith("/illlyy/Fcode"))
    }
}
