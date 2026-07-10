package com.preappointment1.app.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.ui.screens.FollowUpUi
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Generates a clean, printable PDF report of patient measurements.
 * 
 * Design: ultra-simple, rapid to read for a physician.
 * Content: raw patient data only, no AI conclusions, no diagnosis.
 */
object PdfReportGenerator {

    private const val PAGE_WIDTH = 595  // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN_LEFT = 50f
    private const val MARGIN_RIGHT = 50f
    private const val MARGIN_TOP = 60f
    private const val MARGIN_BOTTOM = 60f
    private const val USABLE_WIDTH = PAGE_WIDTH - 100f
    private const val LINE_HEIGHT = 16f
    private const val SECTION_GAP = 24f

    fun generate(
        context: Context,
        followUp: FollowUpUi,
        events: List<TimelineEventResponse>,
        patientName: String = "Patient"
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN_TOP

        // Paints
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 22f
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10f
            color = android.graphics.Color.GRAY
            isAntiAlias = true
            letterSpacing = 0.15f
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10f
            color = android.graphics.Color.DKGRAY
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 0.5f
        }
        val footerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.ITALIC)
            textSize = 8f
            color = android.graphics.Color.GRAY
            isAntiAlias = true
        }

        // Helper to check if we need a new page
        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                // Draw page footer
                drawFooter(canvas, footerPaint, pageNumber)
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN_TOP
            }
        }

        // ═══════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════
        canvas.drawText("PRE-APPOINTMENT 1", MARGIN_LEFT, y, titlePaint)
        y += 20f
        canvas.drawText("MEDICAL FOLLOW-UP REPORT", MARGIN_LEFT, y, subtitlePaint)
        y += 30f

        // Separator line
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        y += SECTION_GAP

        // ═══════════════════════════════════════
        // PATIENT & PROTOCOL INFO
        // ═══════════════════════════════════════
        val startDate = runCatching {
            Instant.parse(followUp.startsAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrDefault(LocalDate.now())
        val endDate = runCatching {
            Instant.parse(followUp.expiresAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrDefault(LocalDate.now().plusDays(14))
        val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

        val infoLines = listOf(
            "Patient" to patientName,
            "Protocol" to followUp.title,
            "Period" to "${startDate.format(dateFormat)} → ${endDate.format(dateFormat)}",
            "Duration" to "${followUp.totalDays} days",
            "Appointment" to endDate.format(dateFormat)
        )

        // Calculate completeness
        val userEvents = events.filter { it.type == "user" && !it.date_label.contains("Question") }
        val expectedMeasurements = followUp.totalDays * (followUp.schedule?.size ?: 1)
        val completionPercent = if (expectedMeasurements > 0) {
            ((userEvents.size.toFloat() / expectedMeasurements.toFloat()) * 100).toInt().coerceAtMost(100)
        } else 0

        for ((label, value) in infoLines) {
            ensureSpace(LINE_HEIGHT + 4f)
            canvas.drawText("$label:", MARGIN_LEFT, y, labelPaint)
            canvas.drawText(value, MARGIN_LEFT + 100f, y, bodyPaint)
            y += LINE_HEIGHT + 2f
        }
        ensureSpace(LINE_HEIGHT + 4f)
        canvas.drawText("Completion:", MARGIN_LEFT, y, labelPaint)
        canvas.drawText("$completionPercent% (${ userEvents.size } / $expectedMeasurements measurements)", MARGIN_LEFT + 100f, y, bodyPaint)
        y += SECTION_GAP

        // Separator
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        y += SECTION_GAP

        // ═══════════════════════════════════════
        // DATA VISUALIZATION (CHARTS)
        // ═══════════════════════════════════════
        val tempPoints = mutableListOf<Pair<LocalDate, Float>>()
        val painPoints = mutableListOf<Pair<LocalDate, Float>>()

        val tempRegex = Regex("(?i)temp(?:erature)?.*?(\\d{2}\\.?\\d*)")
        val painRegex = Regex("(?i)pain.*?(\\d{1,2})(?:\\s*/\\s*10)?")

        for (event in userEvents) {
            val date = runCatching {
                Instant.parse(event.effective_at ?: event.created_at).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrDefault(LocalDate.now())

            tempRegex.find(event.content)?.let { tempPoints.add(date to it.groupValues[1].toFloat()) }
            painRegex.find(event.content)?.let { painPoints.add(date to it.groupValues[1].toFloat()) }
        }

        // Draw Temperature Chart
        if (tempPoints.isNotEmpty()) {
            ensureSpace(180f)
            canvas.drawText("TEMPERATURE EVOLUTION (°C)", MARGIN_LEFT, y, headerPaint)
            y += 20f
            drawChart(canvas, tempPoints, 35f, 42f, MARGIN_LEFT, y, USABLE_WIDTH, 120f, android.graphics.Color.RED, startDate, endDate)
            y += 140f
            ensureSpace(SECTION_GAP)
        }

        // Draw Pain Chart
        if (painPoints.isNotEmpty()) {
            ensureSpace(180f)
            canvas.drawText("PAIN LEVEL EVOLUTION (0-10)", MARGIN_LEFT, y, headerPaint)
            y += 20f
            drawChart(canvas, painPoints, 0f, 10f, MARGIN_LEFT, y, USABLE_WIDTH, 120f, android.graphics.Color.BLUE, startDate, endDate)
            y += 140f
            ensureSpace(SECTION_GAP)
        }
        
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        y += SECTION_GAP

        // ═══════════════════════════════════════
        // MEASUREMENTS LOG
        // ═══════════════════════════════════════
        ensureSpace(24f)
        canvas.drawText("DETAILED MEASUREMENTS LOG", MARGIN_LEFT, y, headerPaint)
        y += 20f

        if (userEvents.isEmpty()) {
            ensureSpace(LINE_HEIGHT)
            canvas.drawText("No measurements recorded.", MARGIN_LEFT, y, bodyPaint)
            y += LINE_HEIGHT
        } else {
            // Group events by date
            val grouped = userEvents.groupBy { event ->
                val timeStr = event.effective_at ?: event.created_at
                runCatching {
                    Instant.parse(timeStr).atZone(ZoneId.systemDefault()).toLocalDate()
                }.getOrDefault(LocalDate.now())
            }.toSortedMap()

            for ((date, dayEvents) in grouped) {
                val formattedDate = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH))

                // Date header
                ensureSpace(LINE_HEIGHT + 8f)
                canvas.drawText(formattedDate, MARGIN_LEFT, y, labelPaint)
                y += 4f
                canvas.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + 200f, y, linePaint)
                y += LINE_HEIGHT

                for (event in dayEvents) {
                    val timeStr = event.effective_at ?: event.created_at
                    val time = runCatching {
                        Instant.parse(timeStr).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    }.getOrDefault("--:--")

                    // Parse measurement content into clean lines
                    val lines = event.content.split("\n").filter { it.isNotBlank() }
                    for (line in lines) {
                        val cleanLine = line.trim().removePrefix("•").removePrefix("-").trim()
                        if (cleanLine.isBlank()) continue

                        ensureSpace(LINE_HEIGHT + 2f)
                        canvas.drawText(time, MARGIN_LEFT + 10f, y, bodyPaint)
                        
                        val photoMatch = Regex("(?i)photo.*?([\\w\\d_-]+\\.jpg)").find(cleanLine)
                        if (photoMatch != null) {
                            val filename = photoMatch.groupValues[1]
                            val imgFile = File(context.filesDir, filename)
                            if (imgFile.exists()) {
                                try {
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath)
                                    if (bitmap != null) {
                                        canvas.drawText("Photo attached:", MARGIN_LEFT + 60f, y, bodyPaint)
                                        y += LINE_HEIGHT
                                        ensureSpace(110f)
                                        val destRect = android.graphics.RectF(MARGIN_LEFT + 60f, y, MARGIN_LEFT + 60f + 100f, y + 100f)
                                        canvas.drawBitmap(bitmap, null, destRect, null)
                                        y += 110f
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            } else {
                                canvas.drawText("[Photo attached but unavailable on this device]", MARGIN_LEFT + 60f, y, bodyPaint)
                                y += LINE_HEIGHT
                            }
                        } else {
                            // Word-wrap long lines
                            val maxLineWidth = USABLE_WIDTH - 70f
                            val textToDraw = cleanLine
                            if (bodyPaint.measureText(textToDraw) <= maxLineWidth) {
                                canvas.drawText(textToDraw, MARGIN_LEFT + 60f, y, bodyPaint)
                                y += LINE_HEIGHT
                            } else {
                                // Simple word wrap
                                val words = textToDraw.split(" ")
                                var currentLine = ""
                                for (word in words) {
                                    val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                                    if (bodyPaint.measureText(test) <= maxLineWidth) {
                                        currentLine = test
                                    } else {
                                        ensureSpace(LINE_HEIGHT)
                                        canvas.drawText(currentLine, MARGIN_LEFT + 60f, y, bodyPaint)
                                        y += LINE_HEIGHT
                                        currentLine = word
                                    }
                                }
                                if (currentLine.isNotEmpty()) {
                                    ensureSpace(LINE_HEIGHT)
                                    canvas.drawText(currentLine, MARGIN_LEFT + 60f, y, bodyPaint)
                                    y += LINE_HEIGHT
                                }
                            }
                        }
                    }
                    y += 4f // Small gap between entries
                }
                y += 8f // Gap between days
            }
        }

        // ═══════════════════════════════════════
        // QUESTIONS & ANSWERS (if any)
        // ═══════════════════════════════════════
        val questions = events.filter { it.type == "user" && it.date_label.contains("Question") }
        if (questions.isNotEmpty()) {
            y += SECTION_GAP / 2
            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            y += SECTION_GAP

            ensureSpace(24f)
            canvas.drawText("PATIENT QUESTIONS", MARGIN_LEFT, y, headerPaint)
            y += 20f

            for (q in questions) {
                ensureSpace(LINE_HEIGHT * 2)
                val time = runCatching {
                    Instant.parse(q.created_at).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                }.getOrDefault("--")
                canvas.drawText("[$time] ${q.content}", MARGIN_LEFT + 10f, y, bodyPaint)
                y += LINE_HEIGHT

                // Find the AI response (next ai event after this one in the full list)
                val qIndex = events.indexOf(q)
                if (qIndex + 1 < events.size && events[qIndex + 1].type == "ai") {
                    val aiReply = events[qIndex + 1].content
                    val replyLines = aiReply.split("\n").filter { it.isNotBlank() }
                    for (line in replyLines.take(3)) { // Limit to 3 lines max
                        ensureSpace(LINE_HEIGHT)
                        val truncated = if (line.length > 90) line.take(87) + "..." else line
                        canvas.drawText("  → $truncated", MARGIN_LEFT + 20f, y, bodyPaint)
                        y += LINE_HEIGHT
                    }
                }
                y += 8f
            }
        }

        // ═══════════════════════════════════════
        // DISCLAIMER FOOTER
        // ═══════════════════════════════════════
        y += SECTION_GAP
        ensureSpace(40f)
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        y += 16f

        val disclaimerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textSize = 8f
            color = android.graphics.Color.GRAY
            isAntiAlias = true
        }
        canvas.drawText("This document contains patient-reported data only. It is not a clinical document.", MARGIN_LEFT, y, disclaimerPaint)
        y += 12f
        canvas.drawText("No diagnosis, interpretation, or treatment suggestion is included.", MARGIN_LEFT, y, disclaimerPaint)
        y += 12f
        val genDate = LocalDate.now().format(dateFormat)
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
        canvas.drawText("Generated by P1 — Pre-Appointment 1 (v$appVersion) on $genDate", MARGIN_LEFT, y, disclaimerPaint)

        // Draw footer on last page
        drawFooter(canvas, footerPaint, pageNumber)
        document.finishPage(page)

        // Save to cache dir
        val outputFile = File(context.cacheDir, "medical_report_${followUp.id.take(8)}.pdf")
        FileOutputStream(outputFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return outputFile
    }

    private fun drawChart(
        canvas: Canvas,
        points: List<Pair<LocalDate, Float>>,
        minY: Float,
        maxY: Float,
        xBase: Float,
        yBase: Float,
        width: Float,
        height: Float,
        lineColor: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        val gridPaint = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 0.5f }
        val axisPaint = Paint().apply { color = android.graphics.Color.DKGRAY; strokeWidth = 1.5f }
        val linePaint = Paint().apply { color = lineColor; strokeWidth = 2.5f; style = Paint.Style.STROKE; isAntiAlias = true }
        val pointPaint = Paint().apply { color = lineColor; style = Paint.Style.FILL; isAntiAlias = true }
        val textPaint = Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 8f; isAntiAlias = true }

        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(1)

        // Draw Y grid and labels
        val ySteps = 5
        for (i in 0..ySteps) {
            val ratio = i.toFloat() / ySteps
            val currentY = yBase + height - (ratio * height)
            val value = minY + ratio * (maxY - minY)
            canvas.drawLine(xBase + 20f, currentY, xBase + width, currentY, gridPaint)
            val label = String.format(Locale.US, "%.1f", value).replace(".0", "")
            canvas.drawText(label, xBase, currentY + 3f, textPaint)
        }

        // Draw Axes
        canvas.drawLine(xBase + 20f, yBase, xBase + 20f, yBase + height, axisPaint) // Y
        canvas.drawLine(xBase + 20f, yBase + height, xBase + width, yBase + height, axisPaint) // X

        // Aggregate multiple points per day (average)
        val dailyAverages = points.groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second }.average().toFloat() }
            .toSortedMap()

        // Plot points
        val path = android.graphics.Path()
        var isFirst = true

        for ((date, value) in dailyAverages) {
            val dayOffset = ChronoUnit.DAYS.between(startDate, date).toInt()
            if (dayOffset < 0 || dayOffset > totalDays) continue

            val px = xBase + 20f + (dayOffset.toFloat() / totalDays) * (width - 20f)
            val clampedValue = value.coerceIn(minY, maxY)
            val py = yBase + height - ((clampedValue - minY) / (maxY - minY) * height)

            if (isFirst) {
                path.moveTo(px, py)
                isFirst = false
            } else {
                path.lineTo(px, py)
            }

            canvas.drawCircle(px, py, 3f, pointPaint)

            // Draw date label for some points (avoid overlap)
            if (dayOffset % ((totalDays / 5).coerceAtLeast(1)) == 0 || dayOffset == totalDays) {
                val dateLabel = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
                canvas.drawText(dateLabel, px - 10f, yBase + height + 12f, textPaint)
            }
        }
        
        if (!isFirst) {
            canvas.drawPath(path, linePaint)
        }
    }

    private fun drawFooter(canvas: Canvas, paint: Paint, pageNumber: Int) {
        canvas.drawText(
            "Page $pageNumber",
            (PAGE_WIDTH / 2f) - 15f,
            PAGE_HEIGHT - 30f,
            paint
        )
    }
}
