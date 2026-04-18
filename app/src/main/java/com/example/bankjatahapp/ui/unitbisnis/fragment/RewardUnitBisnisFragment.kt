package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.bankjatahapp.data.model.ProdukReward
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentRewardUnitBisnisBinding
import com.example.bankjatahapp.ui.nasabah.adapter.ProdukRewardAdapter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class RewardUnitBisnisFragment : Fragment() {

    private var _binding: FragmentRewardUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProdukRewardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadData()

        // Tombol back kembali ke fragment sebelumnya
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProdukRewardAdapter(emptyList()) { produk ->
            onTukarKlik(produk)
        }

        binding.rvProdukReward.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter       = this@RewardUnitBisnisFragment.adapter
        }
    }

    private fun loadData() {
        binding.progressBar.visibility    = View.VISIBLE
        binding.layoutEmpty.visibility    = View.GONE
        binding.rvProdukReward.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id

                // 1. Ambil poin dari tabel dompet_user
                if (idUser != null) {
                    try {
                        val dompet = client.postgrest
                            .from("dompet_user")
                            .select { filter { eq("id_dompet", idUser) } }
                            .data

                        val poin = extractPoin(dompet)
                        binding.tvTotalPoin.text = formatAngka(poin)
                    } catch (e: Exception) {
                        binding.tvTotalPoin.text = "0"
                    }
                }

                // 2. Ambil semua produk reward yang aktif
                val produkList = client.postgrest
                    .from("produk_reward")
                    .select {
                        filter { eq("status_produk", "aktif") }
                    }
                    .decodeList<ProdukReward>()

                binding.progressBar.visibility = View.GONE

                if (produkList.isEmpty()) {
                    binding.layoutEmpty.visibility    = View.VISIBLE
                    binding.rvProdukReward.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility    = View.GONE
                    binding.rvProdukReward.visibility = View.VISIBLE
                    adapter.updateData(produkList)

                    val tersedia = produkList.count { it.stok > 0 }
                    binding.tvRewardTersedia.text = tersedia.toString()
                    binding.tvJumlahProduk.text   = "${produkList.size} produk"
                }

            } catch (e: Exception) {
                binding.progressBar.visibility    = View.GONE
                binding.layoutEmpty.visibility    = View.VISIBLE
                binding.rvProdukReward.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Gagal memuat produk: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onTukarKlik(produk: ProdukReward) {
        Toast.makeText(
            requireContext(),
            "Tukar ${produk.namaProduk} dengan ${produk.poinDibutuhkan} poin?",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun extractPoin(jsonStr: String): Int {
        return try {
            val regex = """"poin_reward"\s*:\s*(\d+)""".toRegex()
            val match = regex.find(jsonStr)
            match?.groupValues?.get(1)?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun formatAngka(angka: Int): String {
        return String.format("%,d", angka).replace(',', '.')
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}