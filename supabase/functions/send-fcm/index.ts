import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

async function getAccessToken(serviceAccount: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000)

  const header = { alg: "RS256", typ: "JWT" }
  const payload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now
  }

  const encode = (obj: any) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '')

  const headerB64  = encode(header)
  const payloadB64 = encode(payload)
  const signingInput = `${headerB64}.${payloadB64}`

  const pemKey = serviceAccount.private_key
  const pemBody = pemKey
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '')

  const binaryKey = Uint8Array.from(atob(pemBody), (c: string) => c.charCodeAt(0))

  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    binaryKey,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  )

  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    new TextEncoder().encode(signingInput)
  )

  const signatureB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')

  const jwt = `${signingInput}.${signatureB64}`

  const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  })

  const tokenData = await tokenResponse.json()
  console.log("Token response:", JSON.stringify(tokenData))
  return tokenData.access_token
}

serve(async (req) => {
  try {
    const { id_user, judul, pesan } = await req.json()
    console.log("Request diterima untuk id_user:", id_user)

    // Ambil service account
    const serviceAccountStr = Deno.env.get("FIREBASE_SERVICE_ACCOUNT")
    if (!serviceAccountStr) {
      console.error("FIREBASE_SERVICE_ACCOUNT tidak ditemukan")
      return new Response("Service account tidak ditemukan", { status: 500 })
    }
    const serviceAccount = JSON.parse(serviceAccountStr)
    const projectId = serviceAccount.project_id
    console.log("Project ID:", projectId)

    // Ambil fcm_token dari Supabase
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    )

    const { data: user, error } = await supabase
      .from("users")
      .select("fcm_token")
      .eq("id_user", id_user)
      .single()

    if (error) {
      console.error("Error ambil user:", error.message)
      return new Response("Error ambil user: " + error.message, { status: 500 })
    }

    if (!user?.fcm_token) {
      console.error("FCM token tidak ditemukan untuk user:", id_user)
      return new Response("Token tidak ditemukan", { status: 400 })
    }

    console.log("FCM token ditemukan:", user.fcm_token.substring(0, 20) + "...")

    // Dapatkan access token
    const accessToken = await getAccessToken(serviceAccount)
    if (!accessToken) {
      console.error("Gagal mendapatkan access token")
      return new Response("Gagal mendapatkan access token", { status: 500 })
    }
    console.log("Access token berhasil didapat")

    // Kirim ke FCM V1 API
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`
    console.log("Mengirim ke FCM URL:", fcmUrl)

    const fcmBody = JSON.stringify({
      message: {
        token: user.fcm_token,
        notification: {
          title: judul,
          body: pesan
        },
        android: {
          priority: "high",
          notification: {
            sound: "notif_bankjatah",
            channel_id: "bankjatah_channel_v2"
          }
        }
      }
    })

    const fcmResponse = await fetch(fcmUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: fcmBody
    })

    console.log("FCM Response status:", fcmResponse.status)
    const responseText = await fcmResponse.text()
    console.log("FCM Response body:", responseText)

    if (!fcmResponse.ok) {
      return new Response(responseText, { status: fcmResponse.status })
    }

    return new Response(responseText, { status: 200 })

  } catch (e) {
    console.error("Error:", e.message)
    return new Response(`Error: ${e.message}`, { status: 500 })
  }
})