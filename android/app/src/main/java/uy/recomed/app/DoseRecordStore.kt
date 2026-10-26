package uy.recomed.app

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

object DoseRecordStore {
    private const val PREFS = "recomed_tracking"
    private const val KEY = "dose_records"

    fun get(context: Context): List<DoseRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                runCatching { DoseRecord.fromJson(array.getJSONObject(index)) }
                    .onSuccess(::add)
            }
        }
    }

    fun find(context: Context, medicineId: Int, date: LocalDate): DoseRecord? =
        get(context).firstOrNull { it.medicineId == medicineId && it.date == date.toString() }

    fun record(context: Context, medicineId: Int, medicineName: String, date: LocalDate, status: DoseRecord.Status) {
        val record = DoseRecord(
            medicineId = medicineId,
            medicineName = medicineName,
            date = date.toString(),
            status = status,
            recordedAt = System.currentTimeMillis()
        )
        val records = get(context)
            .filterNot { it.medicineId == medicineId && it.date == record.date }
            .plus(record)
            .sortedWith(compareBy<DoseRecord> { it.date }.thenBy { it.medicineId })
        val array = JSONArray().apply { records.forEach { put(it.toJson()) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
