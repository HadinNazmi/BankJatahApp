package com.example.bankjatahapp.ui.unitbisnis.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentPengaturanUnitBisnisBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.InputStream

class PengaturanUnitBisnisFragment : Fragment() {

    private var _binding: FragmentPengaturanUnitBisnisBinding? = null
    private val binding get() = _binding!!

    private var idUser: String? = null
    private var fotoBaru: Uri? = null
    private var urlFotoLama: String? = null

    // Sudah ada sponsor atau belum — kalau sudah, field referral di-disable
    private var sudahAdaSponsor: Boolean = false

    // Model decode hasil RPC check_referral_code
    // RPC: check_referral_code(target_code TEXT) → TABLE(id_owner UUID, nama_owner TEXT)
    @Serializable
    data class ReferralResult(
        val id_owner: String,
        val nama_owner: String
    )

    // Launcher galeri untuk pilih foto profil
    private val pilihFotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            fotoBaru = uri
            binding.ivFotoProfil.setImageURI(uri)
        }
    }

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

    // ===== LOAD DATA DARI 3 TABEL =====
    private fun loadData() {
        setFormLoading(true)
        lifecycleScope.launch {
            try {
                idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                // 1. Tabel users
                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser!!) } }
                    .decodeSingle<User>()

                // 2. Tabel nasabah_data (unit bisnis juga punya ini)
                val nasabah = try {
                    client.postgrest
                        .from("nasabah_data")
                        .select { filter { eq("id_nasabah", idUser!!) } }
                        .decodeSingle<NasabahData>()
                } catch (_: Exception) { null }

                // 3. Tabel unit_bisnis_data
                val unit = client.postgrest
                    .from("unit_bisnis_data")
                    .select { filter { eq("id_unit_bisnis", idUser!!) } }
                    .decodeSingle<UnitBisnisData>()

                urlFotoLama = user.urlFotoProfil

                // ===== ISI FIELD YANG BISA DIUBAH =====

                // Dari tabel users
                binding.etNama.setText(user.namaLengkap)
                binding.etNoTelp.setText(user.noTelp ?: "")

                // Dari tabel nasabah_data
                binding.etAlamat.setText(nasabah?.alamatRumah ?: "")

                // Field kode referral sponsor
                // Jika sudah ada sponsor → disable (tidak boleh diubah lagi)
                // Jika belum ada → enable, user bisa isi
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

                // Dari tabel unit_bisnis_data — yang bisa diubah
                binding.etNamaUsaha.setText(unit.namaUsaha ?: "")
                binding.etJamBuka.setText(unit.jamBuka ?: "")
                binding.etJamTutup.setText(unit.jamTutup ?: "")
                binding.etHariOperasional.setText(unit.hariOperasional ?: "")

                // ===== ISI FIELD READ-ONLY =====

                // Dari users
                binding.tvEmailValue.text      = user.email
                binding.tvStatusAkunValue.text = user.statusAkun.replaceFirstChar { it.uppercase() }
                binding.tvRoleValue.text       = "Unit Bisnis"

                // Dari nasabah_data
                binding.tvNikValue.text          = nasabah?.nik ?: "-"
                binding.tvKodeReferralValue.text = nasabah?.kodeReferral ?: "-"
                binding.tvLevelBintangValue.text = "Bintang ${nasabah?.levelBintang ?: 1}"
                binding.tvKategoriValue.text     = (nasabah?.kategoriNasabah ?: "pasif")
                    .replaceFirstChar { it.uppercase() }

                // Dari unit_bisnis_data — read only
                binding.tvTipeUnitValue.text = unit.tipeUnit.replaceFirstChar { it.uppercase() }
                binding.tvStatusVerifikasiValue.text = unit.statusVerifikasiUnit
                    .replace("_", " ").replaceFirstChar { it.uppercase() }
                binding.tvLokasiValue.text = if (unit.lokasiLat != 0.0 && unit.lokasiLong != 0.0) {
                    "${String.format("%.5f", unit.lokasiLat)}, ${String.format("%.5f", unit.lokasiLong)}"
                } else "-"

                // Warna badge status akun
                val statusAkunColor = when (user.statusAkun) {
                    "aktif"               -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
                    "dibekukan"           -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
                    "menunggu_verifikasi" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
                    else                  -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
                }
                binding.tvStatusAkunValue.setBackgroundColor(statusAkunColor)

                // Warna badge status verifikasi unit bisnis
                val statusUnitColor = when (unit.statusVerifikasiUnit) {
                    "disetujui" -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_success_bg)
                    "ditolak"   -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_error_bg)
                    "nonaktif"  -> requireContext().getColor(com.example.bankjatahapp.R.color.gray_border)
                    else        -> requireContext().getColor(com.example.bankjatahapp.R.color.notif_warning_bg)
                }
                binding.tvStatusVerifikasiValue.setBackgroundColor(statusUnitColor)

                // Load foto profil
                if (!urlFotoLama.isNullOrEmpty()) {
                    try {
                        com.bumptech.glide.Glide.with(this@PengaturanUnitBisnisFragment)
                            .load(urlFotoLama)
                            .circleCrop()
                            .placeholder(android.R.drawable.ic_menu_myplaces)
                            .into(binding.ivFotoProfil)
                    } catch (_: Exception) { }
                }

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
            pilihFotoLauncher.launch("image/*")
        }
        binding.btnSimpan.setOnClickListener {
            simpanPerubahan()
        }
    }

    // ===== SIMPAN KE 3 TABEL =====
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

                // ===== UPLOAD FOTO BARU JIKA ADA =====
                var urlFotoBaru: String? = urlFotoLama
                val fotoUri = fotoBaru
                if (fotoUri != null) {
                    try {
                        val inputStream: InputStream = requireContext().contentResolver
                            .openInputStream(fotoUri)
                            ?: throw Exception("Gagal membuka foto")
                        val bytes = inputStream.readBytes()
                        inputStream.close()

                        val namaFile = "profil_$id.jpg"
                        // Gunakan operator [] — cara yang benar untuk Supabase SDK v3
                        client.storage["foto-profil"].upload(namaFile, bytes) {
                            upsert = true
                        }
                        urlFotoBaru = client.storage["foto-profil"].publicUrl(namaFile)
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Foto gagal diupload: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // ===== RESOLUSI KODE REFERRAL SPONSOR via RPC =====
                // Pakai check_referral_code (SECURITY DEFINER) — bypass RLS
                // Sama persis seperti cara RegisterActivity
                // Hanya diproses jika field aktif (belum ada sponsor) dan diisi
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
                            // Kode referral tidak ditemukan
                            setLoading(false)
                            Toast.makeText(
                                requireContext(),
                                "⚠ Kode referral \"$kodeRefSponsor\" tidak ditemukan.\nPastikan kode benar.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }

                        // Ditemukan — ambil UUID pemilik kode
                        idSponsorBaru = hasil[0].id_owner

                        // Pastikan tidak memasukkan kode referral diri sendiri
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

                // ===== 1. UPDATE TABEL users =====
                client.postgrest.from("users").update(
                    mapOf(
                        "nama_lengkap"    to nama,
                        "no_telp"         to noTelp.ifEmpty { null },
                        "url_foto_profil" to urlFotoBaru
                    )
                ) { filter { eq("id_user", id) } }

                // ===== 2. UPDATE TABEL nasabah_data =====
                // id_sponsor hanya diupdate jika belum ada sponsor dan kode valid
                if (!sudahAdaSponsor && idSponsorBaru != null) {
                    client.postgrest.from("nasabah_data").update(
                        mapOf(
                            "alamat_rumah" to alamat.ifEmpty { null },
                            "id_sponsor"   to idSponsorBaru
                        )
                    ) { filter { eq("id_nasabah", id) } }
                } else {
                    // Update tanpa ubah id_sponsor
                    client.postgrest.from("nasabah_data").update(
                        mapOf(
                            "alamat_rumah" to alamat.ifEmpty { null }
                        )
                    ) { filter { eq("id_nasabah", id) } }
                }

                // ===== 3. UPDATE TABEL unit_bisnis_data =====
                client.postgrest.from("unit_bisnis_data").update(
                    mapOf(
                        "nama_usaha"       to namaUsaha.ifEmpty { null },
                        "jam_buka"         to jamBuka.ifEmpty { null },
                        "jam_tutup"        to jamTutup.ifEmpty { null },
                        "hari_operasional" to hariOperasional.ifEmpty { null }
                    )
                ) { filter { eq("id_unit_bisnis", id) } }

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