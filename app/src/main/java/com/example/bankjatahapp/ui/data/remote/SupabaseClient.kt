package com.example.bankjatahapp.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {

    private const val SUPABASE_URL = "https://elhcrpyghhxupixlqdby.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVsaGNycHlnaGh4dXBpeGxxZGJ5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQ4NTQ1ODEsImV4cCI6MjA4MDQzMDU4MX0.gBo93xU2AlRvKydCru6m3Fnicw5VZuZTIJkSaAzoK3M"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}