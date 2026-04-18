package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MutasiSaldo(

    @SerialName("id_mutasi")
    val idMutasi: String? = null,

    @SerialName("id_user")
    val idUser: String = "",

    // setoran_minyak / komisi_unit / komisi_afiliasi / pencairan_dana / penyesuaian_admin / pendapatan_perusahaan
    @SerialName("tipe_transaksi")
    val tipeTransaksi: String = "",

    // masuk / keluar
    @SerialName("arus_dana")
    val arusDana: String = "",

    @SerialName("nominal")
    val nominal: Double = 0.0,

    @SerialName("saldo_sebelum")
    val saldoSebelum: Double? = null,

    @SerialName("saldo_sesudah")
    val saldoSesudah: Double? = null,

    @SerialName("deskripsi")
    val deskripsi: String? = null,

    @SerialName("id_referensi")
    val idReferensi: String? = null,           // UUID referensi ke transaksi terkait

    @SerialName("created_at")
    val createdAt: String? = null
)