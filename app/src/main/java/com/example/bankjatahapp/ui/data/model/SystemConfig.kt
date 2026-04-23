package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemConfig(

    @SerialName("id_config")
    val idConfig: Int = 1,

    @SerialName("bonus_ub_kelurahan")
    val bonusUbKelurahan: Double = 240.0,

    @SerialName("bonus_ub_kabupaten")
    val bonusUbKabupaten: Double = 140.0
)