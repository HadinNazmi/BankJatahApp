package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HargaMinyak(

    @SerialName("id_harga")
    val idHarga: String = "",

    @SerialName("id_wilayah")
    val idWilayah: String = "",

    @SerialName("harga_per_kg")
    val hargaPerKg: Double = 0.0,

    @SerialName("status_harga")
    val statusHarga: Boolean = true,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)