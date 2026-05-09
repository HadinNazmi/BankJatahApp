package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bankjatahapp.data.model.DownlineItem
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentListAfiliasiBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import java.util.Locale

class ListAfiliasiFragment : Fragment() {

    private var _binding: FragmentListAfiliasiBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListAfiliasiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvAfiliasi.layoutManager = LinearLayoutManager(requireContext())
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadAfiliasi()
    }

    private fun loadAfiliasi() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvAfiliasi.visibility = View.GONE

                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val hasil = client.postgrest.from("nasabah_data").select(columns = Columns.raw("""
                    id_nasabah, level_bintang, kategori_nasabah, total_setoran_lifetime, created_at,
                    users!id_nasabah (nama_lengkap, url_foto_profil),
                    downlines:nasabah_data!id_sponsor (
                        id_nasabah, level_bintang, kategori_nasabah, total_setoran_lifetime, created_at,
                        users!id_nasabah (nama_lengkap, url_foto_profil),
                        downlines:nasabah_data!id_sponsor (
                            id_nasabah, level_bintang, kategori_nasabah, total_setoran_lifetime, created_at,
                            users!id_nasabah (nama_lengkap, url_foto_profil)
                        )
                    )
                """.trimIndent())) {
                    filter { eq("id_sponsor", idUser) }
                }.decodeList<DownlineItem>()

                val totalG1 = hasil.size
                val totalG2 = hasil.sumOf { it.downlines?.size ?: 0 }
                val totalG3 = hasil.sumOf { g1 -> g1.downlines?.sumOf { it.downlines?.size ?: 0 } ?: 0 }
                val totalSemua = totalG1 + totalG2 + totalG3

                val totalOmset = hasil.sumOf { g1 ->
                    (g1.totalSetoranLifetime ?: 0.0) +
                            (g1.downlines?.sumOf { g2 ->
                                (g2.totalSetoranLifetime ?: 0.0) +
                                        (g2.downlines?.sumOf { it.totalSetoranLifetime ?: 0.0 } ?: 0.0)
                            } ?: 0.0)
                }

                // Update UI
                binding.tvJumlah.text = "$totalSemua afiliasi"
                binding.tvTotalJaringan.text = totalSemua.toString()
                binding.tvTotalOmset.text = "${String.format(Locale.US, "%,.1f", totalOmset)} Kg"
                binding.tvJumlahG1.text = "G1: $totalG1"
                binding.tvJumlahG2.text = "G2: $totalG2"
                binding.tvJumlahG3.text = "G3: $totalG3"

                binding.progressBar.visibility = View.GONE
                if (hasil.isEmpty()) {
                    binding.tvKosong.visibility = View.VISIBLE
                } else {
                    binding.rvAfiliasi.visibility = View.VISIBLE
                    binding.rvAfiliasi.adapter = DownlineTreeAdapter(hasil)
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}