package com.example.bankjatahapp.ui.auth

import android.content.Context
import android.content.Intent
import com.example.bankjatahapp.data.model.NasabahData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

object DataChecker {

    suspend fun cekDanArahkanJikaDataKurang(
        context: Context,
        onLengkap: () -> Unit
    ) {
        try {
            val userId = client.auth.currentUserOrNull()?.id ?: run {
                // Tidak ada session — ke login
                val intent = Intent(context, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
                return
            }

            val user = client.postgrest
                .from("users")
                .select { filter { eq("id_user", userId) } }
                .decodeSingle<User>()

            val nasabah = try {
                client.postgrest
                    .from("nasabah_data")
                    .select { filter { eq("id_nasabah", userId) } }
                    .decodeSingle<NasabahData>()
            } catch (_: Exception) { null }

            val sudahAdaNoTelp = !user.noTelp.isNullOrEmpty()
            val sudahAdaNik    = !nasabah?.nik.isNullOrEmpty()
            val sudahAdaEmail  = user.email.isNotEmpty()
                    && !user.email.contains("@bankjatah.local")
            val dataLengkap    = sudahAdaNoTelp && sudahAdaNik && sudahAdaEmail

            if (!dataLengkap) {
                val intent = Intent(context, LengkapiDataActivity::class.java).apply {
                    putExtra("id_user",          userId)
                    putExtra("role_user",         user.role)
                    putExtra("sudah_ada_no_telp", sudahAdaNoTelp)
                    putExtra("sudah_ada_nik",     sudahAdaNik)
                    putExtra("sudah_ada_email",   sudahAdaEmail)
                    putExtra("nilai_no_telp",     user.noTelp ?: "")
                    putExtra("nilai_nik",         nasabah?.nik ?: "")
                    putExtra("nilai_email",       user.email)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
            } else {
                onLengkap()
            }

        } catch (_: Exception) {
            // Gagal cek — biarkan lanjut ke home, jangan block user
            onLengkap()
        }
    }
}