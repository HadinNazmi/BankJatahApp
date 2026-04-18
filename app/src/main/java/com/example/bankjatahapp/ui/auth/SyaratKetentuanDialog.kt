package com.example.bankjatahapp.ui.auth

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.bankjatahapp.R

/**
 * Dialog pop-up Syarat & Ketentuan (Draf Akad Tabungan Afiliasi Tabungan Jelantah).
 *
 * Penggunaan di Login  : SyaratKetentuanDialog.tampilkan(context, modeRegister = false)
 * Penggunaan di Register: SyaratKetentuanDialog.tampilkan(context, modeRegister = true) { disetujui -> ... }
 */
object SyaratKetentuanDialog {

    private val ISI_AKAD = """
DRAF AKAD TABUNGAN AFILIASI TABUNGAN JELANTAH
BANK JATAH INDONESIA

─────────────────────────────────────────────

Pasal 1 – Objek Akad

1. Pihak Kedua menitipkan minyak jelantah (tabungan jelantah) kepada Pihak Pertama dengan ketentuan jumlah, berat, dan nilai mengikuti standar harga pasar yang berlaku saat transaksi.
2. Akad ini menggunakan prinsip Wadi'ah Yad Dhamanah (titipan dengan jaminan pengembalian setara nilai).

─────────────────────────────────────────────

Pasal 2 – Hak dan Kewajiban Pihak Pertama

1. Menjaga, mengelola, dan menjamin pengembalian nilai tabungan minyak jelantah kepada Pihak Kedua saat penarikan.
2. Memberikan bonus/hibah kepada Pihak Kedua sesuai ketentuan program afiliasi, tanpa kewajiban mengikat.
3. Bonus bersumber dari keuntungan usaha Bank Jatah, bukan dari pengurangan tabungan Pihak Kedua.

─────────────────────────────────────────────

Pasal 3 – Hak dan Kewajiban Pihak Kedua

1. Menyetorkan minyak jelantah sesuai ketentuan minimal tabungan.
2. Berhak menarik tabungan setelah memenuhi syarat dan prosedur yang berlaku.
3. Berhak menerima bonus sponsor, bonus repeat order, dan reward poin apabila memenuhi syarat keaktifan (minimal tabungan 10 kg yang tidak ditarik selama 3 bulan).
4. Tidak menuntut bonus apabila Bank Jatah tidak membagikan hibah pada periode tertentu.

─────────────────────────────────────────────

Pasal 4 – Skema Bonus Afiliasi

1. Bonus Sponsor: Rp500/kg untuk setiap ajakan pertama menabung.
2. Bonus Repeat Order: Rp500/kg setiap kali anggota binaan menabung ulang, berlaku hingga 3 (tiga) jenjang ke bawah.
3. Reward Poin:
   • Setiap 15 kg tabungan = 1 poin.
   • Poin dapat ditukar dengan hadiah sesuai tabel reward (tour, smartphone, motor, mobil, rumah).
   • Poin hanya berlaku jika nasabah aktif menabung (≥10 kg dan tidak ditarik 3 bulan terakhir).

─────────────────────────────────────────────

Pasal 5 – Ketentuan Tambahan

1. Program bintang (Bintang 1 s/d Bintang 8) adalah jenjang prestasi untuk memotivasi nasabah.
2. Reward poin bersifat hibah dan dapat berubah sesuai kebijakan Pihak Pertama.
3. Segala bentuk bonus tidak dijanjikan di awal sebagai keuntungan tetap, melainkan diberikan sebagai fasilitas tambahan.

─────────────────────────────────────────────

Pasal 6 – Penutup

1. Akad ini disusun berdasarkan prinsip syariah Islam dan diawasi oleh Dewan Pengawas Syariah (DPS).
2. Apabila di kemudian hari terdapat perselisihan, diselesaikan dengan musyawarah. Jika tidak tercapai, maka diselesaikan melalui Badan Arbitrase Syariah Nasional (BASYARNAS).

─────────────────────────────────────────────

Pihak Pertama  : Bank Jatah Indonesia
Pihak Kedua    : Nasabah
    """.trimIndent()

