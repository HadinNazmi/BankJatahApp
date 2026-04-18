package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MasterBank(

    @SerialName("kode_bank")
    val kodeBank: String = "",

    @SerialName("nama_bank")
    val namaBank: String = "",

    @SerialName("status_bank")
    val statusBank: String = "aktif"
)