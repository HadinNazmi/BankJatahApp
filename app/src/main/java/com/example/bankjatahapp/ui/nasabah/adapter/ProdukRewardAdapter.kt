package com.example.bankjatahapp.ui.nasabah.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.ProdukReward

class ProdukRewardAdapter(
    private var listProduk: List<ProdukReward>,
    private val onTukarClick: (ProdukReward) -> Unit
) : RecyclerView.Adapter<ProdukRewardAdapter.ViewHolder>() {
    private var semuaDisabled = false
    private var sudahDiklaim: Set<String> = emptySet() // ← TAMBAH INI
    private var tampilSemua = false

    fun setTampilSemua(semua: Boolean) {
        tampilSemua = semua
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView        = view.findViewById(R.id.ivFotoProduk)
        val ivPlaceholder: ImageView = view.findViewById(R.id.ivPlaceholder)
        val tvStokHabis: TextView    = view.findViewById(R.id.tvStokHabis)
        val tvNama: TextView         = view.findViewById(R.id.tvNamaProduk)
        val tvPoin: TextView         = view.findViewById(R.id.tvPoin)
        val tvStok: TextView         = view.findViewById(R.id.tvStok)
        val btnTukar: Button         = view.findViewById(R.id.btnTukar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_produk_reward, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val produk = listProduk[position]

        holder.tvNama.text = produk.namaProduk
        holder.tvPoin.text = "${formatAngka(produk.poinDibutuhkan)} poin"
        holder.tvStok.text = "Stok ${produk.stok}"

        // ===== LOAD FOTO DARI SUPABASE STORAGE =====
        val urlFoto = produk.fotoProduk
        if (!urlFoto.isNullOrEmpty()) {
            holder.ivFoto.visibility        = View.VISIBLE
            holder.ivPlaceholder.visibility = View.GONE

            Glide.with(holder.ivFoto.context)
                .load(urlFoto)
                .apply(
                    RequestOptions()
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_nav_reward)
                        .error(R.drawable.ic_nav_reward)
                )
                .into(holder.ivFoto)
        } else {
            holder.ivFoto.visibility        = View.GONE
            holder.ivPlaceholder.visibility = View.VISIBLE
        }

// ===== STATUS STOK =====
        if (produk.stok <= 0) {
            holder.tvStokHabis.visibility = View.VISIBLE
            holder.btnTukar.isEnabled     = false
            holder.btnTukar.alpha         = 0.5f
            holder.btnTukar.text          = "Habis"
        } else {
            holder.tvStokHabis.visibility = View.GONE
            holder.btnTukar.isEnabled     = true
            holder.btnTukar.alpha         = 1f
            holder.btnTukar.text          = "Tukar"
        }

        // Cek apakah produk ini sudah diklaim di siklus aktif
        val sudahKlaimProdukIni = sudahDiklaim.contains(produk.idProduk)
        if (sudahKlaimProdukIni) {
            holder.btnTukar.isEnabled = false
            holder.btnTukar.alpha     = 0.5f
            holder.btnTukar.text      = "Sudah Diklaim"
        }

        if (semuaDisabled) {
            holder.btnTukar.isEnabled = false
            holder.btnTukar.alpha     = 0.4f
        }

        holder.btnTukar.setOnClickListener {
            if (produk.stok > 0 && !semuaDisabled && !sudahKlaimProdukIni) {
                onTukarClick(produk)
            }
        }
    }

    fun setSemuaDisabled(disabled: Boolean) {
        semuaDisabled = disabled
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int =
        if (tampilSemua) listProduk.size else minOf(4, listProduk.size)

    fun updateData(data: List<ProdukReward>) {
        listProduk = data
        notifyDataSetChanged()
    }

    fun updateDataDenganKlaim(data: List<ProdukReward>, klaim: Set<String>) {
        listProduk   = data
        sudahDiklaim = klaim
        notifyDataSetChanged()
    }

    fun currentList(): List<ProdukReward> = listProduk

    private fun formatAngka(angka: Int): String =
        String.format("%,d", angka).replace(',', '.')
}