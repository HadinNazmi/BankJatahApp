package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(

    @SerialName("id_notification")
    val idNotification: String = "",

    @SerialName("id_user")
    val idUser: String = "",

    @SerialName("judul")
    val title: String = "",

    @SerialName("pesan")
    val message: String = "",

    @SerialName("type")
    val type: String = "info",

    @SerialName("link")
    val link: String? = null,

    @SerialName("is_read")
    val isRead: Boolean = false,

    @SerialName("id_referensi")
    val idReferensi: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)