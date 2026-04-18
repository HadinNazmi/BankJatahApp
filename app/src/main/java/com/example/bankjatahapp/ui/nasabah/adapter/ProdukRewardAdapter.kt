package com.example.bankjatahapp.ui.nasabah.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.ProdukReward

class ProdukRewardAdapter(
    private var listProduk: List<ProdukReward>,
    private val onTukarClick: (ProdukReward) -> Unit
) : RecyclerView.Adapter<ProdukRewardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView     = view.findViewById(R.id.ivFotoProduk)
        val ivPlaceholder: ImageView = view.findViewById(R.id.ivPlaceholder)
        val tvStokHabis: TextView = view.findViewById(R.id.tvStokHabis)
        val tvNama: TextView      = view.findViewById(R.id.tvNamaProduk)
        val tvPoin: TextView      = view.findViewById(R.id.tvPoin)
        val tvStok: TextView      = view.findViewById(R.id.tvStok)
        val btnTukar: Button      = view.findViewById(R.id.btnTukar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_produk_reward, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val produk = listProduk[position]

        holder.tvNama.text  = produk.namaProduk
        holder.tvPoin.text  = "${formatAngka(produk.poinDibutuhkan)} poin"
        holder.tvStok.text  = "Stok ${produk.stok}"

        // Foto produk — tampilkan placeholder kalau tidak ada URL
        if (!produk.fotoProduk.isNullOrEmpty()) {
            // TODO: load gambar pakai Glide/Coil nanti
            // Glide.with(holder.ivFoto).load(produk.fotoProduk).into(holder.ivFoto)
            holder.ivFoto.visibility        = View.VISIBLE
            holder.ivPlaceholder.visibility = View.GONE
        } else {
            holder.ivFoto.visibility        = View.GONE
            holder.ivPlaceholder.visibility = View.VISIBLE
        }

        // Stok habis
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

        holder.btnTukar.setOnClickListener {
            if (produk.stok > 0) {
                onTukarClick(produk)
            }
        }
    }

    override fun getItemCount(): Int = listProduk.size

    fun updateData(data: List<ProdukReward>) {
        listProduk = data
        notifyDataSetChanged()
    }

    private fun formatAngka(angka: Int): String {
        return String.format("%,d", angka).replace(',', '.')
    }
}