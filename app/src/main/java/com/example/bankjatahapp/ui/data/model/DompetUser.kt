package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DompetUser(

    @SerialName("id_dompet")
    val idDompet: String = "",

    @SerialName("saldo_nasabah")
    val saldoNasabah: Double = 0.0,

    @SerialName("pending_nasabah")
    val pendingNasabah: Double = 0.0,

    @SerialName("saldo_unit")
    val saldoUnit: Double = 0.0,

    @SerialName("pending_unit")
    val pendingUnit: Double = 0.0,

    @SerialName("poin_reward")
    val poinReward: Int = 0,

    @SerialName("saldo_afiliasi")
    val saldoAfiliasi: Double = 0.0,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)