    /**
     * @param modeRegister  true  → tampilkan checkbox "Saya Setuju" + tombol Setuju & Batal
     *                      false → hanya tampilkan konten + tombol Tutup
     * @param onSetuju      callback saat user klik Setuju (hanya relevan di modeRegister = true)
     */
    fun tampilkan(
        context: Context,
        modeRegister: Boolean = false,
        onSetuju: ((disetujui: Boolean) -> Unit)? = null
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // ===== Root Layout =====
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            // Background putih solid dengan sudut membulat — tidak bening
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = (16 * context.resources.displayMetrics.density)
                setColor(android.graphics.Color.WHITE)
            }
            background = bgDrawable

            val dp20 = (20 * context.resources.displayMetrics.density).toInt()
            val dp24 = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(dp24, dp24, dp24, dp20)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val dp = context.resources.displayMetrics.density

        // Judul
        val tvJudul = TextView(context).apply {
            text = "Syarat & Ketentuan"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(context.getColor(R.color.black))
            gravity = android.view.Gravity.CENTER
            val mb = (4 * dp).toInt()
            setPadding(0, 0, 0, mb)
        }
        root.addView(tvJudul)

        // Subtitle
        val tvSub = TextView(context).apply {
            text = "Draf Akad Tabungan Afiliasi Tabungan Jelantah Bank Jatah Indonesia"
            textSize = 11f
            setTextColor(context.getColor(R.color.gray_text))
            gravity = android.view.Gravity.CENTER
            val mb = (12 * dp).toInt()
            setPadding(0, 0, 0, mb)
        }
        root.addView(tvSub)

        // Garis pemisah
        val divider = android.view.View(context).apply {
            setBackgroundColor(context.getColor(R.color.gray_border))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                val mb = (12 * dp).toInt()
                setMargins(0, 0, 0, mb)
            }
        }
        root.addView(divider)

        // ScrollView isi akad
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (320 * dp).toInt()
            )
        }

        val tvIsi = TextView(context).apply {
            text = ISI_AKAD
            textSize = 12f
            setTextColor(context.getColor(R.color.black))
            setLineSpacing(0f, 1.5f)
            val pd = (4 * dp).toInt()
            setPadding(pd, pd, pd, pd)
        }
        scrollView.addView(tvIsi)
        root.addView(scrollView)

        // Garis pemisah bawah
        val divider2 = android.view.View(context).apply {
            setBackgroundColor(context.getColor(R.color.gray_border))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                val mv = (12 * dp).toInt()
                setMargins(0, mv, 0, mv)
            }
        }
        root.addView(divider2)

        if (modeRegister) {
            // ===== MODE REGISTER: Checkbox + Setuju + Batal =====
            val cbSetuju = CheckBox(context).apply {
                text = "Saya telah membaca dan menyetujui seluruh isi akad di atas"
                textSize = 12f
                setTextColor(context.getColor(R.color.black))
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.orange_primary)
                )
                val mb = (12 * dp).toInt()
                setPadding(0, 0, 0, mb)
            }
            root.addView(cbSetuju)

            val rowButtons = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val btnBatal = Button(context).apply {
                text = "Batal"
                textSize = 13f
                setTextColor(context.getColor(R.color.black))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.gray_border)
                )
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    val mr = (8 * dp).toInt()
                    setMargins(0, 0, mr, 0)
                }
                setOnClickListener {
                    onSetuju?.invoke(false)
                    dialog.dismiss()
                }
            }

            val btnSetuju = Button(context).apply {
                text = "Setuju"
                textSize = 13f
                setTextColor(android.graphics.Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.orange_primary)
                )
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                isEnabled = false
                alpha = 0.5f

                setOnClickListener {
                    onSetuju?.invoke(true)
                    dialog.dismiss()
                }
            }

            cbSetuju.setOnCheckedChangeListener { _, isChecked ->
                btnSetuju.isEnabled = isChecked
                btnSetuju.alpha = if (isChecked) 1.0f else 0.5f
            }

            rowButtons.addView(btnBatal)
            rowButtons.addView(btnSetuju)
            root.addView(rowButtons)

        } else {
            // ===== MODE LOGIN: Hanya tombol Tutup =====
            val btnTutup = Button(context).apply {
                text = "Tutup"
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.orange_primary)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { dialog.dismiss() }
            }
            root.addView(btnTutup)
        }

        dialog.setContentView(root)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
}