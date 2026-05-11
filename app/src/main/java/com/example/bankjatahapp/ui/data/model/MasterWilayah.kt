package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MasterWilayah(
    @SerialName("id_wilayah")
    val idWilayah: String = "",

    @SerialName("kecamatan_id")
    val kecamatanId: String = "",

    @SerialName("kode_wilayah")
    val kodeWilayah: String = "",

    @SerialName("nama_wilayah")
    val namaWilayah: String = "",

    @SerialName("status_wilayah")
    val statusWilayah: String = "aktif",

    @SerialName("created_at")
    val createdAt: String? = null
)