package com.example.accessoriesmanager.report

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.accessoriesmanager.model.Installation
import com.example.accessoriesmanager.model.headquarterLabelFromAny
import com.example.accessoriesmanager.model.isPaidState
import com.example.accessoriesmanager.model.isPartialState
import com.example.accessoriesmanager.model.isUnpaidState
import com.example.accessoriesmanager.model.matchesDates
import com.example.accessoriesmanager.model.matchesStatus
import com.example.accessoriesmanager.model.toLocalDate
import com.example.accessoriesmanager.model.vehicleLabelFromAny
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the report ended up: [fileName]/[folder] for a confirmation message, [uri] + [mimeType]
 * to share it (e.g. via `Intent.ACTION_SEND`) without needing a `FileProvider` — a MediaStore
 * content:// uri is already shareable by the app that inserted it.
 */
data class ExportResult(val fileName: String, val folder: String, val uri: Uri, val mimeType: String)

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val DOWNLOADS_SUBFOLDER = "FacilityTracker"

/**
 * Builds a two-sheet (Resumen / Detalle) .xlsx report of installations, filtered by [ReportFilter],
 * and saves it directly into the public Downloads/FacilityTracker folder via MediaStore
 * (no storage permission needed on API 29+, which this app's minSdk already requires).
 */
