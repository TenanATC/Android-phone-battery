package com.tenan.batteryanalyzer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.TimeUnit

/**
 * Local SQLite store for battery samples. Kept deliberately dependency-free
 * (no Room) — one table, append-mostly, pruned to [RETENTION_DAYS].
 */
class BatteryHistoryStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE samples (
                ts INTEGER PRIMARY KEY,
                level INTEGER NOT NULL,
                charging INTEGER NOT NULL,
                plugged INTEGER NOT NULL,
                temp_c REAL NOT NULL,
                voltage_mv INTEGER NOT NULL,
                health INTEGER NOT NULL,
                screen_on INTEGER NOT NULL,
                current_ua INTEGER NOT NULL DEFAULT 0,
                charge_uah INTEGER NOT NULL DEFAULT -1,
                cycles INTEGER NOT NULL DEFAULT -1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS samples")
        onCreate(db)
    }

    fun insert(s: BatterySnapshot) {
        val values = ContentValues().apply {
            put("ts", s.timestamp)
            put("level", s.level)
            put("charging", if (s.isCharging) 1 else 0)
            put("plugged", s.plugged)
            put("temp_c", s.temperatureC)
            put("voltage_mv", s.voltageMv)
            put("health", s.health)
            put("screen_on", if (s.screenOn) 1 else 0)
            put("current_ua", s.currentNowUa)
            put("charge_uah", s.chargeCounterUah)
            put("cycles", s.cycleCount)
        }
        writableDatabase.insertWithOnConflict(
            "samples", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        prune()
    }

    fun samplesSince(sinceMillis: Long): List<BatterySnapshot> {
        val list = mutableListOf<BatterySnapshot>()
        readableDatabase.rawQuery(
            "SELECT ts, level, charging, plugged, temp_c, voltage_mv, health, screen_on, current_ua, charge_uah, cycles " +
                "FROM samples WHERE ts >= ? ORDER BY ts ASC",
            arrayOf(sinceMillis.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list += BatterySnapshot(
                    timestamp = c.getLong(0),
                    level = c.getInt(1),
                    isCharging = c.getInt(2) == 1,
                    plugged = c.getInt(3),
                    temperatureC = c.getFloat(4),
                    voltageMv = c.getInt(5),
                    health = c.getInt(6),
                    screenOn = c.getInt(7) == 1,
                    currentNowUa = c.getInt(8),
                    chargeCounterUah = c.getInt(9),
                    cycleCount = c.getInt(10),
                )
            }
        }
        return list
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        writableDatabase.delete("samples", "ts < ?", arrayOf(cutoff.toString()))
    }

    companion object {
        private const val DB_NAME = "battery_history.db"
        private const val DB_VERSION = 1
        private const val RETENTION_DAYS = 30L

        @Volatile
        private var instance: BatteryHistoryStore? = null

        fun get(context: Context): BatteryHistoryStore =
            instance ?: synchronized(this) {
                instance ?: BatteryHistoryStore(context).also { instance = it }
            }
    }
}
