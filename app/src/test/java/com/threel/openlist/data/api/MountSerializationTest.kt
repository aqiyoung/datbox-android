package com.threel.openlist.data.api

import com.threel.openlist.data.model.Mount
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 挂载(storage)编辑回传的回归测试。
 *
 * 上游 UpdateStorage 是整结构体绑定 + 全量落库：body 里缺哪个字段哪个就被清零。
 * 客户端编辑挂载时直接 POST 整个 [Mount]（/api/admin/storage/update），
 * 本测试模拟「列表拿到 → 原样回传」，锁死 addition(驱动配置)/disabled/order 等字段不丢——
 * addition 丢了驱动的 token/根目录配置，整个挂载直接废掉。
 */
class MountSerializationTest {

    /** 上游 storage/list 返回的典型条目（model.Storage 的 JSON），含服务端未来新增字段 */
    private val listJson = """
        {
          "id": 7,
          "mount_path": "/local",
          "order": 5,
          "driver": "Local",
          "cache_expiration": 30,
          "status": "work",
          "addition": "{\"root_folder_path\":\"/data/files\",\"thumbnail\":false}",
          "remark": "主目录",
          "modified": "2025-01-02T03:04:05+08:00",
          "disabled": true,
          "disable_index": true,
          "enable_sign": false,
          "order_by": "name",
          "order_direction": "ASC",
          "extract_folder": "",
          "web_proxy": true,
          "webdav_policy": "native_proxy",
          "proxy_range": false,
          "down_proxy_url": "https://proxy.example.com",
          "disable_proxy_sign": false,
          "custom_cache_policies": "[\"cache1\"]",
          "future_server_field": {"nested": 1}
        }
    """.trimIndent()

    private fun String.field(name: String) =
        OpenListJson.parseToJsonElement(this).jsonObject[name]

    /** 「列表拿到 → 原样回传」: 每个字段都必须出现在 update 请求体里 */
    @Test
    fun `update request round-trips every upstream field`() {
        val mount = OpenListJson.decodeFromString<Mount>(listJson)
        val out = OpenListJson.encodeToString(mount)

        assertEquals(7, out.field("id")!!.jsonPrimitive.int)
        assertEquals("/local", out.field("mount_path")!!.jsonPrimitive.content)
        assertEquals("Local", out.field("driver")!!.jsonPrimitive.content)
        assertEquals("work", out.field("status")!!.jsonPrimitive.content)
        assertEquals(5, out.field("order")!!.jsonPrimitive.int)
        assertEquals(30, out.field("cache_expiration")!!.jsonPrimitive.int)
        assertEquals("主目录", out.field("remark")!!.jsonPrimitive.content)
        // 驱动配置: 编辑一次就丢的话挂载直接废掉 —— 最关键的断言
        assertEquals(
            """{"root_folder_path":"/data/files","thumbnail":false}""",
            out.field("addition")!!.jsonPrimitive.content
        )
        assertEquals(true, out.field("disabled")!!.jsonPrimitive.boolean)
        assertEquals(true, out.field("disable_index")!!.jsonPrimitive.boolean)
        assertEquals(false, out.field("enable_sign")!!.jsonPrimitive.boolean)
        assertEquals("name", out.field("order_by")!!.jsonPrimitive.content)
        assertEquals("ASC", out.field("order_direction")!!.jsonPrimitive.content)
        assertEquals("", out.field("extract_folder")!!.jsonPrimitive.content)
        assertEquals(true, out.field("web_proxy")!!.jsonPrimitive.boolean)
        assertEquals("native_proxy", out.field("webdav_policy")!!.jsonPrimitive.content)
        assertEquals(false, out.field("proxy_range")!!.jsonPrimitive.boolean)
        assertEquals("https://proxy.example.com", out.field("down_proxy_url")!!.jsonPrimitive.content)
        assertEquals(false, out.field("disable_proxy_sign")!!.jsonPrimitive.boolean)
        assertEquals("""["cache1"]""", out.field("custom_cache_policies")!!.jsonPrimitive.content)
    }

    /** 等于 Kotlin 默认值的字段也必须显式发出:
     *  driver="Local" 若被省略, 服务端 bind 到空串会判定"改了驱动"直接报错 */
    @Test
    fun `default-valued fields are still serialized`() {
        val out = OpenListJson.encodeToString(OpenListJson.decodeFromString<Mount>(listJson))
        assertNotNull(out.field("driver"))
        assertNotNull(out.field("status"))
        assertNotNull(out.field("order"))
        assertNotNull(out.field("remark"))
    }

    /** 服务端未来新增字段不能导致解析崩溃(编辑挂载入口) */
    @Test
    fun `unknown response fields are ignored on decode`() {
        val mount = OpenListJson.decodeFromString<Mount>(
            """{"id":1,"mount_path":"/x","driver":"Aliyundrive","future_field":{"a":1}}"""
        )
        assertEquals("Aliyundrive", mount.driver)
        assertEquals("/x", mount.mountPath)
    }
}
