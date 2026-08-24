package com.example.bankjatahapp.ui.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.example.bankjatahapp.databinding.DialogRequestPermissionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PermissionDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogRequestPermissionBinding? = null
    private val binding get() = _binding!!

    // Pindahkan inisialisasi ke level atas kelas agar lifecycle-nya valid
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val lokasiGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val kameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (lokasiGranted || kameraGranted || notifGranted) {
            dismiss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRequestPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false // Mencegah dialog tertutup jika diklik di luar area dialog

        binding.btnIzinkanPopUp.setOnClickListener {
            mintaIzinSistem()
        }

        binding.btnNantiSaja.setOnClickListener {
            dismiss() // Tutup pop-up jika dilewati
        }
    }

    private fun mintaIzinSistem() {
        val listIzin = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listIzin.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(listIzin.toTypedArray())
    }

    companion object {
        fun periksaDanTampilkan(fragmentManager: FragmentManager, context: Context) {
            val lokasiOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val kameraOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true

            if (!lokasiOk || !kameraOk || !notifOk) {
                val dialog = PermissionDialogFragment()
                dialog.show(fragmentManager, "PermissionDialog")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}