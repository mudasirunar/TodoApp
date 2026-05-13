package com.example.mytodoapp.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.example.mytodoapp.data.TodoTask
import java.io.FileOutputStream

object PdfHelper {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val COL_SR = MARGIN
    private const val COL_TASK = 75f
    private const val TABLE_WIDTH = 555f

    fun generateTodoPdf(context: Context, uri: Uri, title: String, tasks: List<TodoTask>, config: PdfConfig = PdfConfig()) {
        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    writePdfToStream(outputStream, title, tasks, config)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun writePdfToStream(outputStream: java.io.OutputStream, title: String, tasks: List<TodoTask>, config: PdfConfig = PdfConfig()) {
        val pdfDocument = PdfDocument()
        val validTasks = tasks.filter { it.text.isNotBlank() }
        
        var currentPageNumber = 1
        var y = 90f
        
        // Setup initial page
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Draw Title only on first page
        drawTitle(canvas, title)
        drawTableHeader(canvas, y, config)
        y += 20f

        validTasks.forEachIndexed { index, task ->
            val rowHeight = calculateRowHeight(task, taskTextPaint, config)
            
            // Multi-page Check
            if (y + rowHeight > 800f) {
                pdfDocument.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
                drawTableHeader(canvas, y, config)
                y += 20f
            }

            drawTaskRow(canvas, index, task, y, rowHeight, config)
            y += rowHeight + 2f // Add small gap
        }

        // --- ADD SUMMARY TABLE ---
        if (config.includeSummary && (config.includeStatus || config.includeFavorites)) {
            val summaryHeight = (if (config.includeStatus) 3 * 18f else 0f) + (if (config.includeFavorites) 18f else 0f) + 60f
            
            // Multi-page Check for Summary
            if (y + summaryHeight > 800f) {
                pdfDocument.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
            
            drawSummaryTable(canvas, y, validTasks, config)
        }

        pdfDocument.finishPage(page)

        try {
            pdfDocument.writeTo(outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }

    // --- Drawing Components (Reusable for Preview) ---

    private fun drawSummaryTable(canvas: Canvas, y: Float, tasks: List<TodoTask>, config: PdfConfig) {
        var currentY = y + 35f // Space from last row
        
        // Section Title
        canvas.drawText("Summary", COL_SR, currentY, headerPaint)
        currentY += 10f
        
        // Minimalist Divider
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 1f
        }
        canvas.drawLine(COL_SR, currentY, COL_SR + 120f, currentY, dividerPaint)
        currentY += 22f

        val labelPaint = Paint().apply {
            textSize = 11f
            color = Color.parseColor("#444444")
            isAntiAlias = true
        }

        if (config.includeStatus) {
            // Sorted to match user request: Done, Ongoing, Coming Up (Pending)
            val sortedStatuses = listOf(com.example.mytodoapp.data.TodoStatus.Done, com.example.mytodoapp.data.TodoStatus.Ongoing, com.example.mytodoapp.data.TodoStatus.ComingUp)
            sortedStatuses.forEach { status ->
                val count = tasks.count { it.status == status }
                val label = if (status == com.example.mytodoapp.data.TodoStatus.ComingUp) "Pending" else status.label
                
                canvas.drawText("$label:", COL_SR, currentY, labelPaint)
                
                val countPaint = Paint().apply {
                    textSize = 11f
                    isFakeBoldText = true
                    color = status.color.toArgb()
                    isAntiAlias = true
                }
                canvas.drawText("$count", COL_SR + 80f, currentY, countPaint)
                currentY += 18f
            }
        }

        if (config.includeFavorites) {
            val favCount = tasks.count { it.isFavorite }
            canvas.drawText("Favorites:", COL_SR, currentY, labelPaint)
            
            val favCountPaint = Paint().apply {
                textSize = 11f
                isFakeBoldText = true
                color = Color.parseColor("#FFB300") // Polished Amber/Gold
                isAntiAlias = true
            }
            canvas.drawText("$favCount", COL_SR + 80f, currentY, favCountPaint)
            currentY += 18f
        }
    }

    private val titlePaint = Paint().apply {
        textSize = 24f
        color = Color.BLACK
        isFakeBoldText = true
    }

    private val headerPaint = Paint().apply {
        textSize = 13f
        isFakeBoldText = true
        color = Color.parseColor("#333333")
    }

    private val taskTextPaint = TextPaint().apply {
        textSize = 11f
        color = Color.BLACK
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 0.8f
    }

    private val goldPaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.FILL
    }

    private val goldStrokePaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private fun drawTitle(canvas: Canvas, title: String) {
        canvas.drawText(title.ifBlank { "Untitled Project" }, COL_SR, 55f, titlePaint)
    }

    private fun drawTableHeader(canvas: Canvas, y: Float, config: PdfConfig) {
        val paint = headerPaint
        val linePaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 1.2f }
        canvas.drawText("No.", COL_SR, y, paint)
        canvas.drawText("Task Description", COL_TASK, y, paint)
        
        var currentX = COL_TASK + getTaskWidth(config) + 15f
        if (config.includeStatus) {
            canvas.drawText("Status", currentX, y, paint)
            currentX += 105f
        }
        if (config.includeFavorites) {
            canvas.drawText("Favorite", currentX, y, paint)
        }
        
        canvas.drawLine(COL_SR, y + 8f, TABLE_WIDTH, y + 8f, linePaint)
    }

    private fun getTaskWidth(config: PdfConfig): Int {
        return when {
            !config.includeStatus && !config.includeFavorites -> 470
            !config.includeStatus && config.includeFavorites -> 430
            config.includeStatus && !config.includeFavorites -> 420
            else -> 320
        }
    }

    fun calculateRowHeight(task: TodoTask, paint: TextPaint, config: PdfConfig): Float {
        val staticLayout = StaticLayout.Builder.obtain(task.text, 0, task.text.length, paint, getTaskWidth(config))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()
        return staticLayout.height.toFloat() + 12f // vertical padding
    }

    fun drawTaskRow(canvas: Canvas, index: Int, task: TodoTask, y: Float, rowHeight: Float, config: PdfConfig) {
        val centerYOffset = (rowHeight / 2f)

        // 1. Serial Number
        canvas.drawText("${index + 1}.", COL_SR, y + centerYOffset + 4f, taskTextPaint)

        // 2. Task Description (Wrapped)
        val staticLayout = StaticLayout.Builder.obtain(task.text, 0, task.text.length, taskTextPaint, getTaskWidth(config))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()
        
        canvas.save()
        canvas.translate(COL_TASK, y + 6f)
        staticLayout.draw(canvas)
        canvas.restore()

        var currentX = COL_TASK + getTaskWidth(config) + 15f

        // 3. Status Badge
        if (config.includeStatus) {
            val statusPaint = Paint().apply {
                textSize = 10f
                isFakeBoldText = true
                color = task.status.color.toArgb()
            }
            canvas.drawText(task.status.label, currentX, y + centerYOffset + 4f, statusPaint)
            currentX += 105f
        }

        // 4. Favorite Star
        if (config.includeFavorites) {
            val starX = currentX + 12f
            val starY = y + centerYOffset
            if (task.isFavorite) {
                drawStar(canvas, starX, starY, 6f, goldPaint)
                drawStar(canvas, starX, starY, 6f, goldStrokePaint)
            } else {
                drawStar(canvas, starX, starY, 6f, goldStrokePaint)
            }
        }

        // 5. Subtle Divider
        canvas.drawLine(COL_SR, y + rowHeight, TABLE_WIDTH, y + rowHeight, linePaint)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        val outerRadius = radius
        val innerRadius = radius * 0.4f
        var angle = Math.PI / 2.0 * 3.0
        val step = Math.PI / 5.0

        path.moveTo((cx + outerRadius * Math.cos(angle)).toFloat(), (cy + outerRadius * Math.sin(angle)).toFloat())
        for (i in 1..10) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            angle += step
            path.lineTo((cx + r * Math.cos(angle)).toFloat(), (cy + r * Math.sin(angle)).toFloat())
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
