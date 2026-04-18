package com.example.bankjatahapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProdukReward(

    @SerialName("id_produk")
    val idProduk: String = "",

    @SerialName("nama_produk")
    val namaProduk: String = "",

    @SerialName("deskripsi")
    val deskripsi: String? = null,

    @SerialName("poin_dibutuhkan")
    val poinDibutuhkan: Int = 0,

    @SerialName("stok")
    val stok: Int = 0,

    @SerialName("foto_produk")
    val fotoProduk: String? = null,

    @SerialName("status_produk")
    val statusProduk: String = "aktif",     // "aktif" atau "nonaktif"

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)