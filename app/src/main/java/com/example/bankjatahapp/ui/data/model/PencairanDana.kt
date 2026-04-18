package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PencairanDana(

    @SerialName("id_pencairan")
    val idPencairan: String? = null,           // UUID auto-generate Supabase

    @SerialName("kode_pencairan")
    val kodePencairan: String = "",            // PCR-{timestamp}

    @SerialName("id_user")
    val idUser: String = "",

    @SerialName("jumlah_tarik")
    val jumlahTarik: Double = 0.0,

    @SerialName("biaya_admin")
    val biayaAdmin: Double = 0.0,

    @SerialName("jumlah_bersih")
    val jumlahBersih: Double = 0.0,

    @SerialName("metode_pencairan")
    val metodePencairan: String = "manual",

    @SerialName("bukti_transfer")
    val buktiTransfer: String? = null,

    @SerialName("bank_tujuan")
    val bankTujuan: String? = null,

    @SerialName("no_rekening_tujuan")
    val noRekeningTujuan: String? = null,

    @SerialName("nama_pemilik_rekening")
    val namaPemilikRekening: String? = null,

    @SerialName("status_request")
    val statusRequest: String = "menunggu",    // menunggu/diproses/selesai/gagal/ditolak

    @SerialName("tgl_request")
    val tglRequest: String? = null,

    @SerialName("tgl_selesai")
    val tglSelesai: String? = null,

    @SerialName("alasan_penolakan")
    val alasanPenolakan: String? = null,

    @SerialName("sumber_dana")
    val sumberDana: String = "setoran_minyak", // setoran_minyak/komisi_unit/komisi_afiliasi

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)