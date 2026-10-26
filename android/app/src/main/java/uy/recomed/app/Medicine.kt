package uy.recomed.app

import org.json.JSONObject

data class Medicine(
    val id: Int,
    val name: String,
    val dose: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val icon: String = "Tableta",
    val color: Int = 0xFF176B5B.toInt(), // Color de tarjeta / acento
    val routine: String = "Diario", // "Mañana", "Tarde", "Noche", "Diario"
    val frequency: String = "Cada 24 horas",
    val alarmTone: String = "default", // "default", "suave", "urgente"
    val customToneUri: String = "" // URI del archivo multimedia personalizado del usuario
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("dose", dose)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("icon", icon)
        put("color", color)
        put("routine", routine)
        put("frequency", frequency)
        put("alarmTone", alarmTone)
        put("customToneUri", customToneUri)
    }

    companion object {
        fun fromJson(json: JSONObject) = Medicine(
            id = json.optInt("id"),
            name = json.optString("name"),
            dose = json.optString("dose"),
            hour = json.optInt("hour"),
            minute = json.optInt("minute"),
            enabled = json.optBoolean("enabled", true),
            icon = when (val savedIcon = json.optString("icon", "Tableta")) {
                "💊" -> "Tableta"
                "🧪" -> "Jarabe"
                "💉" -> "Inyección"
                "💧" -> "Gotas"
                "💨" -> "Inhalador"
                else -> savedIcon
            },
            color = json.optInt("color", 0xFF176B5B.toInt()),
            routine = json.optString("routine", "Diario"),
            frequency = json.optString("frequency", "Cada 24 horas"),
            alarmTone = json.optString("alarmTone", "default"),
            customToneUri = json.optString("customToneUri", "")
        )
    }
}
