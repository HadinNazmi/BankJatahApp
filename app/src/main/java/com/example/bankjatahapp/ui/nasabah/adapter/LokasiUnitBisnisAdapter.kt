package com.example.bankjatahapp.ui.nasabah.fragment

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.UnitBisnisData

class LokasiUnitBisnisAdapter(
    private val list: List<Triple<UnitBisnisData, String?, Double?>>
    // Triple: UnitBisnisData, NamaDisplay, JarakKm (null = tidak diketahui)
) : RecyclerView.Adapter<LokasiUnitBisnisAdapter.VH>() {

    private var expandedPosition: Int = -1

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNama: TextView           = v.findViewById(R.id.tvNamaUnit)
        val tvTipe: TextView           = v.findViewById(R.id.tvTipeUnit)
        val tvJarak: TextView          = v.findViewById(R.id.tvJarak)
        val layoutDetail: LinearLayout = v.findViewById(R.id.layoutDetail)
        val tvAlamat: TextView         = v.findViewById(R.id.tvAlamat)
        val tvJamOps: TextView         = v.findViewById(R.id.tvJamOperasional)
        val tvHariOps: TextView        = v.findViewById(R.id.tvHariOperasional)
        val tvKoordinat: TextView      = v.findViewById(R.id.tvKoordinat)
        val btnMaps: Button            = v.findViewById(R.id.btnBukaMaps)
        val ivFotoLokasi: ImageView    = v.findViewById(R.id.ivFotoLokasi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lokasi_unit_bisnis, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (unit, nama, jarakKm) = list[position]
        val ctx = holder.itemView.context

        holder.tvNama.text = nama ?: "Unit Bisnis"
        holder.tvTipe.text = when (unit.tipeUnit) {
            "kabupaten" -> "UB Kabupaten"
            else        -> "UB Kelurahan"
        }

        // Tampilkan jarak
        holder.tvJarak.text = when {
            jarakKm == null          -> " Lokasi tidak tersedia"
            jarakKm < 1.0            -> " ${(jarakKm * 1000).toInt()} m dari Anda"
            else                     -> " ${"%.1f".format(jarakKm)} km dari Anda"
        }

        val isExpanded = position == expandedPosition
        holder.layoutDetail.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val prev = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            notifyItemChanged(prev)
            notifyItemChanged(position)
        }

        holder.tvAlamat.text  = unit.alamat?.ifEmpty { "-" } ?: "-"
        holder.tvJamOps.text  = if (!unit.jamBuka.isNullOrEmpty() && !unit.jamTutup.isNullOrEmpty())
            "${unit.jamBuka} – ${unit.jamTutup}" else "-"
        holder.tvHariOps.text = unit.hariOperasional?.ifEmpty { "-" } ?: "-"

        val lat  = unit.lokasiLat
        val long = unit.lokasiLong
        if (lat != 0.0 && long != 0.0) {
            holder.tvKoordinat.text   = "${String.format("%.5f", lat)}, ${String.format("%.5f", long)}"
            holder.btnMaps.visibility = View.VISIBLE
            holder.btnMaps.setOnClickListener {
                val label = Uri.encode(nama ?: "Unit Bisnis")
                val uri   = Uri.parse("geo:$lat,$long?q=$lat,$long($label)")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                } else {
                    val browserUri = Uri.parse("https://maps.google.com/?q=$lat,$long")
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                }
            }
        } else {
            holder.tvKoordinat.text   = "Koordinat belum tersedia"
            holder.btnMaps.visibility = View.GONE
        }

        val fotoUrl = unit.fotoLokasi
        if (!fotoUrl.isNullOrEmpty()) {
            holder.ivFotoLokasi.visibility = View.VISIBLE
            Glide.with(ctx)
                .load(fotoUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_bg_aktivitas_orange)
                .into(holder.ivFotoLokasi)
        } else {
            holder.ivFotoLokasi.visibility = View.GONE
        }
    }

    override fun getItemCount() = list.size
}