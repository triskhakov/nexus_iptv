package com.nexus.iptv.core.network.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfo? = null
)

@Serializable
data class XtreamUserInfo(
    @SerialName("username") val username: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("auth") val auth: Int = 0,
    @SerialName("exp_date") val expDateEpochSec: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    @SerialName("active_cons") val activeConnections: String? = null
)

@Serializable
data class XtreamServerInfo(
    @SerialName("url") val url: String = "",
    @SerialName("port") val port: String = "80",
    @SerialName("https_port") val httpsPort: String? = null,
    @SerialName("server_protocol") val serverProtocol: String = "http",
    @SerialName("timezone") val timezone: String? = null
)

@Serializable
data class XtreamCategoryDto(
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String
)

@Serializable
data class XtreamLiveStreamDto(
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("stream_type") val streamType: String = "live",
    @SerialName("stream_id") val streamId: Long = 0L,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("tv_archive") val tvArchive: Int = 0,
    @SerialName("tv_archive_duration") val tvArchiveDurationDays: Int = 0
)
