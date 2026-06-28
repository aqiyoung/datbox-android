package com.threel.openlist.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val username: String,
    val password: String = "",
    val role: Int = 1,
    val disabled: Boolean = false,
    val permission: Int = 0
)

@Serializable
data class Mount(
    val id: Int = 0,
    val name: String = "",
    val driver: String = "Local",
    val path: String = "",
    val status: Int = 1
)

@Serializable
data class Share(
    val id: String = "",
    val path: String = "",
    val name: String = "",
    val password: String = "",
    val expires: String = ""
)

@Serializable
data class Overview(
    val userCount: Int = 0,
    val mountCount: Int = 0,
    val shareCount: Int = 0
)

@Serializable
data class ManagementResponse(val code: Int = 0, val message: String = "")
