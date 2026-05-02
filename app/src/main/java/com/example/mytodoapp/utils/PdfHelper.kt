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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mytodoapp.data.TodoTask
import java.io.FileOutputStream

object PdfHelper {

    fun generateTodoPdf(context: Context, uri: Uri, title: String, tasks: List<TodoTask>) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // --- Professional Paints Setup ---
        val titlePaint = Paint().apply {
            textSize = 24f
            color = Color.BLACK
            isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            textSize = 13f
            isFakeBoldText = true
            color = Color.parseColor("#333333")
        }
        val taskTextPaint = TextPaint().apply {
            textSize = 11f
            color = Color.BLACK
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            strokeWidth = 0.8f
        }
        val goldPaint = Paint().apply {
            color = Color.parseColor("#FFD700")
            style = Paint.Style.FILL
        }
        val goldStrokePaint = Paint().apply {
            color = Color.parseColor("#FFD700")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        // Column X positions
        val colSr = 40f
        val colTask = 75f
        val colStatus = 410f
        val colFav = 515f
        val taskWidth = 320 // Increased width to utilize space better
        val tableWidth = 555f

        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Title
        canvas.drawText(title.ifBlank { "Untitled Project" }, colSr, 55f, titlePaint)

        var y = 90f
        drawTableHeader(canvas, colSr, colTask, colStatus, colFav, y, headerPaint, tableWidth)

        // Tight padding below header
        y += 20f

        tasks.filter { it.text.isNotBlank() }.forEachIndexed { index, task ->
            // 1. Calculate precise text height
            val staticLayout = StaticLayout.Builder.obtain(task.text, 0, task.text.length, taskTextPaint, taskWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f) // Slight line spacing for readability
                .build()

            // Row height is the text height plus small professional padding
            val verticalPadding = 12f
            val contentHeight = staticLayout.height.toFloat()
            val totalRowHeight = contentHeight + verticalPadding

            // 2. Multi-page Check
            if (y + totalRowHeight > 800f) {
                pdfDocument.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
                drawTableHeader(canvas, colSr, colTask, colStatus, colFav, y, headerPaint, tableWidth)
                y += 20f
            }

            // --- DRAWING THE ROW ---
            val centerYOffset = (totalRowHeight / 2f)

            // 3. Serial Number (Vertically Centered)
            canvas.drawText("${index + 1}.", colSr, y + centerYOffset + 4f, taskTextPaint)

            // 4. Task Description (Wrapped)
            canvas.save()
            canvas.translate(colTask, y + (verticalPadding / 2f))
            staticLayout.draw(canvas)
            canvas.restore()

            // 5. Status Badge (Vertically Centered)
            val statusColorStr = when (task.status.label) {
                "Done" -> "#2E7D32"     // Darker Green for PDF
                "Ongoing" -> "#1565C0"  // Darker Blue for PDF
                else -> "#C62828"       // Darker Red for PDF
            }
            val statusPaint = Paint().apply {
                textSize = 10f
                isFakeBoldText = true
                color = Color.parseColor(statusColorStr)
            }
            canvas.drawText(task.status.label, colStatus, y + centerYOffset + 4f, statusPaint)

            // 6. Favorite Star (Vertically Centered)
            val starX = colFav + 12f
            val starY = y + centerYOffset
            if (task.isFavorite) {
                drawStar(canvas, starX, starY, 6f, goldPaint)
                drawStar(canvas, starX, starY, 6f, goldStrokePaint)
            } else {
                drawStar(canvas, starX, starY, 6f, goldStrokePaint)
            }

            // 7. Increment Y and Draw Subtle Divider
            y += totalRowHeight
            canvas.drawLine(colSr, y, tableWidth, y, linePaint)

            // Small gap before next row
            y += 2f
        }

        pdfDocument.finishPage(page)

        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { pdfDocument.writeTo(it) }
            }
        } catch (e: Exception) { e.printStackTrace() } finally { pdfDocument.close() }
    }

    private fun drawTableHeader(canvas: Canvas, colSr: Float, colTask: Float, colStatus: Float, colFav: Float, y: Float, paint: Paint, tableWidth: Float) {
        val linePaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 1.2f }
        canvas.drawText("No.", colSr, y, paint)
        canvas.drawText("Task Description", colTask, y, paint)
        canvas.drawText("Status", colStatus, y, paint)
        canvas.drawText("Fav", colFav, y, paint)
        canvas.drawLine(colSr, y + 8f, tableWidth, y + 8f, linePaint)
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

    // --- Preview Dialog code remains the same as previous (already uses weight) ---
    @Composable
    fun PdfPreviewDialog(title: String, tasks: List<TodoTask>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.Companion.padding(24.dp)) {
                    Text(
                        "Document Preview",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Companion.ExtraBold
                    )
                    Spacer(Modifier.Companion.height(16.dp))

                    Row(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            "Task",
                            Modifier.Companion.weight(1f),
                            fontWeight = FontWeight.Companion.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            "Status",
                            Modifier.Companion.width(70.dp),
                            fontWeight = FontWeight.Companion.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            "Fav",
                            Modifier.Companion.width(30.dp),
                            fontWeight = FontWeight.Companion.Bold,
                            fontSize = 12.sp
                        )
                    }

                    LazyColumn(modifier = Modifier.Companion.weight(1f)) {
                        items(tasks.filter { it.text.isNotBlank() }) { task ->
                            Row(
                                modifier = Modifier.Companion.padding(
                                    vertical = 10.dp,
                                    horizontal = 8.dp
                                ),
                                verticalAlignment = Alignment.Companion.CenterVertically
                            ) {
                                Text(
                                    task.text,
                                    modifier = Modifier.Companion.weight(1f),
                                    fontSize = 14.sp
                                )
                                Text(
                                    task.status.label,
                                    color = task.status.color,
                                    modifier = Modifier.Companion.width(70.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Companion.Bold
                                )
                                Icon(
                                    imageVector = if (task.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFFFFD700),
                                    modifier = Modifier.Companion.size(16.dp)
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }

                    Spacer(Modifier.Companion.height(24.dp))
                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Go Back") }
                        Spacer(Modifier.Companion.width(8.dp))
                        Button(
                            onClick = onConfirm,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Text("Save  PDF")
                        }
                    }
                }
            }
        }
    }
}