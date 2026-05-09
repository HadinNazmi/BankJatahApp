package com.example.bankjatahapp.ui.nasabah.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.DownlineItem
import java.util.Locale

class DownlineTreeAdapter(
    private val items: List<DownlineItem>
) : RecyclerView.Adapter<DownlineTreeAdapter.VH>() {

    private val expanded = mutableSetOf<String>()

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val tvNamaG1: TextView = view.findViewById(R.id.tvNamaG1)
        val tvInisialG1: TextView = view.findViewById(R.id.tvInisialG1)
        val tvInfoG1: TextView = view.findViewById(R.id.tvInfoG1)
        val tvSetoranG1: TextView = view.findViewById(R.id.tvSetoranG1)
        val tvExpandBtn: TextView = view.findViewById(R.id.tvExpandBtn)
        val containerG2: LinearLayout = view.findViewById(R.id.containerG2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_downline_g1, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g1 = items[position]
        val namaG1 = g1.users?.namaLengkap ?: "User"

        holder.tvNamaG1.text = namaG1
        holder.tvInisialG1.text = namaG1.take(1).uppercase()
        holder.tvInfoG1.text = "B${g1.levelBintang ?: 1} • ${(g1.kategoriNasabah ?: "pasif")}"
        holder.tvSetoranG1.text = "${String.format(Locale.US, "%,.1f", g1.totalSetoranLifetime ?: 0.0)} Kg"

        val g2List = g1.downlines ?: emptyList()
        val isExpanded = g1.idNasabah in expanded

        holder.tvExpandBtn.visibility = if (g2List.isEmpty()) View.GONE else View.VISIBLE
        holder.tvExpandBtn.text = if (isExpanded) "▲ Sembunyikan (${g2List.size})" else "▼ Lihat ${g2List.size} downline"
        holder.containerG2.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            holder.containerG2.removeAllViews()
            val inflater = LayoutInflater.from(holder.itemView.context)

            g2List.forEach { g2 ->
                val g2View = inflater.inflate(R.layout.item_downline_g2, holder.containerG2, false)
                val namaG2 = g2.users?.namaLengkap ?: "User"

                g2View.findViewById<TextView>(R.id.tvNamaG2).text = namaG2
                g2View.findViewById<TextView>(R.id.tvInisialG2).text = namaG2.take(1).uppercase()
                g2View.findViewById<TextView>(R.id.tvInfoG2).text = "B${g2.levelBintang ?: 1} • ${g2.kategoriNasabah ?: "pasif"}"
                g2View.findViewById<TextView>(R.id.tvSetoranG2).text = "${String.format(Locale.US, "%,.1f", g2.totalSetoranLifetime ?: 0.0)} Kg"

                val containerG3 = g2View.findViewById<LinearLayout>(R.id.containerG3)
                val g3List = g2.downlines ?: emptyList()

                if (g3List.isNotEmpty()) {
                    containerG3.visibility = View.VISIBLE
                    g3List.forEach { g3 ->
                        val g3View = inflater.inflate(R.layout.item_downline_g3, containerG3, false)
                        val namaG3 = g3.users?.namaLengkap ?: "User"

                        g3View.findViewById<TextView>(R.id.tvNamaG3).text = namaG3
                        g3View.findViewById<TextView>(R.id.tvInisialG3).text = namaG3.take(1).uppercase()
                        g3View.findViewById<TextView>(R.id.tvInfoG3).text = "B${g3.levelBintang ?: 1} • ${g3.kategoriNasabah ?: "pasif"}"
                        g3View.findViewById<TextView>(R.id.tvSetoranG3).text = "${String.format(Locale.US, "%,.1f", g3.totalSetoranLifetime ?: 0.0)} Kg"

                        containerG3.addView(g3View)
                    }
                }
                holder.containerG2.addView(g2View)
            }
        }

        val toggleAction = View.OnClickListener {
            if (isExpanded) expanded.remove(g1.idNasabah) else expanded.add(g1.idNasabah)
            notifyItemChanged(position)
        }
        holder.view.setOnClickListener(toggleAction)
        holder.tvExpandBtn.setOnClickListener(toggleAction)
    }
}