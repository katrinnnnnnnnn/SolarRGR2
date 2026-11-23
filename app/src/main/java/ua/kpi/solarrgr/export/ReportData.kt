package ua.kpi.solarrgr.export

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ReportRow(val hour: String, val radiation: Double, val powerKw: Double)

/** Серіалізація/десеріалізація звіту в CSV та JSON з валідацією. */
object ReportData {
    private const val HEADER = "hour,radiation,powerKw"
    private val gson = Gson()

    fun validate(rows: List<ReportRow>) {
        rows.forEach {
            require(it.hour.isNotBlank()) { "Порожній час" }
            require(it.radiation >= 0) { "Від'ємна радіація: ${it.hour}" }
            require(it.powerKw >= 0) { "Від'ємна потужність: ${it.hour}" }
        }
    }

    fun toCsv(rows: List<ReportRow>): String {
        validate(rows)
        return buildString {
            appendLine(HEADER)
            rows.forEach { appendLine("${it.hour},${it.radiation},${it.powerKw}") }
        }
    }

    fun parseCsv(text: String): List<ReportRow> {
        val lines = text.lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty() && lines.first().trim() == HEADER) { "Невірний заголовок CSV" }
        val rows = lines.drop(1).mapIndexed { i, line ->
            val p = line.split(",")
            require(p.size == 3) { "Рядок ${i + 2}: очікується 3 поля" }
            ReportRow(
                p[0].trim(),
                p[1].toDoubleOrNull() ?: throw IllegalArgumentException("Рядок ${i + 2}: радіація не число"),
                p[2].toDoubleOrNull() ?: throw IllegalArgumentException("Рядок ${i + 2}: потужність не число")
            )
        }
        validate(rows)
        return rows
    }

    fun toJson(rows: List<ReportRow>): String {
        validate(rows)
        return gson.toJson(rows)
    }

    fun parseJson(text: String): List<ReportRow> {
        val type = object : TypeToken<List<ReportRow>>() {}.type
        val rows: List<ReportRow> = gson.fromJson(text, type) ?: throw IllegalArgumentException("Порожній JSON")
        validate(rows)
        return rows
    }
}
