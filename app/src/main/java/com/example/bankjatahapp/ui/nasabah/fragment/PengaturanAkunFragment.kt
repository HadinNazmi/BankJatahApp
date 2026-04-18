package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPengaturanAkunBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class PengaturanAkunFragment : Fragment() {

    private var _binding: FragmentPengaturanAkunBinding? = null
    private val binding get() = _binding!!

    private var idUser: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPengaturanAkunBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupClickListeners()
    }

    // ===== LOAD DATA DARI SUPABASE =====
    private fun loadData() {
        lifecycleScope.launch {
            try {
                idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // 1. Ambil dari tabel users
                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                // 2. Ambil dari tabel nasabah_data
                val nasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser!!) } }
                    .decodeSingle<NasabahData>()

                // 3. Isi form dengan data yang ada
                binding.etNama.setText(user.namaLengkap)
                binding.etNoTelp.setText(user.noTelp ?: "")
                binding.etEmail.setText(user.email)
                binding.etNik.setText(nasabah.nik ?: "")
                binding.etAlamat.setText(nasabah.alamatRumah ?: "")

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Gagal memuat data: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupClickListeners() {

        // Tombol back
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Ganti foto profil
        binding.btnGantiFoto.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur ganti foto segera hadir", Toast.LENGTH_SHORT).show()
        }

        // Ajukan menjadi unit bisnis
        binding.btnAjukanUnitBisnis.setOnClickListener {
            tampilkanDialogAjukanUnitBisnis()
        }

        // Simpan perubahan
        binding.btnSimpan.setOnClickListener {
            simpanPerubahan()
        }
    }

    // ===== SIMPAN PERUBAHAN KE SUPABASE =====
    private fun simpanPerubahan() {
        val nama    = binding.etNama.text.toString().trim()
        val noTelp  = binding.etNoTelp.text.toString().trim()
        val nik     = binding.etNik.text.toString().trim()
        val alamat  = binding.etAlamat.text.toString().trim()

        // Validasi
        if (nama.isEmpty()) {
            binding.tilNama.error = "Nama tidak boleh kosong"
            return
        }
        binding.tilNama.error = null

        if (nik.isNotEmpty() && nik.length != 16) {
            binding.tilNik.error = "NIK harus 16 digit"
            return
        }
        binding.tilNik.error = null

        setLoading(true)

        lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Session tidak ditemukan")

                // 1. Update tabel users
                client.postgrest.from("users").update(
                    mapOf(
                        "nama_lengkap" to nama,
                        "no_telp"      to noTelp.ifEmpty { null }
                    )
                ) {
                    filter { eq("id_user", id) }
                }

                // 2. Update tabel nasabah_data
                client.postgrest.from("nasabah_data").update(
                    mapOf(
                        "nik"          to nik.ifEmpty { null },
                        "alamat_rumah" to alamat.ifEmpty { null }
                    )
                ) {
                    filter { eq("id_nasabah", id) }
                }

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "Profil berhasil diperbarui!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("unique") == true &&
                            e.message?.contains("nik") == true ->
                        "NIK sudah digunakan oleh akun lain"
                    else -> "Gagal menyimpan: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===== DIALOG AJUKAN UNIT BISNIS =====
    private fun tampilkanDialogAjukanUnitBisnis() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Ajukan Menjadi Unit Bisnis")
            .setMessage(
                "Dengan mengajukan, Anda menyatakan bersedia menjadi mitra pengumpul minyak jelantah.\n\n" +
                        "Tim kami akan menghubungi Anda melalui nomor telepon yang terdaftar untuk proses verifikasi."
            )
            .setPositiveButton("Ajukan") { _, _ ->
                kirimPengajuanUnitBisnis()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun kirimPengajuanUnitBisnis() {
        // Untuk saat ini hanya Toast — implementasi penuh butuh tabel pengajuan
        // atau perubahan role yang dihandle admin
        Toast.makeText(
            requireContext(),
            "Pengajuan terkirim! Tim kami akan menghubungi Anda segera.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSimpan.isEnabled = !loading
        binding.btnSimpan.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}