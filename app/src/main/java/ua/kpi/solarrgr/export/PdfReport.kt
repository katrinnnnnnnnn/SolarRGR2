package ua.kpi.solarrgr.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Двосторінковий PDF: таблиця погодинних даних і стовпчиковий графік потужності. */
object PdfReport {
    fun create(file: File, title: String, rows: List<ReportRow>) {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK; textSize = 12f }
        val head = Paint(paint).apply { textSize = 18f; isFakeBoldText = true }

        val p1 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        p1.canvas.drawText(title, 40f, 50f, head)
        val total = rows.sumOf { it.powerKw }
        p1.canvas.drawText("Записів: ${rows.size}; сума потужності: %.1f кВт·год".format(total), 40f, 75f, paint)
        var y = 105f
        p1.canvas.drawText("Година", 40f, y, paint)
        p1.canvas.drawText("Радіація, Вт/м²", 230f, y, paint)
        p1.canvas.drawText("Потужність, кВт", 400f, y, paint)
        y += 18f
        for (r in rows.take(40)) {
            p1.canvas.drawText(r.hour, 40f, y, paint)
            p1.canvas.drawText("%.0f".format(r.radiation), 230f, y, paint)
            p1.canvas.drawText("%.1f".format(r.powerKw), 400f, y, paint)
            y += 16f
        }
        doc.finishPage(p1)

        val p2 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
        p2.canvas.drawText("Графік потужності, кВт", 40f, 50f, head)
        val bars = rows.take(48)
        val max = (bars.maxOfOrNull { it.powerKw } ?: 1.0).coerceAtLeast(1.0)
        val bw = 500f / bars.size.coerceAtLeast(1)
        val fill = Paint().apply { color = Color.rgb(103, 80, 164) }
        bars.forEachIndexed { i, r ->
            val h = (r.powerKw / max * 300).toFloat()
            p2.canvas.drawRect(40f + i * bw, 400f - h, 40f + (i + 1) * bw - 2f, 400f, fill)
        }
        p2.canvas.drawLine(40f, 400f, 540f, 400f, paint)
        doc.finishPage(p2)

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}
