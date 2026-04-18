package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.MasterWilayah
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentRegistrasiUnitBisnisBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RegistrasiUnitBisnisFragment : Fragment() {

    private var _binding: FragmentRegistrasiUnitBisnisBinding? = null
    private val binding get() = _binding!!

    // List wilayah dari Supabase (pakai model MasterWilayah)
    private val listWilayah = mutableListOf<MasterWilayah>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegistrasiUnitBisnisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadWilayah()
        setupClickListeners()
    }

    // ===== LOAD WILAYAH — pakai decodeList<MasterWilayah>() =====
    private fun loadWilayah() {
        lifecycleScope.launch {
            try {
                val hasil = client.postgrest
                    .from("master_wilayah")
                    .select {
                        filter { eq("status_wilayah", "aktif") }
                    }
                    .decodeList<MasterWilayah>()

                listWilayah.clear()
                listWilayah.addAll(hasil)

                // Isi spinner dengan nama wilayah
                val namaList = listWilayah.map { "${it.kodeWilayah} - ${it.namaWilayah}" }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    namaList
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerWilayah.adapter = adapter

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Gagal memuat wilayah: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnAjukanKemitraan.setOnClickListener {
            validasiDanAjukan()
        }
    }

    // ===== VALIDASI FORM =====
    private fun validasiDanAjukan() {
        val namaUsaha = binding.etNamaUsaha.text.toString().trim()
        val alamat    = binding.etAlamat.text.toString().trim()
        val latStr    = binding.etLatitude.text.toString().trim()
        val lonStr    = binding.etLongitude.text.toString().trim()

        if (namaUsaha.isEmpty()) {
            binding.tilNamaUsaha.error = "Nama usaha tidak boleh kosong"
            return
        }
        binding.tilNamaUsaha.error = null

        if (listWilayah.isEmpty()) {
            Toast.makeText(requireContext(), "Data wilayah belum dimuat", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIndex = binding.spinnerWilayah.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= listWilayah.size) {
            Toast.makeText(requireContext(), "Pilih wilayah operasional", Toast.LENGTH_SHORT).show()
            return
        }

        if (alamat.isEmpty()) {
            binding.tilAlamat.error = "Alamat tidak boleh kosong"
            return
        }
        binding.tilAlamat.error = null

        val lat = latStr.toDoubleOrNull()
        if (lat == null || latStr.isEmpty()) {
            binding.tilLatitude.error = "Latitude tidak valid (contoh: -0.934783)"
            return
        }
        binding.tilLatitude.error = null

        val lon = lonStr.toDoubleOrNull()
        if (lon == null || lonStr.isEmpty()) {
            binding.tilLongitude.error = "Longitude tidak valid (contoh: 100.361533)"
            return
        }
        binding.tilLongitude.error = null

        val wilayahDipilih = listWilayah[selectedIndex]
        ajukanKemitraan(namaUsaha, alamat, lat, lon, wilayahDipilih)
    }

    // ===== SUBMIT KE Supabase =====
    // Insert ke unit_bisnis_data → status_verifikasi_unit = 'menunggu'
    // Role di tabel users TIDAK diubah (tetap 'nasabah' sampai admin setujui)
    private fun ajukanKemitraan(
        namaUsaha: String,
        alamat: String,
        lat: Double,
        lon: Double,
        wilayah: MasterWilayah
    ) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session tidak ditemukan, silakan login ulang")

                // Cek apakah sudah pernah mengajukan
                val existing = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUser) } }
                    .data

                if (existing != "[]" && existing.isNotBlank()) {
                    setLoading(false)
                    Toast.makeText(
                        requireContext(),
                        "Anda sudah pernah mengajukan kemitraan.\nTunggu proses verifikasi dari admin.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Insert ke unit_bisnis_data
                val payload = buildJsonObject {
                    put("id_unit_bisnis",        idUser)
                    put("id_wilayah",            wilayah.idWilayah)
                    put("nama_usaha",            namaUsaha)
                    put("alamat",                alamat)
                    put("lokasi_lat",            lat)
                    put("lokasi_long",           lon)
                    put("status_verifikasi_unit","menunggu")
                    put("tipe_unit",             "kelurahan")
                    put("transaksi_harian",      0)
                }

                client.postgrest.from("unit_bisnis_data").insert(payload)

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "✓ Pengajuan kemitraan berhasil dikirim!\nTim kami akan menghubungi Anda segera.",
                    Toast.LENGTH_LONG
                ).show()

                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("duplicate") == true ||
                            e.message?.contains("unique") == true       -> "Anda sudah pernah mengajukan kemitraan"
                    e.message?.contains("row-level security") == true -> "Akses ditolak. Pastikan Anda sudah login."
                    else -> "Gagal mengajukan: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnAjukanKemitraan.isEnabled = !loading
        binding.btnAjukanKemitraan.text = if (loading) "Mengajukan..." else "Ajukan Kemitraan Final"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}