package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DompetUser(

    @SerialName("id_dompet")
    val idDompet: String = "",

    // ==========================================
    // SALDO TABUNGAN NASABAH (dari setoran minyak)
    // ==========================================

    @SerialName("saldo_nasabah")
    val saldoNasabah: Double = 0.0,

    @SerialName("pending_nasabah")
    val pendingNasabah: Double = 0.0,

    // ==========================================
    // SALDO UNIT BISNIS (komisi dari setoran nasabah ke UB)
    // ==========================================

    @SerialName("saldo_unit")
    val saldoUnit: Double = 0.0,

    @SerialName("pending_unit")
    val pendingUnit: Double = 0.0,

    // ==========================================
    // SALDO AFILIASI / BONUS (komisi jaringan, diisi trigger affiliate_commission_distribution)
    // Dicairkan via pencairan_dana dengan sumber_dana = 'komisi_afiliasi'
    // ==========================================

    @SerialName("saldo_afiliasi")
    val saldoAfiliasi: Double = 0.0,

    @SerialName("pending_afiliasi")
    val pendingAfiliasi: Double = 0.0,

    // ==========================================
    // POIN REWARD (dari setoran minyak, untuk redeem produk)
    // ==========================================

    @SerialName("poin_reward")
    val poinReward: Int = 0,

    @SerialName("poin_pending")
    val poinPending: Int = 0,

    // ==========================================
    // AUDIT
    // ==========================================

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)