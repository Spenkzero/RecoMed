package uy.recomed.app

import android.content.Context
import org.json.JSONArray

object MedicineStore {
    private const val PREFS = "recomed_local"
    private const val KEY = "medicines"

    fun get(context: Context): MutableList<Medicine> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val result = mutableListOf<Medicine>()
        for (i in 0 until array.length()) {
            runCatching { result.add(Medicine.fromJson(array.getJSONObject(i))) }
        }
        return result
    }

    fun save(context: Context, medicines: List<Medicine>) {
        val array = JSONArray()
        medicines.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
