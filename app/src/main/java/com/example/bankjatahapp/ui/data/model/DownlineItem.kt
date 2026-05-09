package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Model untuk 1 nasabah downline dengan nested downlines-nya
@Serializable
data class DownlineItem(

    @SerialName("id_nasabah")
    val idNasabah: String = "",

    @SerialName("level_bintang")
    val levelBintang: Int? = 1,

    @SerialName("kategori_nasabah")
    val kategoriNasabah: String? = "pasif",

    @SerialName("total_setoran_lifetime")
    val totalSetoranLifetime: Double? = 0.0,

    @SerialName("created_at")
    val createdAt: String? = null,

    // Join ke tabel users
    @SerialName("users")
    val users: DownlineUserInfo? = null,

    // Downline generasi berikutnya (recursive 2 level)
    @SerialName("downlines")
    val downlines: List<DownlineItem>? = null
)

@Serializable
data class DownlineUserInfo(
    @SerialName("nama_lengkap")
    val namaLengkap: String = "",

    @SerialName("url_foto_profil")
    val urlFotoProfil: String? = null
)