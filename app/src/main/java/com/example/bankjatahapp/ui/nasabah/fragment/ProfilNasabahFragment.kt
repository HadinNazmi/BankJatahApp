package com.example.bankjatahapp.ui.nasabah.fragment

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bankjatahapp.R
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentProfilNasabahBinding
import com.example.bankjatahapp.ui.auth.LoginActivity
import com.example.bankjatahapp.ui.component.AvatarUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class ProfilNasabahFragment : Fragment() {

    private var _binding: FragmentProfilNasabahBinding? = null
    private val binding get() = _binding!!

    private var idNasabah: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilNasabahBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        setupClickListeners()
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val idUser = client.auth.currentUserOrNull()?.id ?: return@launch

                val user = client.postgrest
                    .from("users")
                    .select { filter { eq("id_user", idUser) } }
                    .decodeSingle<User>()

                val nasabahData = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", idUser) } }
                    .decodeSingle<NasabahData>()

                val dompet = client.postgrest
                    .from("dompet_user")
                    .select { filter { eq("id_dompet", idUser) } }
                    .decodeSingle<com.example.bankjatahapp.data.model.DompetUser>()

                // Hitung jumlah afiliasi (nasabah yang id_sponsornya adalah user ini)
                val listAfiliasi = client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_sponsor", idUser) } }
                    .decodeList<NasabahData>()

                // Simpan UUID PENUH untuk QR
                idNasabah = nasabahData.idNasabah

                tampilkanData(user, nasabahData, dompet, listAfiliasi.size)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun tampilkanData(
        user: User,
        nasabah: NasabahData,
        dompet: com.example.bankjatahapp.data.model.DompetUser,
        jumlahAfiliasi: Int
    ) {
        binding.tvNama.text = user.namaLengkap
        binding.tvRole.text = "Nasabah"

        // ===== AVATAR INISIAL — tidak pakai storage/foto =====
        AvatarUtils.pasangKeImageView(binding.ivFotoProfil, user.namaLengkap, 300)
        binding.tvJumlahAfiliasi.text = "$jumlahAfiliasi orang"
    }

    // QR berisi UUID PENUH
    private fun generateQrBitmap(uuid: String): Bitmap {
        val size = 512
        val writer = QRCodeWriter()
        val matrix = writer.encode(uuid, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun tampilkanDialogQr() {
        val id = idNasabah
        if (id == null) {
            Toast.makeText(requireContext(), "Data belum dimuat", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_qr_nasabah)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivQr = dialog.findViewById<ImageView>(R.id.ivQrCode)
        val tvKodeId = dialog.findViewById<TextView>(R.id.tvKodeId)
        val tvNamaQr = dialog.findViewById<TextView>(R.id.tvNamaQr)
        val btnTutup = dialog.findViewById<TextView>(R.id.btnTutupQr)

        ivQr.setImageBitmap(generateQrBitmap(id))
        tvKodeId.text = "ID: ${id.take(8)}..."
        tvNamaQr.text = binding.tvNama.text

        btnTutup.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun tampilkanKonfirmasiLogout() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Logout")
            .setMessage("Apakah Anda yakin ingin keluar dari akun ini?")
            .setPositiveButton("Ya, Logout") { _, _ ->
                lifecycleScope.launch {
                    try { client.auth.signOut() } catch (_: Exception) {}
                }
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(requireContext().getColor(R.color.orange_primary))
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(requireContext().getColor(R.color.gray_text))
    }



    private fun setupClickListeners() {

        binding.btnTampilkanQr.setOnClickListener {
            tampilkanDialogQr()
        }

        // Tombol card List Afiliasi
        binding.cardListAfiliasi.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ListAfiliasiFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }

        binding.menuPengaturan.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PengaturanAkunFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }

        binding.menuNotifikasi.setOnClickListener {
            bukaFormAjukanBerhenti()
        }

        binding.menuBantuan.setOnClickListener {
            Toast.makeText(requireContext(), "Bantuan & FAQ", Toast.LENGTH_SHORT).show()
        }


        binding.menuLogout.setOnClickListener {
            tampilkanKonfirmasiLogout()
        }


    }

    private fun bukaFormAjukanBerhenti() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AjukanBerhentiNasabahFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}