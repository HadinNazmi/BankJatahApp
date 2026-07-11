package com.example.bankjatahapp.ui.nasabah.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bankjatahapp.data.model.UnitBisnisData
import com.example.bankjatahapp.data.model.User
import com.example.bankjatahapp.data.remote.SupabaseClient.client
import com.example.bankjatahapp.databinding.FragmentLokasiUnitBisnisBinding
import com.example.bankjatahapp.databinding.FragmentLokasiUnitBisnisUBBinding
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.math.*

class LokasiUnitBisnisUBFragment : Fragment() {

    private var _binding: FragmentLokasiUnitBisnisUBBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLokasiUnitBisnisUBBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvUnitBisnis.layoutManager = LinearLayoutManager(requireContext())
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadUnitBisnis()
    }

    private fun loadUnitBisnis() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility  = View.VISIBLE
                binding.rvUnitBisnis.visibility = View.GONE
                binding.tvKosong.visibility     = View.GONE

                val listUnit = client.postgrest
                    .from("unit_bisnis_data")
                    .select()
                    .decodeList<UnitBisnisData>()

                val listWithNama = listUnit.map { unit ->
                    val namaDisplay = if (!unit.namaUsaha.isNullOrEmpty()) {
                        unit.namaUsaha!!
                    } else {
                        try {
                            val user = client.postgrest
                                .from("users")
                                .select { filter { eq("id_user", unit.idUnitBisnis) } }
                                .decodeSingle<User>()
                            user.namaLengkap
                        } catch (_: Exception) { "Unit Bisnis" }
                    }
                    unit to namaDisplay
                }

                // Ambil lokasi user
                val lokasiUser = ambilLokasiUser()

                // Hitung jarak dan sort
                val listDenganJarak = listWithNama.map { (unit, nama) ->
                    val jarak = if (lokasiUser != null &&
                        unit.lokasiLat != 0.0 && unit.lokasiLong != 0.0) {
                        hitungJarakKm(lokasiUser.first, lokasiUser.second,
                            unit.lokasiLat, unit.lokasiLong)
                    } else null
                    Triple(unit, nama, jarak)
                }.sortedWith(compareBy(nullsLast()) { it.third })

                binding.progressBar.visibility = View.GONE
                binding.tvJumlah.text = "${listUnit.size} unit bisnis tersedia"

                if (listUnit.isEmpty()) {
                    binding.tvKosong.visibility = View.VISIBLE
                } else {
                    binding.rvUnitBisnis.visibility = View.VISIBLE
                    binding.rvUnitBisnis.adapter = LokasiUnitBisnisAdapter(listDenganJarak)
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun ambilLokasiUser(): Pair<Double, Double>? {
        val izinOk = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (!izinOk) return null

        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                LocationServices.getFusedLocationProviderClient(requireActivity())
                    .lastLocation
                    .addOnSuccessListener { loc ->
                        cont.resume(if (loc != null) Pair(loc.latitude, loc.longitude) else null) {}
                    }
                    .addOnFailureListener { cont.resume(null) {} }
            }
        } catch (_: Exception) { null }
    }

    private fun hitungJarakKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}