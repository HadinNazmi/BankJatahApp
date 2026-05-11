package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MasterKecamatan(
    @SerialName("id_kecamatan")
    val idKecamatan: String = "",

    @SerialName("kabupaten_id")
    val kabupatenId: String = "",

    @SerialName("nama_kecamatan")
    val namaKecamatan: String = "",

    @SerialName("created_at")
    val createdAt: String? = null
)