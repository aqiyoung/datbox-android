package com.datbox.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareVersionsTest {

    private val engine = GitHubUpdateEngine(UpdateConfig("aqiyoung", "openlist-android"))

    @Test
    fun `higher minor and patch win`() {
        assertTrue(engine.compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(engine.compareVersions("1.2.4", "1.2.3") > 0)
        assertTrue(engine.compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `equal versions compare to zero, with or without leading v`() {
        assertEquals(0, engine.compareVersions("1.2.3", "v1.2.3"))
        assertEquals(0, engine.compareVersions("V1.2.3", "1.2.3"))
        assertEquals(0, engine.compareVersions("1.2", "1.2.0"))
    }

    @Test
    fun `prerelease suffix is truncated so beta equals release`() {
        assertEquals(0, engine.compareVersions("1.2.3-beta.1", "1.2.3"))
        assertEquals(0, engine.compareVersions("1.2.3-beta.2", "1.2.3-beta.1"))
        assertTrue(engine.compareVersions("1.2.4-beta.1", "1.2.3") > 0)
    }

    @Test
    fun `build metadata after plus is ignored`() {
        assertEquals(0, engine.compareVersions("1.2.3+abc", "1.2.3"))
    }

    @Test
    fun `garbage segments degrade to zero instead of crashing`() {
        assertEquals(0, engine.compareVersions("abc", "0.0.0"))
    }
}
