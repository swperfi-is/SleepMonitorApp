package com.example.sleepmonitorapp

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class SleepJobService : JobService(), SensorEventListener {

    private lateinit var dbHelper: SleepDatabaseHelper
    private var hasGeneratedCSV = false

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

    private var currentStatusValue = "awake"

    override fun onCreate() {
        super.onCreate()
        dbHelper = SleepDatabaseHelper(this)
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("SleepJobService", "Iniciando coleta de dados...")

        initializeSensors()
        collectSleepData()

        val dbGet = "/data/data/com.example.sleepmonitorapp/databases/sleep_data.db"
        val rowCount = dbHelper.countRowsInTable(dbGet)
        Log.d("SleepJobService", "Quantidade de dados: $rowCount")

        if (rowCount >= 1440 && !hasGeneratedCSV) {
            if (generateCSV()) {
                resetDatabase()
            }
        }

        return false // Indica que o job foi concluído
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        stopSensorsMonitoring()
        return true // Permite reexecutar caso seja interrompido
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        if (proximity != null) sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        if (gyroscope != null) sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        if (light != null) sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopSensorsMonitoring() {
        sensorManager.unregisterListener(this)
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
        // Trate mudanças de precisão se necessário
    }

    private fun collectSleepData() {
        Log.d("SleepJobService", "Coletando dados do sensor...")

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
            "awake" // Aqui você pode pegar o status real do seu app
        )

        val queueManager = QueueManager(this)
        queueManager.addToQueue(sleepData)

        stopSensorsMonitoring()
    }

    private fun generateCSV(): Boolean {
        val dataList = dbHelper.getAllSleepData()
        val file = File(getCSVFilePath())

        try {
            val writer = file.bufferedWriter()
            writer.write("Id,Timestamp,Light,Proximity,AccelerometerX,AccelerometerY,AccelerometerZ,GyroscopeX,GyroscopeY,GyroscopeZ,Status\n")
            for (data in dataList) {
                writer.write("${data}\n")
            }
            writer.close()
            Log.d("SleepJobService", "Arquivo CSV criado com sucesso em: ${file.absolutePath}")

            hasGeneratedCSV = true
            return true
        } catch (e: Exception) {
            Log.e("SleepJobService", "Erro ao gerar o CSV: ${e.message}")
            return false
        }
    }

    private fun resetDatabase() {
        try {
            dbHelper.resetDatabase()
            hasGeneratedCSV = false
            Log.d("SleepJobService", "Banco de dados resetado com sucesso.")
        } catch (e: Exception) {
            Log.e("SleepJobService", "Erro ao resetar o banco de dados: ${e.message}")
        }
    }

    private fun getCSVFilePath(): String {
        val documentsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SleepData")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val baseFileName = "sleep_data_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.csv"
        return File(documentsDir, baseFileName).absolutePath
    }
}