package ru.rzk.schedule.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * На какой день расписание, выложенное картинкой.
 *
 * Имя файла ничего не говорит о дне («Screenshot_…jpg»), зато в самой картинке крупно написано
 * «Замены уроков на 26.10.2026 год (понедельник)». Дату читаем распознаванием текста прямо на телефоне
 * (Google ML Kit, работает без интернета). Русские слова модель не разбирает, но цифры и точки
 * в дате читаются надёжно, а нам нужна только дата.
 */
object ImageDate {
    // Распознавание иногда ставит запятую или пробелы вместо точки: «26,10.2026», «26. 10. 2026».
    private val dateRe = Regex("""(?<!\d)(\d{1,2})\s*[.,]\s*(\d{1,2})\s*[.,]\s*(20\d{2})(?!\d)""")

    /** Первая дата вида ДД.ММ.ГГГГ в тексте или null. */
    fun find(text: String): LocalDate? = dateRe.findAll(text).firstNotNullOfOrNull { m ->
        runCatching {
            LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }.getOrNull()
    }

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * Дата из шапки картинки; null — если даты в картинке нет. Если распознавание не сработало
     * (ошибка ML Kit), бросает исключение: результат «не знаю» запоминать нельзя, попробуем позже.
     */
    suspend fun read(file: File): LocalDate? = withContext(Dispatchers.Default) {
        val bitmap = decode(file) ?: return@withContext null
        try {
            // Сначала шапка, где крупный заголовок; если там даты нет — вся картинка.
            val head = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, (bitmap.height * 0.45).toInt().coerceAtLeast(1))
            val fromHead = try {
                find(recognize(head))
            } finally {
                if (head !== bitmap) head.recycle()
            }
            fromHead ?: find(recognize(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_WIDTH) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    private const val MAX_WIDTH = 2600
}
