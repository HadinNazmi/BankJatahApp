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
import com.example.bankjatahapp.ui.component.AvatarUtils
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

    private fun loadData() {
        setFormLoading(true)
        lifecycleScope.launch {
            try {
                idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                val nasabah = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser!!) } }
                    .decodeSingle<NasabahData>()

                // ===== AVATAR INISIAL dari nama =====
                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)

                // Field yang bisa diubah
                binding.etNama.setText(user.namaLengkap)
                binding.etNoTelp.setText(user.noTelp ?: "")
                binding.etAlamat.setText(nasabah.alamatRumah ?: "")

                // Kode referral sponsor — tampilkan kode bukan UUID
                if (!nasabah.idSponsor.isNullOrEmpty()) {
                    try {
                        val sponsor = client.postgrest
                            .from("nasabah_data")
                            .select { filter { eq("id_nasabah", nasabah.idSponsor!!) } }
                            .decodeSingle<NasabahData>()
                        binding.etKodeReferralSponsor.setText(sponsor.kodeReferral ?: "")
                    } catch (_: Exception) {
                        binding.etKodeReferralSponsor.setText("")
                    }
                }

                // Field read-only
                binding.tvEmailValue.text        = user.email
                binding.tvNikValue.text          = nasabah.nik ?: "-"
                binding.tvKodeReferralValue.text = nasabah.kodeReferral ?: "-"
                binding.tvLevelBintangValue.text = "Bintang ${nasabah.levelBintang ?: 1}"
                binding.tvKategoriValue.text     = (nasabah.kategoriNasabah ?: "pasif")
                    .replaceFirstChar { it.uppercase() }
                binding.tvStatusAkunValue.text   = user.statusAkun.replaceFirstChar { it.uppercase() }
                binding.tvRoleValue.text         = "Nasabah"

                val statusColor = when (user.statusAkun) {
                    "aktif"               -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
                    "dibekukan"           -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
                    "menunggu_verifikasi" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
                    else                  -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
                }
                binding.tvStatusAkunValue.setBackgroundColor(statusColor)

                setFormLoading(false)

            } catch (e: Exception) {
                setFormLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        // ===== HAPUS btnGantiFoto — tidak ada upload foto =====
        // btnGantiFoto dihide dari XML atau tidak dipakai
        binding.btnSimpan.setOnClickListener {
            simpanPerubahan()
        }
    }

    private fun simpanPerubahan() {
        val nama           = binding.etNama.text.toString().trim()
        val noTelp         = binding.etNoTelp.text.toString().trim()
        val alamat         = binding.etAlamat.text.toString().trim()
        val kodeRefSponsor = binding.etKodeReferralSponsor.text.toString().trim()

        if (nama.isEmpty()) {
            binding.tilNama.error = "Nama tidak boleh kosong"
            return
        }
        binding.tilNama.error = null

        setLoading(true)

        lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Session tidak ditemukan")

                // Resolusi kode referral sponsor → UUID
                var idSponsorBaru: String? = null
                if (kodeRefSponsor.isNotEmpty()) {
                    try {
                        val sponsorData = client.postgrest
                            .from("nasabah_data")
                            .select { filter { eq("kode_referral", kodeRefSponsor) } }
                            .decodeSingle<NasabahData>()
                        idSponsorBaru = sponsorData.idNasabah
                    } catch (_: Exception) {
                        setLoading(false)
                        Toast.makeText(requireContext(), "Kode referral sponsor tidak ditemukan", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                // Update tabel users — tanpa url_foto_profil
                client.postgrest.from("users").update(
                    mapOf(
                        "nama_lengkap" to nama,
                        "no_telp"      to noTelp.ifEmpty { null }
                    )
                ) { filter { eq("id_user", id) } }

                // Update tabel nasabah_data
                client.postgrest.from("nasabah_data").update(
                    mapOf(
                        "alamat_rumah" to alamat.ifEmpty { null },
                        "id_sponsor"   to idSponsorBaru
                    )
                ) { filter { eq("id_nasabah", id) } }

                // Update avatar sesuai nama baru
                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, nama, 300)

                setLoading(false)
                Toast.makeText(requireContext(), "✓ Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSimpan.isEnabled = !loading
        binding.btnSimpan.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
    }

    private fun setFormLoading(loading: Boolean) {
        binding.progressBar.visibility   = if (loading) View.VISIBLE else View.GONE
        binding.scrollContent.visibility = if (loading) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}