package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentLokasiUnitBisnisBinding
import com.example.bankjatahapp.databinding.FragmentLokasiUnitBisnisUBBinding
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class LokasiUnitBisnisUBFragment : Fragment() {

    private var _binding: FragmentLokasiUnitBisnisUBBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLokasiUnitBisnisUBBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvUnitBisnis.layoutManager = LinearLayoutManager(requireContext())
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadUnitBisnis()
    }

    private fun loadUnitBisnis() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility  = View.VISIBLE
                binding.rvUnitBisnis.visibility = View.GONE
                binding.tvKosong.visibility     = View.GONE

                // Ambil semua UB yang sudah disetujui
                // SEMENTARA hapus filter untuk test apakah data masuk
                val listUnit = client.postgrest
                    .from("unit_bisnis_data")
                    .select()  // ← tanpa filter dulu
                    .decodeList<UnitBisnisData>()

                // Untuk setiap UB, ambil nama dari nama_usaha atau fallback ke nama user
                val listWithNama = listUnit.map { unit ->
                    val namaDisplay = if (!unit.namaUsaha.isNullOrEmpty()) {
                        unit.namaUsaha!!
                    } else {
                        try {
                            val user = client.postgrest
                                .from("users")
                                .select { filter { eq("id_user", unit.idUnitBisnis) } }
                                .decodeSingle<User>()
                            user.namaLengkap
                        } catch (_: Exception) {
                            "Unit Bisnis"
                        }
                    }
                    unit to namaDisplay
                }

                binding.progressBar.visibility = View.GONE
                binding.tvJumlah.text = "${listUnit.size} unit bisnis tersedia"

                if (listUnit.isEmpty()) {
                    binding.tvKosong.visibility = View.VISIBLE
                } else {
                    binding.rvUnitBisnis.visibility = View.VISIBLE
                    binding.rvUnitBisnis.adapter = LokasiUnitBisnisAdapter(listWithNama)
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Gagal memuat: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}