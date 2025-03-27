package com.example.sleepmonitorapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var status: String = "awake"
    private var sleepStatusTextView: TextView? = null
    private var sleepMonitorReceiver: BroadcastReceiver? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            Log.d("MainActivity", "${it.key} concedida: ${it.value}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.statusBarColor = getColor(android.R.color.darker_gray)

        setContentView(R.layout.activity_main)

        sleepStatusTextView = findViewById(R.id.sleep_status_text_view)

        val prefs = getSharedPreferences("SleepMonitorPrefs", MODE_PRIVATE)
        val savedStatus = prefs.getString("status", "")

        if (!savedStatus!!.isEmpty()) {
            sleepStatusTextView?.setText(if (savedStatus == "sleeping") "Você está dormindo. Monitorando..." else "Você está acordado. Monitorando...")
        } else {
            sleepStatusTextView?.setText("Você está acordado. Monitorando...")
        }

        sleepMonitorReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val sleepStatus = intent.getStringExtra("sleepStatus")
                Log.d("Sleep Status Received", "$sleepStatus")

                sleepStatusTextView?.setText(sleepStatus)
            }
        }

        Log.d("MainActivity", "iniciou")
        // Solicitar permissões necessárias
        requestRequiredPermissions()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        val intent = Intent(this, SleepService::class.java)
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(
                sleepMonitorReceiver,
                IntentFilter("SLEEP_MONITOR_UPDATE"),
                RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(sleepMonitorReceiver)
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_WIFI_STATE
                )
            )
        }

        // Permissão para modificar configurações do sistema (brilho)
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    fun toggleSleepStatus(view: View?) {
        val prefs = getSharedPreferences("SleepMonitorPrefs", MODE_PRIVATE)
        val editor = prefs.edit()

        if (status == "awake") {
            status = "sleeping"
            sleepStatusTextView?.setText("Você esta dormindo. Monitorando...")
            Log.d("SleepMonitor", "Status salvo: $status")
        } else {
            status = "awake"
            Log.d("SleepMonitor", "Status salvo: $status")
        }

        editor.putString("status", status)
        editor.apply()
    }

    fun stopSleepMonitor(view: View?) {
        val intent = Intent(
            this,
            SleepService::class.java
        )
        sleepStatusTextView?.setText("Você esta acordado. Monitorando...")

        status = "awake"

        val prefs = getSharedPreferences("SleepMonitorPrefs", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("status", status)
        editor.apply()
    }


}