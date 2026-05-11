package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MasterProvinsi(
    @SerialName("id_provinsi")
    val idProvinsi: String = "",

    @SerialName("nama_provinsi")
    val namaProvinsi: String = "",

    @SerialName("created_at")
    val createdAt: String? = null
)