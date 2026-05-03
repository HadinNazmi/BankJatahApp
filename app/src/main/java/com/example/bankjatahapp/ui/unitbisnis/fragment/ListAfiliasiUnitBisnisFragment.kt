package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentListAfiliasiUnitBisnisBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class ListAfiliasiUnitBisnisFragment : Fragment() {

    private var _binding: FragmentListAfiliasiUnitBisnisBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListAfiliasiUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAfiliasi.layoutManager = LinearLayoutManager(requireContext())

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadAfiliasi()
    }

    private fun loadAfiliasi() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvAfiliasi.visibility = View.GONE
                binding.tvKosong.visibility = View.GONE

                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // Ambil semua nasabah yang id_sponsornya adalah unit bisnis ini
                val listNasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_sponsor", idUser) } }
                    .decodeList<NasabahData>()

                if (listNasabah.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvKosong.visibility = View.VISIBLE
                    binding.tvJumlah.text = "0 afiliasi"
                    return@launch
                }

                // Ambil data user (nama) untuk setiap nasabah
                val idList = listNasabah.map { it.idNasabah }
                val listUser = mutableListOf<User>()
                for (id in idList) {
                    try {
                        val u = client.postgrest
                            .from("users")
                            .select { filter { eq("id_user", id) } }
                            .decodeSingle<User>()
                        listUser.add(u)
                    } catch (_: Exception) {}
                }

                binding.tvJumlah.text = "${listNasabah.size} afiliasi"
                binding.progressBar.visibility = View.GONE
                binding.rvAfiliasi.visibility = View.VISIBLE

                val adapter = AfiliasiUnitBisnisAdapter(listUser, listNasabah)
                binding.rvAfiliasi.adapter = adapter

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}