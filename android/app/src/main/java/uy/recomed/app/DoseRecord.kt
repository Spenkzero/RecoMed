package uy.recomed.app

import org.json.JSONObject

data class DoseRecord(
    val medicineId: Int,
    val medicineName: String,
    val date: String,
    val status: Status,
    val recordedAt: Long
) {
    enum class Status {
        TAKEN,
        MISSED
    }

    fun toJson() = JSONObject().apply {
        put("medicineId", medicineId)
        put("medicineName", medicineName)
        put("date", date)
        put("status", status.name)
        put("recordedAt", recordedAt)
    }

    companion object {
        fun fromJson(json: JSONObject) = DoseRecord(
            medicineId = json.getInt("medicineId"),
            medicineName = json.getString("medicineName"),
            date = json.getString("date"),
            status = Status.valueOf(json.getString("status")),
            recordedAt = json.getLong("recordedAt")
        )
    }
}
