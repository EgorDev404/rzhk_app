package ru.rzk.schedule.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Превращает PDF в JPEG-страницы средствами самого Android.
 *
 * JPEG с качеством 92: файл в несколько раз меньше PNG при почти неразличимых артефактах
 * на чёрном тексте по белому фону. Ширина 1500 px — компромисс между резкостью и весом.
 */
object PdfPages {
    private val lock = Mutex() // PdfRenderer не потокобезопасен

    private const val TARGET_WIDTH = 1500
    private const val JPEG_QUALITY = 92

    suspend fun render(pdf: File, outDir: File, targetWidth: Int = TARGET_WIDTH, maxPages: Int = 10): List<File> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val marker = File(outDir, ".done")
                if (marker.exists()) return@withLock listPages(outDir)

                outDir.deleteRecursively()
                outDir.mkdirs()
                val files = ArrayList<File>()
                val fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(fd)
                    try {
                        for (index in 0 until minOf(renderer.pageCount, maxPages)) {
                            val page = renderer.openPage(index)
                            try {
                                val scale = targetWidth.toFloat() / page.width
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE) // у PDF прозрачный фон — красим в белый
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val target = File(outDir, "page_${(index + 1).toString().padStart(2, '0')}.jpg")
                                FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                                bitmap.recycle()
                                files += target
                            } finally {
                                page.close()
                            }
                        }
                    } finally {
                        renderer.close()
                    }
                } finally {
                    fd.close()
                }
                marker.createNewFile()
                files
            }
        }

    private fun listPages(dir: File): List<File> =
        dir.listFiles { f -> f.name.startsWith("page_") }?.sortedBy { it.name }.orEmpty()
}