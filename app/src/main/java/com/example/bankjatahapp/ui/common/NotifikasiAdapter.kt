package com.example.bankjatahapp.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.Notification
import com.example.bankjatahapp.databinding.ItemNotifikasiBinding

class NotifikasiAdapter(
    private var list: List<Notification>,
    private val onClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotifikasiAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemNotifikasiBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotifikasiBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notif = list[position]

        holder.binding.tvJudul.text = notif.title
        holder.binding.tvPesan.text = notif.message
        holder.binding.tvWaktu.text = formatWaktu(notif.createdAt)

        // Icon & background berdasarkan type
        val (iconRes, bgRes) = when (notif.type) {
            "success" -> Pair(R.drawable.ic_nav_reward,  R.drawable.ic_bg_status_berhasil)
            "warning" -> Pair(R.drawable.ic_notifikasi,  R.drawable.ic_bg_status_pending)
            "error"   -> Pair(R.drawable.ic_notifikasi,  R.drawable.ic_bg_status_gagal)
            else      -> Pair(R.drawable.ic_notifikasi,  R.drawable.ic_bg_aktivitas_orange)
        }
        holder.binding.ivIcon.setImageResource(iconRes)
        holder.binding.ivIcon.setBackgroundResource(bgRes)

        // Dot merah jika belum dibaca
        holder.binding.viewUnread.visibility = if (!notif.isRead) View.VISIBLE else View.GONE

        // Background card sedikit beda jika belum dibaca
        if (!notif.isRead) {
            holder.binding.root.setCardBackgroundColor(
                holder.binding.root.context.getColor(R.color.white)
            )
            holder.binding.tvJudul.alpha = 1.0f
        } else {
            holder.binding.tvJudul.alpha = 0.6f
        }

        holder.binding.root.setOnClickListener { onClick(notif) }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<Notification>) {
        list = newList
        notifyDataSetChanged()
    }

    private fun formatWaktu(createdAt: String?): String {
        if (createdAt == null) return ""
        return try {
            val bulan = mapOf(
                "01" to "Jan", "02" to "Feb", "03" to "Mar", "04" to "Apr",
                "05" to "Mei", "06" to "Jun", "07" to "Jul", "08" to "Agu",
                "09" to "Sep", "10" to "Okt", "11" to "Nov", "12" to "Des"
            )
            val tgl = createdAt.substring(8, 10)
            val bln = bulan[createdAt.substring(5, 7)] ?: createdAt.substring(5, 7)
            val thn = createdAt.substring(0, 4)
            val jam = createdAt.substring(11, 16)
            "$tgl $bln $thn • $jam"
        } catch (e: Exception) {
            createdAt.take(10)
        }
    }
}