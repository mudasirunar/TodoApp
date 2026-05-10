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
import com.example.mytodoapp.data.TodoTask
import java.io.FileOutputStream

object PdfHelper {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val COL_SR = MARGIN
    private const val COL_TASK = 75f
    private const val COL_STATUS = 410f
    private const val COL_FAV = 515f
    private const val TASK_WIDTH = 320
    private const val TABLE_WIDTH = 555f

    fun generateTodoPdf(context: Context, uri: Uri, title: String, tasks: List<TodoTask>) {
        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    writePdfToStream(outputStream, title, tasks)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun writePdfToStream(outputStream: java.io.OutputStream, title: String, tasks: List<TodoTask>) {
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
        drawTableHeader(canvas, y)
        y += 20f

        validTasks.forEachIndexed { index, task ->
            val rowHeight = calculateRowHeight(task, taskTextPaint)
            
            // Multi-page Check
            if (y + rowHeight > 800f) {
                pdfDocument.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
                drawTableHeader(canvas, y)
                y += 20f
            }

            drawTaskRow(canvas, index, task, y, rowHeight)
            y += rowHeight + 2f // Add small gap
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

    private fun drawTableHeader(canvas: Canvas, y: Float) {
        val paint = headerPaint
        val linePaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 1.2f }
        canvas.drawText("No.", COL_SR, y, paint)
        canvas.drawText("Task Description", COL_TASK, y, paint)
        canvas.drawText("Status", COL_STATUS, y, paint)
        canvas.drawText("Fav", COL_FAV, y, paint)
        canvas.drawLine(COL_SR, y + 8f, TABLE_WIDTH, y + 8f, linePaint)
    }

    fun calculateRowHeight(task: TodoTask, paint: TextPaint): Float {
        val staticLayout = StaticLayout.Builder.obtain(task.text, 0, task.text.length, paint, TASK_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()
        return staticLayout.height.toFloat() + 12f // vertical padding
    }

    fun drawTaskRow(canvas: Canvas, index: Int, task: TodoTask, y: Float, rowHeight: Float) {
        val centerYOffset = (rowHeight / 2f)

        // 1. Serial Number
        canvas.drawText("${index + 1}.", COL_SR, y + centerYOffset + 4f, taskTextPaint)

        // 2. Task Description (Wrapped)
        val staticLayout = StaticLayout.Builder.obtain(task.text, 0, task.text.length, taskTextPaint, TASK_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()
        
        canvas.save()
        canvas.translate(COL_TASK, y + 6f)
        staticLayout.draw(canvas)
        canvas.restore()

        // 3. Status Badge
        val statusColorStr = when (task.status.label) {
            "Done" -> "#2E7D32"
            "Ongoing" -> "#1565C0"
            else -> "#C62828"
        }
        val statusPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = Color.parseColor(statusColorStr)
        }
        canvas.drawText(task.status.label, COL_STATUS, y + centerYOffset + 4f, statusPaint)

        // 4. Favorite Star
        val starX = COL_FAV + 12f
        val starY = y + centerYOffset
        if (task.isFavorite) {
            drawStar(canvas, starX, starY, 6f, goldPaint)
            drawStar(canvas, starX, starY, 6f, goldStrokePaint)
        } else {
            drawStar(canvas, starX, starY, 6f, goldStrokePaint)
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

    // --- Preview Helper ---
    
    data class PdfPageData(
        val title: String,
        val tasks: List<TodoTask>,
        val pageNumber: Int,
        val isFirstPage: Boolean
    )

    fun paginateTasks(title: String, tasks: List<TodoTask>): List<List<TodoTask>> {
        val pages = mutableListOf<MutableList<TodoTask>>()
        var currentTasks = mutableListOf<TodoTask>()
        var y = 90f + 20f // title + header
        
        tasks.filter { it.text.isNotBlank() }.forEach { task ->
            val rowHeight = calculateRowHeight(task, taskTextPaint)
            if (y + rowHeight > 800f) {
                pages.add(currentTasks)
                currentTasks = mutableListOf()
                y = 50f + 20f // header only on new page
            }
            currentTasks.add(task)
            y += rowHeight + 2f
        }
        if (currentTasks.isNotEmpty()) pages.add(currentTasks)
        return pages
    }

    fun drawPreviewPage(canvas: Canvas, title: String, tasks: List<TodoTask>, isFirstPage: Boolean, startIndex: Int) {
        // Clear background
        canvas.drawColor(Color.WHITE)
        
        var y = if (isFirstPage) {
            drawTitle(canvas, title)
            90f
        } else {
            50f
        }
        
        drawTableHeader(canvas, y)
        y += 20f
        
        tasks.forEachIndexed { index, task ->
            val rowHeight = calculateRowHeight(task, taskTextPaint)
            drawTaskRow(canvas, startIndex + index, task, y, rowHeight)
            y += rowHeight + 2f
        }
    }
}
