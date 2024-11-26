package com.example.sleepmonitorapp

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class QueueManager(private val context: Context) {
    private val queue = ConcurrentLinkedQueue<SleepData>()
    private val dbHelper = SleepDatabaseHelper(context)
    private val executor = Executors.newSingleThreadExecutor()

    data class SleepData(
        val brightness: Float,
        val proximity: Float,
        val accx: Float,
        val accy: Float,
        val accz: Float,
        val gyrox: Float,
        val gyroy: Float,
        val gyroz: Float,
        val status: String
    )

    fun addToQueue(data: SleepData) {
        queue.add(data)
        processQueue()
    }

    private fun processQueue() {
        executor.execute {
            while (queue.isNotEmpty()) {
                val data = queue.poll()
                data?.let {
                    try {
                        dbHelper.insertSleepData(it.brightness, it.proximity, it.accx, it.accy, it.accz, it.gyrox, it.gyroy, it.gyroz, it.status)
                        Log.d("QueueManager", "Dados processados: Brightness=${it.brightness}, " +
                                "Proximity=${it.proximity}, AccX=${it.accx}, AccY=${it.accy}, " +
                                "AccZ=${it.accz}, GyroX=${it.gyrox}, GyroY=${it.gyroy}, GyroZ=${it.gyroz}, Status=${it.status}")
                    } catch (e: Exception) {
                        Log.e("QueueManager", "Erro ao processar dados: ${e.message}", e)
                    }
                }
            }
        }
    }
}