package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MasterKabupaten(
    @SerialName("id_kabupaten")
    val idKabupaten: String = "",

    @SerialName("provinsi_id")
    val provinsiId: String = "",

    @SerialName("nama_kabupaten")
    val namaKabupaten: String = "",

    @SerialName("created_at")
    val createdAt: String? = null
)