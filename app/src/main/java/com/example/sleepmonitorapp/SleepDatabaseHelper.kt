package com.example.sleepmonitorapp


import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.io.IOException

class SleepDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "sleep_data.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "sleep_data"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_BRIGHTNESS = "brightness"
        private const val COLUMN_PROXIMITY = "proximity"
        private const val COLUMN_ACCX = "accx"
        private const val COLUMN_ACCY = "accy"
        private const val COLUMN_ACCZ = "accz"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableStatement = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp DATETIME,
            brightness INTEGER,
            proximity INTEGER,
            accx INTEGER,
            accy INTEGER,
            accz INTEGER,
            gyrox INTEGER,
            gyroy INTEGER,
            gyroz INTEGER,
            status TEXT
        );
    """.trimIndent()
        db.execSQL(createTableStatement)
//
//        val createTriggerStatement = """
//        CREATE TRIGGER IF NOT EXISTS increment_timestamp_before_insert
//        BEFORE INSERT ON $TABLE_NAME
//        FOR EACH ROW
//        BEGIN
//            SELECT CASE
//                WHEN (SELECT COUNT(*) FROM $TABLE_NAME) > 0 THEN
//                    NEW.timestamp = strftime('%Y-%m-%d %H:%M:%S', (SELECT timestamp FROM $TABLE_NAME ORDER BY id DESC LIMIT 1), '+1 second', '-4 hours')
//                ELSE
//                    NEW.timestamp = strftime('%Y-%m-%d %H:%M:%S', 'now', '-4 hours')
//            END;
//        END;
//    """.trimIndent()
//        db.execSQL(createTriggerStatement)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Função para registrar falhas de inserção em um arquivo de texto
    private fun logInsertionFailure(message: String) {
        val logFile = File(context.filesDir, "insertion_failures.txt")
        try {
            logFile.appendText("${System.currentTimeMillis()} - $message\n")
        } catch (e: IOException) {
            Log.e("SleepDatabaseHelper", "Erro ao gravar no arquivo de log: ${e.message}", e)
        }
    }

    @Synchronized
    fun insertSleepData(brightness: Float, proximity: Float, accx: Float, accy: Float, accz: Float, gyrox: Float, gyroy: Float, gyroz: Float, status: String) {
        val db = writableDatabase

        // Verificação de bloqueio
        if (db.isDbLockedByCurrentThread || db.isDbLockedByOtherThreads) {
            Log.d("SleepDatabaseHelper", "Banco de dados bloqueado. Tentando novamente...")
            Thread.sleep(100) // Aguarda 100ms antes de tentar novamente
            insertSleepData(brightness, proximity, accx, accy, accz, gyrox, gyroy, gyroz, status) // Chama novamente
            return
        }

        db.beginTransaction()
        var isInserted = false

        try {
            // Obtenha o timestamp mais recente
            val cursor = db.rawQuery("SELECT timestamp FROM $TABLE_NAME ORDER BY id DESC LIMIT 1", null)
            val newTimestamp: String = if (cursor.moveToFirst()) {
                val lastTimestamp = cursor.getString(0)
                // Incrementa 1 segundo ao timestamp mais recente
                "strftime('%Y-%m-%d %H:%M:%S', '$lastTimestamp', '+1 second')"
            } else {
                // Caso não haja registros, use o timestamp atual
                "strftime('%Y-%m-%d %H:%M:%S', 'now', '-4 hours')"
            }
            cursor.close()

            // Inserir os dados com o timestamp calculado
            val insertStatement = """
    INSERT INTO $TABLE_NAME (timestamp, brightness, proximity, accx, accy, accz, gyrox, gyroy, gyroz, status)
    VALUES (strftime('%Y-%m-%d %H:%M:%S', 'now', '-4 hours'), ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()
            db.execSQL(insertStatement, arrayOf(brightness, proximity, accx, accy, accz, gyrox, gyroy, gyroz, status))
            db.setTransactionSuccessful()
            isInserted = true
            Thread.sleep(100)
            Log.d("SleepDatabaseHelper", "Dados inseridos com sucesso: Brightness=$brightness, Prox=$proximity, " +
                    "AccX=$accx, AccY=$accy, AccZ=$accz, GyroX=$gyrox, GyroY=$gyroy, GyroZ=$gyroz, Status=$status")
        } catch (e: Exception) {
            Log.e("SleepDatabaseHelper", "Erro ao inserir dados durante a transação (Primeira tentativa): ${e.message}", e)
        } finally {
            db.endTransaction()
        }

        // Verificação de re-tentativa se a primeira inserção falhar
        if (!isInserted) {
            try {
                db.beginTransaction()
                val insertStatement = "INSERT INTO $TABLE_NAME (brightness, proximity, accx, accy, accz, gyrox, gyroy, gyroz, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                db.execSQL(insertStatement, arrayOf(brightness, proximity, accx, accy, accz, gyrox, gyroy, gyroz))
                db.setTransactionSuccessful()
                isInserted = true
                Log.d("SleepDatabaseHelper", "Dados inseridos com sucesso na segunda tentativa: Brightness=$brightness, " +
                        "Prox=$proximity, AccX=$accx, AccY=$accy, AccZ=$accz, GyroX=$gyrox, GyroY=$gyroy, GyroZ=$gyroz, Status=$status")
            } catch (e: Exception) {
                Log.e("SleepDatabaseHelper", "Erro ao inserir dados durante a transação (Segunda tentativa): ${e.message}", e)
                logInsertionFailure("Falha ao inserir dados (segunda tentativa): Brightness=$brightness, Prox=$proximity, " +
                        "AccX=$accx, AccY=$accy, AccZ=$accz, GyroX=$gyrox, GyroY=$gyroy, GyroZ=$gyroz, Status=$status - Erro: ${e.message}")
            } finally {
                db.endTransaction()
            }
        }

        if (!isInserted) {
            Log.e("SleepDatabaseHelper", "Falha ao inserir dados após duas tentativas.")
            logInsertionFailure("Falha definitiva ao inserir dados após duas tentativas: Brightness=$brightness, Prox=$proximity, " +
                    "AccX=$accx, AccY=$accy, AccZ=$accz, GyroX=$gyrox, GyroY=$gyroy, GyroZ=$gyroz, Status=$status")
        }
    }

    fun getAllSleepData(): List<String> {
        val db = SQLiteDatabase.openDatabase("/data/data/com.example.sleepmonitorapp/databases/sleep_data.db", null, SQLiteDatabase.OPEN_READWRITE)
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)
        val data = mutableListOf<String>()

        if (cursor.moveToFirst()) {
            do {
                val row = (0 until cursor.columnCount).joinToString(",") { cursor.getString(it) }
                data.add(row)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return data
    }

//    fun getAllSleepData(): List<String> {
//        val db = readableDatabase
//        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)
//        val data = mutableListOf<String>()
//        if (cursor.moveToFirst()) {
//            do {
//                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
//                val brightness = cursor.getInt(cursor.getColumnIndexOrThrow("brightness"))
//                val proximity = cursor.getDouble(cursor.getColumnIndexOrThrow("proximity"))
//                val accx = cursor.getDouble(cursor.getColumnIndexOrThrow("accx"))
//                val accy = cursor.getInt(cursor.getColumnIndexOrThrow("accy"))
//                val accz = cursor.getInt(cursor.getColumnIndexOrThrow("accz"))
//                val gyrox = cursor.getInt(cursor.getColumnIndexOrThrow("gyrox"))
//                val gyroy = cursor.getInt(cursor.getColumnIndexOrThrow("gyroy"))
//                val gyroz = cursor.getInt(cursor.getColumnIndexOrThrow("gyroz"))
//                val status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
//
//                data.add("Timestamp: $timestamp, Brightness: $brightness, Proximity=$proximity, " +
//                        "AccX=$accx, AccY=$accy, AccZ=$accz, GyroX=$gyrox, GyroY=$gyroy, GyroZ=$gyroz, Status=$status")
//            } while (cursor.moveToNext())
//        }
//        cursor.close()
//        return data
//    }

    fun countRowsInTable(dbPath: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0) // O número total de linhas
        cursor.close()
        db.close()
        return count
    }

    fun resetDatabase() {
        val db = writableDatabase
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")  // Exclui a tabela
        onCreate(db)  // Recria a tabela
    }
}