package dev.krishnagurram.budguard.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** On-device event log. Everything stays on the phone. */
class EventStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "budguard.db", null, 1) {

    private val version = MutableStateFlow(0)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER NOT NULL,
                type TEXT NOT NULL,
                source TEXT NOT NULL,
                lat REAL,
                lng REAL,
                accuracy REAL,
                place TEXT,
                battery INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX events_time ON events(time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(time: Long, type: EventType, source: EventSource, battery: Int? = null): Long {
        val values = ContentValues().apply {
            put("time", time)
            put("type", type.name)
            put("source", source.name)
            if (battery != null) put("battery", battery)
        }
        val id = writableDatabase.insert("events", null, values)
        version.value++
        return id
    }

    fun attachLocation(id: Long, lat: Double, lng: Double, accuracyM: Float?, place: String?) {
        val values = ContentValues().apply {
            put("lat", lat)
            put("lng", lng)
            if (accuracyM != null) put("accuracy", accuracyM)
            if (place != null) put("place", place)
        }
        writableDatabase.update("events", values, "id = ?", arrayOf(id.toString()))
        version.value++
    }

    fun recent(limit: Int = 500): List<BudEvent> =
        readableDatabase.rawQuery(
            "SELECT * FROM events ORDER BY time DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toEvent()) } }

    fun observe(): Flow<List<BudEvent>> = version.map { recent() }.flowOn(Dispatchers.IO)

    fun clear() {
        writableDatabase.delete("events", null, null)
        version.value++
    }

    private fun Cursor.toEvent(): BudEvent {

        fun doubleOrNull(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }

        val accIdx = getColumnIndexOrThrow("accuracy")
        val placeIdx = getColumnIndexOrThrow("place")
        val battIdx = getColumnIndexOrThrow("battery")
        return BudEvent(
            id = getLong(getColumnIndexOrThrow("id")),
            time = getLong(getColumnIndexOrThrow("time")),
            type = EventType.valueOf(getString(getColumnIndexOrThrow("type"))),
            source = EventSource.valueOf(getString(getColumnIndexOrThrow("source"))),
            lat = doubleOrNull("lat"),
            lng = doubleOrNull("lng"),
            accuracyM = if (isNull(accIdx)) null else getFloat(accIdx),
            place = if (isNull(placeIdx)) null else getString(placeIdx),
            battery = if (isNull(battIdx)) null else getInt(battIdx),
        )
    }

    companion object {
        @Volatile private var instance: EventStore? = null

        fun get(context: Context): EventStore =
            instance ?: synchronized(this) { instance ?: EventStore(context).also { instance = it } }
    }
}
