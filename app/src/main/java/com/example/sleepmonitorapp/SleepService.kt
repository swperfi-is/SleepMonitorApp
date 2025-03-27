package com.example.sleepmonitorapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class SleepService : Service(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dbHelper: SleepDatabaseHelper
    private var runnable: Runnable? = null
    private val channelId = "BatteryServiceChannel"

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var queueManager: QueueManager

    // Sensor Manager e Sensores
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximity: Sensor? = null
    private var gyroscope: Sensor? = null
    private var light: Sensor? = null

    private var accxValue: Float = 0f
    private var accyValue: Float = 0f
    private var acczValue: Float = 0f
    private var proximityValue: Float = 0f
    private var gyroxValue: Float = 0f
    private var gyroyValue: Float = 0f
    private var gyrozValue: Float = 0f
    private var lightValue: Float = 0f

    private var hasGeneratedCSV = false

    private var currentStatusValue = "awake"

    private var isHandlerRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d("SleepService", "Serviço criado")
        dbHelper = SleepDatabaseHelper(this)
        Log.d("SleepService", "dbHelper inicializado")
        queueManager = QueueManager(this)
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepService::WakeLock")
        wakeLock.acquire() // Mantenha o processador ativo


        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Solicita ao usuário ignorar otimizações de bateria
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
        startSensorsMonitoring()
    }

    private fun startSensorsMonitoring() {
        if (accelerometer != null && proximity != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager?.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager?.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager?.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }


    private fun stopSensorsMonitoring() {
        sensorManager?.unregisterListener(this, accelerometer)
        sensorManager?.unregisterListener(this, proximity)
        sensorManager?.unregisterListener(this, gyroscope)
        sensorManager?.unregisterListener(this, light)
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SleepService", "Serviço iniciado")
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sleep Service em execução")
            .setContentText("Coletando dados de bateria em segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification) // Mantém o serviço em execução em primeiro plano

        if (!isHandlerRunning) {
            isHandlerRunning = true
            runnable = Runnable {
                try {
                    val startTime = System.currentTimeMillis()

                    // Coleta os dados
                    collectSleepData()

                    val dbGet = "/data/data/com.example.sleepmonitorapp/databases/sleep_data.db"
                    val rowCount = dbHelper.countRowsInTable(dbGet)
                    Log.d("SleepService", "Quantidade de dados: $rowCount")

                    if (rowCount >= 1440 && !hasGeneratedCSV) {
                        if (generateCSV()) {
                            resetDatabase()
                        }
                    }

                    val nextExecutionTime = startTime + 60000
                    val delay = nextExecutionTime - System.currentTimeMillis()
                    //SystemClock.uptimeMillis()

                    handler.postDelayed(runnable!!, delay)
                } catch (e: Exception) {
                    Log.e("SleepService", "Erro durante a coleta de dados: ${e.message}")
                }
            }
            handler.post(runnable!!)
        }

        return START_STICKY
    }

    private fun generateCSV(): Boolean {
        val dataList = dbHelper.getAllSleepData()
        val file = File(getCSVFilePath())

        try {
            val writer = file.bufferedWriter()
            writer.write("Id,Timestamp,Light,Proximity,AccelerometerX,AccelerometerY,AccelerometerZ,GyroscopeX,GyroscopeY,GyroscopeZ,Status\n")
            for (data in dataList) {
                writer.write("${data}\n")  // Adiciona cada linha de dados
            }
            writer.close()
            Log.d("SleepService", "Arquivo CSV criado com sucesso em: ${file.absolutePath}")

            // Marca que o CSV foi gerado%$
            hasGeneratedCSV = true
            return true
        } catch (e: Exception) {
            Log.e("SleepService", "Erro ao gerar o CSV: ${e.message}")
            return false
        }
    }

    private fun resetDatabase() {
        try {
            dbHelper.resetDatabase()  // Exclui e recria o banco de dados
            hasGeneratedCSV = false
            Log.d("SleepService", "Banco de dados resetado com sucesso.")
        } catch (e: Exception) {
            Log.e("SleepService", "Erro ao resetar o banco de dados: ${e.message}")
        }
    }

    private fun getCSVFilePath(): String {
        val documentsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SleepData")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val baseFileName = "sleep_data_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.csv"

        // Caminho completo para o arquivo
        var file = File(documentsDir, baseFileName)
        var fileName = baseFileName
        var count = 1

        while (file.exists()) {
            fileName = "sleep_data_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}_($count).csv"
            file = File(documentsDir, fileName)
            count++
        }

        return file.absolutePath
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("SleepService", "Serviço destruído")
        handler.removeCallbacks(runnable!!)
        isHandlerRunning = false
        if (wakeLock.isHeld) {
            wakeLock.release() // Liberte o WakeLock quando o serviço for destruído
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Sleep Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun collectSleepData() {
        val prefs = getSharedPreferences("SleepMonitorPrefs", MODE_PRIVATE)
        currentStatusValue = prefs.getString("status", "awake")!!

        Log.d("SleepService", "Coleta de Dados - Status: $currentStatusValue, Brightness: $lightValue, " +
                "Prox: ${proximityValue}, Accx: ${accxValue}")

        val sleepData = QueueManager.SleepData(
            lightValue,
            proximityValue,
            accxValue,
            accyValue,
            acczValue,
            gyroxValue,
            gyroyValue,
            gyrozValue,
            currentStatusValue
        )
        queueManager.addToQueue(sleepData)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accxValue = event.values[0]
                accyValue = event.values[1]
                acczValue = event.values[2]
            }
            Sensor.TYPE_PROXIMITY -> {
                proximityValue = event.values[0]
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroxValue = event.values[0]
                gyroyValue = event.values[1]
                gyrozValue = event.values[2]
            }
            Sensor.TYPE_LIGHT -> {
                lightValue = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }
}