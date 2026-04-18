package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Setoran(

    @SerialName("id_setoran")
    val idSetoran: String? = null,              // UUID, auto generate Supabase

    @SerialName("kode_transaksi")
    val kodeTransaksi: String = "",             // TRX-SIM-QR-{timestamp}

    @SerialName("id_nasabah")
    val idNasabah: String = "",                 // dari scan QR / input manual

    @SerialName("id_unit")
    val idUnit: String = "",                    // id_user unit bisnis yang login

    @SerialName("id_batch")
    val idBatch: String? = null,                // null

    @SerialName("tgl_setoran")
    val tglSetoran: String? = null,             // timestamp saat submit

    @SerialName("berat_bersih_kg")
    val beratBersihKg: Double = 0.0,            // input user

    @SerialName("harga_satuan_snapshot")
    val hargaSatuanSnapshot: Double = 90.0,     // tetap 90

    @SerialName("level_bintang_snapshot")
    val levelBintangSnapshot: Int = 1,          // default 1

    @SerialName("komisi_sudah_dibagi")
    val komisiSudahDibagi: Boolean = true,      // TRUE

    @SerialName("harga_per_kg")
    val hargaPerKg: Double = 90.0,              // tetap 90

    @SerialName("total_rupiah_nasabah")
    val totalRupiahNasabah: Double = 0.0,       // berat_bersih_kg × harga_per_kg

    @SerialName("komisi_per_kg")
    val komisiPerKg: Double = 1000.0,           // tetap 1000

    @SerialName("total_komisi_unit")
    val totalKomisiUnit: Double = 0.0,          // komisi_per_kg × berat_bersih_kg

    @SerialName("status_setoran")
    val statusSetoran: String = "menunggu",     // default menunggu

    @SerialName("bukti_foto_minyak")
    val buktiFotoMinyak: String? = null,        // null untuk sekarang

    @SerialName("catatan_unit")
    val catatanUnit: String? = null,            // input opsional

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)