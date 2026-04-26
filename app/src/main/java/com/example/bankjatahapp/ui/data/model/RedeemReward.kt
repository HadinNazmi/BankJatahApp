package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RedeemReward(

    @SerialName("id_redeem")
    val idRedeem: String = "",

    @SerialName("id_nasabah")
    val idNasabah: String = "",

    @SerialName("id_produk")
    val idProduk: String = "",

    @SerialName("poin_dipakai")
    val poinDipakai: Int = 0,

    @SerialName("status_redeem")
    val statusRedeem: String = "menunggu", // menunggu | disetujui | ditolak | selesai

    @SerialName("tgl_redeem")
    val tglRedeem: String? = null,

    @SerialName("tgl_selesai")
    val tglSelesai: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)