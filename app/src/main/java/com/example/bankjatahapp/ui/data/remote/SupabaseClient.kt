package com.example.bankjatahapp.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.logging.LogLevel

object SupabaseClient {

    private const val SUPABASE_URL = "https://elhcrpyghhxupixlqdby.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVsaGNycHlnaGh4dXBpeGxxZGJ5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQ4NTQ1ODEsImV4cCI6MjA4MDQzMDU4MX0.gBo93xU2AlRvKydCru6m3Fnicw5VZuZTIJkSaAzoK3M"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        defaultLogLevel = LogLevel.NONE

        install(Auth) {
            // Simpan session ke storage permanen (SharedPreferences)
            // sehingga user tidak perlu login ulang walau app di-kill
            autoSaveToStorage   = true
            autoLoadFromStorage = true
            alwaysAutoRefresh   = true
            flowType            = FlowType.PKCE
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}