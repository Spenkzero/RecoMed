package uy.recomed.app

import org.json.JSONObject

data class Medicine(
    val id: Int,
    val name: String,
    val dose: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("dose", dose)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject) = Medicine(
            id = json.optInt("id"),
            name = json.optString("name"),
            dose = json.optString("dose"),
            hour = json.optInt("hour"),
            minute = json.optInt("minute"),
            enabled = json.optBoolean("enabled", true)
        )
    }
}