@Singleton
class ExcelReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val moneyFormat = "\"$\" #,##0"
    private val dateFormat = "dd/mm/yyyy"

    private val headerFill = "4472C4"
    private val headerFont = "FFFFFF"

    private val paidFill = "C6EFCE"
    private val paidFont = "006100"
    private val unpaidFill = "FFC7CE"
    private val unpaidFont = "9C0006"
    private val partialFill = "FFEB9C"
    private val partialFont = "9C6500"

    private val summaryHeaders = listOf(
        "Fecha", "Orden", "Placa", "Serie", "Sede", "Vehículo",
        "# Accesorios", "Total Trabajado", "Total Pagado", "Total No Pagado", "Estado", "Comentario"
    )
    private val summaryWidths = listOf(12.0, 8.0, 10.0, 10.0, 16.0, 18.0, 12.0, 14.0, 14.0, 14.0, 12.0, 28.0)

    private val detailHeaders = listOf(
        "Fecha", "Orden", "Placa", "Serie", "Sede", "Vehículo", "Accesorio", "Precio", "Pagado", "Estado Instalación"
    )
    private val detailWidths = listOf(12.0, 8.0, 10.0, 10.0, 16.0, 18.0, 20.0, 12.0, 10.0, 16.0)

    suspend fun generate(installations: List<Installation>, filter: ReportFilter): ExportResult =
        withContext(Dispatchers.IO) {
            val (from, to) = filter.range.toDateBounds()
            val filtered = installations
                .filter { it.matchesDates(exact = null, from = from, to = to) }
                .filter { it.matchesStatus(filter.status) }
                .sortedByDescending { it.date?.seconds ?: 0L }

            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now())
            val fileName = "Reporte_Instalaciones_${filter.range.fileTag()}_$stamp.xlsx"
            val relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_SUBFOLDER"

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, XLSX_MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se pudo crear el archivo en Descargas")

            resolver.openOutputStream(uri)?.use { out ->
                Workbook(out, "FacilityTracker", "1.0").use { wb ->
                    writeSummarySheet(wb.newWorksheet("Resumen"), filtered)
                    writeDetailSheet(wb.newWorksheet("Detalle"), filtered)
                }
            } ?: throw IllegalStateException("No se pudo abrir el archivo para escritura")

            ExportResult(fileName, relativeFolder, uri, XLSX_MIME)
        }

    // -------------------- Sheets --------------------

    private fun writeSummarySheet(ws: Worksheet, installations: List<Installation>) {
        writeHeader(ws, summaryHeaders)

        installations.forEachIndexed { i, inst ->
            val row = i + 1
            ws.valueDate(row, 0, inst.date.toLocalDate())
            ws.valueLong(row, 1, inst.order?.toLong())
            ws.value(row, 2, inst.plate.orEmpty())
            ws.value(row, 3, inst.serie.orEmpty())
            ws.value(row, 4, headquarterLabelFromAny(inst.headquarter))
            ws.value(row, 5, vehicleLabelFromAny(inst.vehicle))
            ws.value(row, 6, (inst.accessories?.size ?: 0).toLong())
            ws.value(row, 7, inst.totalWorked ?: 0L)
            ws.value(row, 8, inst.totalPaid ?: 0L)
            ws.value(row, 9, inst.totalUnpaid ?: 0L)
            ws.value(row, 10, statusLabel(inst))
            ws.value(row, 11, inst.comment.orEmpty())

            ws.style(row, 0).format(dateFormat).set()
            ws.style(row, 7).format(moneyFormat).set()
            ws.style(row, 8).format(moneyFormat).set()
            ws.style(row, 9).format(moneyFormat).set()
            applyStatusStyle(ws, row, 10, inst)
        }

        val lastDataRow = installations.size
        if (installations.isNotEmpty()) {
            val totalsRow = lastDataRow + 1
            ws.value(totalsRow, 5, "TOTAL")
            ws.value(totalsRow, 7, installations.sumOf { it.totalWorked ?: 0L })
            ws.value(totalsRow, 8, installations.sumOf { it.totalPaid ?: 0L })
            ws.value(totalsRow, 9, installations.sumOf { it.totalUnpaid ?: 0L })
            ws.range(totalsRow, 0, totalsRow, summaryHeaders.lastIndex).style().bold().set()
            ws.style(totalsRow, 7).format(moneyFormat).bold().set()
            ws.style(totalsRow, 8).format(moneyFormat).bold().set()
            ws.style(totalsRow, 9).format(moneyFormat).bold().set()
        }

        ws.setAutoFilter(0, 0, lastDataRow, summaryHeaders.lastIndex)
        ws.freezePane(0, 1)
        summaryWidths.forEachIndexed { c, w -> ws.width(c, w) }
    }

    private fun writeDetailSheet(ws: Worksheet, installations: List<Installation>) {
        writeHeader(ws, detailHeaders)

        var row = 1
        installations.forEach { inst ->
            val date = inst.date.toLocalDate()
            val plate = inst.plate.orEmpty()
            val serie = inst.serie.orEmpty()
            val headquarter = headquarterLabelFromAny(inst.headquarter)
            val vehicle = vehicleLabelFromAny(inst.vehicle)
            val status = statusLabel(inst)

            inst.accessories.orEmpty().forEach { acc ->
                ws.valueDate(row, 0, date)
                ws.valueLong(row, 1, inst.order?.toLong())
                ws.value(row, 2, plate)
                ws.value(row, 3, serie)
                ws.value(row, 4, headquarter)
                ws.value(row, 5, vehicle)
                ws.value(row, 6, acc.name.orEmpty())
                ws.value(row, 7, acc.price)
                ws.value(row, 8, if (acc.isPaid) "Sí" else "No")
                ws.value(row, 9, status)

                ws.style(row, 0).format(dateFormat).set()
                ws.style(row, 7).format(moneyFormat).set()
                val (fill, font) = if (acc.isPaid) paidFill to paidFont else unpaidFill to unpaidFont
                ws.style(row, 8).fillColor(fill).fontColor(font).bold().set()

                row++
            }
        }

        val lastDataRow = (row - 1).coerceAtLeast(0)
        ws.setAutoFilter(0, 0, lastDataRow, detailHeaders.lastIndex)
        ws.freezePane(0, 1)
        detailWidths.forEachIndexed { c, w -> ws.width(c, w) }
    }

    // -------------------- Helpers --------------------

    private fun writeHeader(ws: Worksheet, headers: List<String>) {
        headers.forEachIndexed { c, h -> ws.value(0, c, h) }
        ws.range(0, 0, 0, headers.lastIndex).style()
            .bold()
            .fillColor(headerFill)
            .fontColor(headerFont)
            .horizontalAlignment("center")
            .set()
        ws.rowHeight(0, 18.0)
    }

    private fun statusLabel(inst: Installation): String = when {
        inst.isPaidState() -> "Pagado"
        inst.isUnpaidState() -> "No pagado"
        inst.isPartialState() -> "Parcial"
        else -> inst.state.orEmpty()
    }

    private fun applyStatusStyle(ws: Worksheet, row: Int, col: Int, inst: Installation) {
        val (fill, font) = when {
            inst.isPaidState() -> paidFill to paidFont
            inst.isUnpaidState() -> unpaidFill to unpaidFont
            inst.isPartialState() -> partialFill to partialFont
            else -> return
        }
        ws.style(row, col).fillColor(fill).fontColor(font).bold().set()
    }

    private fun Worksheet.valueDate(row: Int, col: Int, date: LocalDate?) {
        if (date != null) value(row, col, date) else value(row, col, "")
    }

    private fun Worksheet.valueLong(row: Int, col: Int, n: Long?) {
        if (n != null) value(row, col, n) else value(row, col, "")
    }

    private fun ReportRange.toDateBounds(): Pair<LocalDate?, LocalDate?> = when (this) {
        is ReportRange.All -> null to null
        is ReportRange.Range -> from to to
        is ReportRange.Month -> {
            val first = LocalDate.of(year, month, 1)
            first to first.with(TemporalAdjusters.lastDayOfMonth())
        }
    }

    private fun ReportRange.fileTag(): String = when (this) {
        is ReportRange.All -> "Todo"
        is ReportRange.Range -> "${from}_a_$to"
        is ReportRange.Month -> "%04d-%02d".format(year, month)
    }
}
