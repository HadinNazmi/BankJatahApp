package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnitBisnisData(

    @SerialName("id_unit_bisnis")
    val idUnitBisnis: String = "",          // FK → users.id_user

    @SerialName("id_wilayah")
    val idWilayah: String? = null,

    @SerialName("tipe_unit")
    val tipeUnit: String = "kelurahan",     // "kelurahan" atau "kecamatan"

    @SerialName("nama_usaha")
    val namaUsaha: String? = null,

    @SerialName("bank_code")
    val bankCode: String? = null,

    @SerialName("no_rekening")
    val noRekening: String? = null,

    @SerialName("atas_nama_rekening")
    val atasNamaRekening: String? = null,

    @SerialName("alamat")
    val alamat: String? = null,

    @SerialName("lokasi_lat")
    val lokasiLat: Double = 0.0,

    @SerialName("lokasi_long")
    val lokasiLong: Double = 0.0,

    @SerialName("foto_lokasi")
    val fotoLokasi: String? = null,

    @SerialName("transaksi_harian")
    val transaksiHarian: Double = 0.0,

    @SerialName("jam_buka")
    val jamBuka: String? = null,

    @SerialName("jam_tutup")
    val jamTutup: String? = null,

    @SerialName("hari_operasional")
    val hariOperasional: String? = null,

    @SerialName("status_verifikasi_unit")
    val statusVerifikasiUnit: String = "menunggu",  // "menunggu","disetujui","ditolak"

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)