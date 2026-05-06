package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPengaturanUnitBisnisBinding
import com.example.bankjatahapp.ui.component.AvatarUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PengaturanUnitBisnisFragment : Fragment() {

    private var _binding: FragmentPengaturanUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private var idUser: String? = null

    private var sudahAdaSponsor: Boolean = false

    @Serializable
    data class ReferralResult(
        val id_owner: String,
        val nama_owner: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPengaturanUnitBisnisBinding.inflate(inflater, container, false)
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

                val nasabah = try {
                    client.postgrest
                        .from("nasabah_data")
                        .select { filter { eq("id_nasabah", idUser!!) } }
                        .decodeSingle<NasabahData>()
                } catch (_: Exception) { null }

                val unit = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUser!!) } }
                    .decodeSingle<UnitBisnisData>()

                binding.etNama.setText(user.namaLengkap)
                binding.etNoTelp.setText(user.noTelp ?: "")
                binding.etAlamat.setText(nasabah?.alamatRumah ?: "")

                sudahAdaSponsor = !nasabah?.idSponsor.isNullOrEmpty()
                if (sudahAdaSponsor) {
                    binding.etKodeReferralSponsor.setText("")
                    binding.etKodeReferralSponsor.hint = "Sponsor sudah terdaftar"
                    binding.tilKodeReferralSponsor.isEnabled = false
                    binding.tilKodeReferralSponsor.helperText = "Sponsor tidak dapat diubah"
                } else {
                    binding.etKodeReferralSponsor.setText("")
                    binding.etKodeReferralSponsor.hint = "Contoh: ABC12345"
                    binding.tilKodeReferralSponsor.isEnabled = true
                    binding.tilKodeReferralSponsor.helperText = "Opsional — isi jika Anda memiliki sponsor"
                }

                binding.etNamaUsaha.setText(unit.namaUsaha ?: "")
                binding.etJamBuka.setText(unit.jamBuka ?: "")
                binding.etJamTutup.setText(unit.jamTutup ?: "")
                binding.etHariOperasional.setText(unit.hariOperasional ?: "")

                binding.tvEmailValue.text      = user.email
                binding.tvStatusAkunValue.text = user.statusAkun.replaceFirstChar { it.uppercase() }
                binding.tvRoleValue.text       = "Unit Bisnis"

                binding.tvNikValue.text          = nasabah?.nik ?: "-"
                binding.tvKodeReferralValue.text = nasabah?.kodeReferral ?: "-"
                binding.tvLevelBintangValue.text = "Bintang ${nasabah?.levelBintang ?: 1}"
                binding.tvKategoriValue.text     = (nasabah?.kategoriNasabah ?: "pasif")
                    .replaceFirstChar { it.uppercase() }

                binding.tvTipeUnitValue.text = unit.tipeUnit.replaceFirstChar { it.uppercase() }
                binding.tvStatusVerifikasiValue.text = unit.statusVerifikasiUnit
                    .replace("_", " ").replaceFirstChar { it.uppercase() }
                binding.tvLokasiValue.text = if (unit.lokasiLat != 0.0 && unit.lokasiLong != 0.0) {
                    "${String.format("%.5f", unit.lokasiLat)}, ${String.format("%.5f", unit.lokasiLong)}"
                } else "-"

                val statusAkunColor = when (user.statusAkun) {
                    "aktif"               -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
                    "dibekukan"           -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
                    "menunggu_verifikasi" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
                    else                  -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
                }
                binding.tvStatusAkunValue.setBackgroundColor(statusAkunColor)

                val statusUnitColor = when (unit.statusVerifikasiUnit) {
                    "disetujui" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
                    "ditolak"   -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
                    "nonaktif"  -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
                    else        -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
                }
                binding.tvStatusVerifikasiValue.setBackgroundColor(statusUnitColor)

                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)

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
        binding.btnGantiFoto.setOnClickListener {
            Toast.makeText(requireContext(), "Foto profil menggunakan inisial nama", Toast.LENGTH_SHORT).show()
        }
        binding.btnSimpan.setOnClickListener {
            simpanPerubahan()
        }
        // ===== SHARE KODE REFERRAL =====
        binding.tvBagikanKeTeman.setOnClickListener {
            val kodeReferral = binding.tvKodeReferralValue.text.toString()
            if (kodeReferral == "-" || kodeReferral.isEmpty()) {
                Toast.makeText(requireContext(), "Kode referral belum tersedia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            bagikanKodeReferral(kodeReferral)
        }
    }

    private fun bagikanKodeReferral(kode: String) {
        val link = "bankjatah://register/ref?kode=$kode"
        val pesan = """
            🎉 Hei! Aku mengundang kamu bergabung di aplikasi Bank Jatah!
            
            Gunakan kode referral aku: *$kode*
            
            Langkah daftar:
            1. Install aplikasi Bank Jatah
            2. Buka aplikasi → tap Daftar
            3. Masukkan kode referral: *$kode*
""".trimIndent()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, pesan)
        }
        startActivity(Intent.createChooser(intent, "Bagikan kode referral via"))
    }

    private fun simpanPerubahan() {
        val nama            = binding.etNama.text.toString().trim()
        val noTelp          = binding.etNoTelp.text.toString().trim()
        val alamat          = binding.etAlamat.text.toString().trim()
        val kodeRefSponsor  = binding.etKodeReferralSponsor.text.toString().trim()
        val namaUsaha       = binding.etNamaUsaha.text.toString().trim()
        val jamBuka         = binding.etJamBuka.text.toString().trim()
        val jamTutup        = binding.etJamTutup.text.toString().trim()
        val hariOperasional = binding.etHariOperasional.text.toString().trim()

        if (nama.isEmpty()) {
            binding.tilNama.error = "Nama tidak boleh kosong"
            return
        }
        binding.tilNama.error = null

        setLoading(true)

        lifecycleScope.launch {
            try {
                val id = idUser ?: throw Exception("Session tidak ditemukan")

                var idSponsorBaru: String? = null
                if (!sudahAdaSponsor && kodeRefSponsor.isNotEmpty()) {
                    try {
                        val payload = buildJsonObject {
                            put("target_code", kodeRefSponsor)
                        }

                        val hasil = client.postgrest
                            .rpc("check_referral_code", payload)
                            .decodeList<ReferralResult>()

                        if (hasil.isEmpty()) {
                            setLoading(false)
                            Toast.makeText(
                                requireContext(),
                                "⚠ Kode referral \"$kodeRefSponsor\" tidak ditemukan.\nPastikan kode benar.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }

                        idSponsorBaru = hasil[0].id_owner

                        if (idSponsorBaru == id) {
                            setLoading(false)
                            Toast.makeText(
                                requireContext(),
                                "Anda tidak bisa menjadi sponsor diri sendiri",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                    } catch (e: Exception) {
                        setLoading(false)
                        Toast.makeText(
                            requireContext(),
                            "Gagal memvalidasi kode referral: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }

                client.postgrest.from("users").update(
                    mapOf(
                        "nama_lengkap" to nama,
                        "no_telp"      to noTelp.ifEmpty { null }
                    )
                ) { filter { eq("id_user", id) } }

                if (!sudahAdaSponsor && idSponsorBaru != null) {
                    client.postgrest.from("nasabah_data").update(
                        mapOf(
                            "alamat_rumah" to alamat.ifEmpty { null },
                            "id_sponsor"   to idSponsorBaru
                        )
                    ) { filter { eq("id_nasabah", id) } }
                } else {
                    client.postgrest.from("nasabah_data").update(
                        mapOf(
                            "alamat_rumah" to alamat.ifEmpty { null }
                        )
                    ) { filter { eq("id_nasabah", id) } }
                }

                client.postgrest.from("unit_bisnis_data").update(
                    mapOf(
                        "nama_usaha"       to namaUsaha.ifEmpty { null },
                        "jam_buka"         to jamBuka.ifEmpty { null },
                        "jam_tutup"        to jamTutup.ifEmpty { null },
                        "hari_operasional" to hariOperasional.ifEmpty { null }
                    )
                ) { filter { eq("id_unit_bisnis", id) } }

                AvatarUtils.pasangKeImageView(binding.ivFotoProfil, nama, 300)

                setLoading(false)
                Toast.makeText(
                    requireContext(),
                    "✓ Profil berhasil diperbarui!",
                    Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                setLoading(false)
                val pesan = when {
                    e.message?.contains("unique") == true &&
                            e.message?.contains("no_telp") == true ->
                        "Nomor telepon sudah digunakan akun lain"
                    else -> "Gagal menyimpan: ${e.message}"
                }
                Toast.makeText(requireContext(), pesan, Toast.LENGTH_LONG).show()
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