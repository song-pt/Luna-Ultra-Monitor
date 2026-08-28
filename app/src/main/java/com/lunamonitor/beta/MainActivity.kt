package com.lunamonitor.beta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunamonitor.beta.ui.LunaMonitorApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 1001)
        }
        setContent {
            val monitorViewModel: MonitorViewModel = viewModel()
            LunaMonitorApp(monitorViewModel)
        }
    }
}
