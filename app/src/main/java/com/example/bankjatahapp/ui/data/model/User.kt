package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(

    @SerialName("id_user")
    val idUser: String = "",

    @SerialName("email")
    val email: String = "",

    @SerialName("nama_lengkap")
    val namaLengkap: String = "",

    @SerialName("role")
    val role: String = "",

    @SerialName("url_foto_profil")
    val urlFotoProfil: String? = null,

    @SerialName("no_telp")
    val noTelp: String? = null,

    @SerialName("status_akun")
    val statusAkun: String = "aktif",

    @SerialName("fcm_token")
    val fcmToken: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)