package com.threel.openlist.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 请求体 JSON 契约回归测试。
 *
 * 这些端点（上游 server/handles/fsmanage.go、user.go）以前是手拼字符串拼 JSON，
 * 文件名带引号会产出非法 JSON；字段名/结构也曾与上游不符（mkdir 发成 no-op、move 字段全错）。
 * 序列化器化之后由本测试锁定契约，任何人改 DTO 都会在这里被拦下。
 */
class RequestSerializationTest {

    private fun String.field(name: String) =
        OpenListJson.parseToJsonElement(this).jsonObject[name]

    // ===== fs/mkdir =====

    /** 上游 MkdirOrLinkReq 只收完整的新目录路径，没有 name 字段 */
    @Test
    fun `mkdir sends full path and no name field`() {
        val json = OpenListJson.encodeToString(FsMkdirRequest("/docs/新建 文件夹"))
        assertEquals("/docs/新建 文件夹", json.field("path")!!.jsonPrimitive.content)
        assertNull("mkdir 不该发 name 字段(上游无此字段)", json.field("name"))
    }

    // ===== fs/rename =====

    @Test
    fun `rename sends full path plus new name`() {
        val json = OpenListJson.encodeToString(FsRenameRequest("/docs/a.txt", "b.txt"))
        assertEquals("/docs/a.txt", json.field("path")!!.jsonPrimitive.content)
        assertEquals("b.txt", json.field("name")!!.jsonPrimitive.content)
    }

    // ===== fs/move =====

    /** 上游 MoveCopyReq = {src_dir, dst_dir, names}；旧实现发 {path, new_path} 是全错的 */
    @Test
    fun `move sends src_dir dst_dir names`() {
        val json = OpenListJson.encodeToString(
            FsMoveRequest("/a", "/b", listOf("x.txt", "y.txt"))
        )
        assertEquals("/a", json.field("src_dir")!!.jsonPrimitive.content)
        assertEquals("/b", json.field("dst_dir")!!.jsonPrimitive.content)
        val names = json.field("names")!!.jsonArray
        assertEquals("x.txt", names[0].jsonPrimitive.content)
        assertEquals("y.txt", names[1].jsonPrimitive.content)
        assertNull("旧实现的错误字段", json.field("path"))
        assertNull("旧实现的错误字段", json.field("new_path"))
    }

    // ===== fs/remove =====

    @Test
    fun `remove sends dir plus names`() {
        val json = OpenListJson.encodeToString(FsRemoveRequest("/docs", listOf("a.txt")))
        assertEquals("/docs", json.field("dir")!!.jsonPrimitive.content)
        val names = json.field("names")!!.jsonArray
        assertEquals("a.txt", names[0].jsonPrimitive.content)
    }

    // ===== fs/get (下载/预览签名) =====

    /** 文件名含引号/反斜杠: 手拼 JSON 产出非法 JSON(400 甚至 JSON 注入), 序列化器必须转义 */
    @Test
    fun `fs get escapes quotes and backslashes in path`() {
        val raw = """/dir/a"b\c.txt"""
        val json = OpenListJson.encodeToString(FsGetRequest(raw))
        // 序列化产物里必须是转义过的
        assertTrue("引号和反斜杠必须转义", json.contains("""a\"b\\c.txt"""))
        // 且整体是合法 JSON, 解析回来与原文一致
        assertEquals(raw, json.field("path")!!.jsonPrimitive.content)
    }

    // ===== share/create =====

    @Test
    fun `share create sends files array`() {
        val json = OpenListJson.encodeToString(
            ShareCreateRequest(listOf("/a/x.txt", "/b/y.txt"))
        )
        val files = json.field("files")!!.jsonArray
        assertEquals("/a/x.txt", files[0].jsonPrimitive.content)
        assertEquals("/b/y.txt", files[1].jsonPrimitive.content)
    }

    // ===== user/update (全字段覆盖端点) =====

    /** 上游 UpdateUser 整结构体绑定 + 全量落库: base_path/sso_id/authn 等 SerialName 必须对 */
    @Test
    fun `user update carries full struct with correct field names`() {
        val json = OpenListJson.encodeToString(
            UserUpdateRequest(
                id = 3, username = "bob", password = "s3cret", role = 0,
                permission = 128, basePath = "/data", disabled = true,
                ssoId = "sso-1", authn = "[]",
            )
        )
        assertEquals(3, json.field("id")!!.jsonPrimitive.int)
        assertEquals("bob", json.field("username")!!.jsonPrimitive.content)
        assertEquals("s3cret", json.field("password")!!.jsonPrimitive.content)
        assertEquals(0, json.field("role")!!.jsonPrimitive.int)
        assertEquals(128, json.field("permission")!!.jsonPrimitive.int)
        assertEquals("/data", json.field("base_path")!!.jsonPrimitive.content)
        assertEquals(true, json.field("disabled")!!.jsonPrimitive.boolean)
        assertEquals("sso-1", json.field("sso_id")!!.jsonPrimitive.content)
        assertNotNull(json.field("authn"))
    }

    /** 空密码 = 服务端保留原密码(上游显式处理): 必须显式发出空串, 而非整字段消失 */
    @Test
    fun `empty password is sent as empty string, not omitted`() {
        val json = OpenListJson.encodeToString(UserUpdateRequest(id = 1, username = "bob"))
        assertNotNull("password 字段不能缺席", json.field("password"))
        assertEquals("", json.field("password")!!.jsonPrimitive.content)
    }
}
