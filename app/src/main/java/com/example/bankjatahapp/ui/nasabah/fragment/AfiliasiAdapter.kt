package com.example.bankjatahapp.ui.nasabah.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.databinding.ItemAfiliasiBinding

class AfiliasiAdapter(
    private val users: List<User>,
    private val nasabahList: List<NasabahData>
) : RecyclerView.Adapter<AfiliasiAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAfiliasiBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAfiliasiBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        val nasabah = nasabahList.find { it.idNasabah == user.idUser }

        holder.binding.tvNamaAfiliasi.text = user.namaLengkap
        holder.binding.tvNoTelp.text = if (!user.noTelp.isNullOrEmpty()) user.noTelp!! else "-"
        holder.binding.tvKategori.text = nasabah?.kategoriNasabah?.replaceFirstChar { it.uppercase() } ?: "-"
        holder.binding.tvKodeReferral.text = "Ref: ${nasabah?.kodeReferral ?: "-"}"

        // Warna badge kategori
        val badgeColor = when (nasabah?.kategoriNasabah) {
            "aktif" -> android.graphics.Color.parseColor("#4CAF50")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
        holder.binding.tvKategori.setBackgroundColor(badgeColor)
    }

    override fun getItemCount() = users.size
}