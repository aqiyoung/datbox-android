package com.datbox.app.data.api

import com.datbox.app.data.model.PublicSettingsResponse
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GET /api/public/settings 的契约回归测试。
 *
 * 关于页的「OpenList 版本」药丸读的是 data.version (上游 op.GetPublicSettingsMap,
 * version 取 setting item 的 READONLY 值)。字段/结构一改, 这里要拦下。
 */
class PublicSettingsResponseTest {

    @Test
    fun `reads the server version out of public settings`() {
        val body = """
            {"code":200,"message":"success","data":{
              "version":"v4.2.2 (Commit: abc1234) - Frontend: v4.0.0 - Build at: 2026-01-01",
              "site_title":"OpenList"
            }}
        """.trimIndent()
        val resp = OpenListJson.decodeFromString<PublicSettingsResponse>(body)
        assertEquals(200, resp.code)
        assertEquals(
            "v4.2.2 (Commit: abc1234) - Frontend: v4.0.0 - Build at: 2026-01-01",
            resp.data?.get("version"),
        )
    }

    @Test
    fun `a server that does not publish the version yields null`() {
        val resp = OpenListJson.decodeFromString<PublicSettingsResponse>(
            """{"code":200,"message":"success","data":{"site_title":"OpenList"}}"""
        )
        assertNull(resp.data?.get("version"))
    }
}
