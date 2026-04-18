package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NasabahData(

    @SerialName("id_nasabah")
    val idNasabah: String = "",

    @SerialName("alamat_rumah")
    val alamatRumah: String? = null,

    @SerialName("kode_referral")
    val kodeReferral: String? = null,

    @SerialName("id_sponsor")
    val idSponsor: String? = null,

    @SerialName("level_bintang")
    val levelBintang: Int? = 1,              // 1–8

    @SerialName("total_berat_group")
    val totalBeratGroup: Double? = 0.0,

    @SerialName("total_setoran_lifetime")
    val totalSetoranLifetime: Double? = 0.0, // total kg disetor sepanjang waktu

    @SerialName("blokir_pencairan")
    val blokirPencairan: Boolean? = false,

    @SerialName("bank_code")
    val bankCode: String? = null,

    @SerialName("no_rekening")
    val noRekening: String? = null,

    @SerialName("atas_nama_rekening")
    val atasNamaRekening: String? = null,

    @SerialName("nik")
    val nik: String? = null,

    @SerialName("kategori_nasabah")
    val kategoriNasabah: String? = "pasif",

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)