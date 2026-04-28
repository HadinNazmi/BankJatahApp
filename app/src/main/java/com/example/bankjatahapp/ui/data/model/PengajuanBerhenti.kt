package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PengajuanBerhenti(
    @SerialName("id_pengajuan")
    val idPengajuan: String? = null,

    @SerialName("id_user")
    val idUser: String,

    @SerialName("tipe")
    val tipe: String, // "nasabah" | "unit_bisnis" | "total"

    @SerialName("alasan")
    val alasan: String? = null,

    @SerialName("id_pencairan_saldo")
    val idPencairanSaldo: String? = null,

    @SerialName("id_pencairan_komisi")
    val idPencairanKomisi: String? = null,

    @SerialName("id_pencairan_afiliasi")
    val idPencairanAfiliasi: String? = null,

    @SerialName("status")
    val status: String = "menunggu", // "menunggu" | "diproses" | "disetujui" | "ditolak"

    @SerialName("catatan_admin")
    val catatanAdmin: String? = null,

    @SerialName("bukti_transfer_final")
    val buktiTransferFinal: String? = null,

    @SerialName("diproses_oleh")
    val diprosesOleh: String? = null,

    @SerialName("processed_at")
    val processedAt: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

// Request insert — hanya field yang diisi dari mobile
@Serializable
data class PengajuanBerhentiInsert(
    @SerialName("id_user")
    val idUser: String,

    @SerialName("tipe")
    val tipe: String,

    @SerialName("alasan")
    val alasan: String? = null,

    @SerialName("status")
    val status: String = "menunggu"
)