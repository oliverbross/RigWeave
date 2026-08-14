package app.rigweave.mobile

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Qso(val id: String, val callsign: String, val frequencyHz: Long, val mode: String,
               val rstSent: String, val rstReceived: String, val createdAt: Long)

class QsoDatabase(context: Context) : SQLiteOpenHelper(context, "rigweave.sqlite", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE radio_profile(id TEXT PRIMARY KEY, model TEXT NOT NULL)")
        db.execSQL("CREATE TABLE qso(id TEXT PRIMARY KEY, callsign TEXT NOT NULL, frequency_hz INTEGER NOT NULL, mode TEXT NOT NULL, rst_sent TEXT NOT NULL, rst_received TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(qso: Qso): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM qso WHERE callsign=? AND frequency_hz=? AND mode=? AND created_at>=? LIMIT 1",
            arrayOf(qso.callsign, qso.frequencyHz.toString(), qso.mode, (qso.createdAt - 15).toString())).use {
            if (it.moveToFirst()) return false
        }
        writableDatabase.execSQL("INSERT INTO qso(id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at) VALUES(?,?,?,?,?,?,?)",
            arrayOf<Any>(qso.id, qso.callsign, qso.frequencyHz, qso.mode, qso.rstSent, qso.rstReceived, qso.createdAt))
        return true
    }

    fun list(): List<Qso> = query(" LIMIT 100")
    fun all(): List<Qso> = query("")

    private fun query(limit: String): List<Qso> = buildList {
        readableDatabase.rawQuery("SELECT id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at FROM qso ORDER BY created_at DESC" + limit, null).use { cursor ->
            while (cursor.moveToNext()) add(Qso(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getLong(6)))
        }
    }
}
