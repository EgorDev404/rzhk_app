package ru.rzk.schedule.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class SeedPreset(val name: String, val argb: Int)

/** Готовые цвета в карточке «Оформление». Первый — цвет приложения по умолчанию. */
val SeedPresets: List<SeedPreset> = listOf(
    SeedPreset("Синий", 0xFF3A5BA9.toInt()),
    SeedPreset("Фиолетовый", 0xFF6750A4.toInt()),
    SeedPreset("Розовый", 0xFFB5446E.toInt()),
    SeedPreset("Коралловый", 0xFFD6493F.toInt()),
    SeedPreset("Янтарный", 0xFFC77700.toInt()),
    SeedPreset("Зелёный", 0xFF3B8A4F.toInt()),
    SeedPreset("Бирюзовый", 0xFF0F8A8A.toInt()),
)

/**
 * Палитра Material You из одного выбранного цвета, без внешних библиотек.
 *
 * Как у Google: из исходного цвета берутся оттенок и насыщенность, из них строятся «тональные палитры»
 * (основная, вторичная, третичная, нейтральные), а роли цветов берутся с фиксированных «тонов»
 * (светлота от 0 до 100). Например, основной цвет в светлой теме — тон 40, а текст на нём — тон 100.
 * Благодаря этому читаемость текста гарантирована для любого выбранного цвета.
 *
 * Тон здесь — это светлота по CIE L*, то есть настоящая яркость цвета. Оттенок и насыщенность считаются
 * в пространстве OKLCH. Для каждого тона подбирается цвет нужной яркости; если такого цвета с желаемой
 * насыщенностью не существует на экране, насыщенность уменьшается ровно настолько, насколько нужно.
 */
object SeedColors {
    fun scheme(seedArgb: Int, dark: Boolean): ColorScheme {
        val seed = Color(seedArgb)
        val lab = toOklab(seed.red.toDouble(), seed.green.toDouble(), seed.blue.toDouble())
        val hue = (Math.toDegrees(atan2(lab[2], lab[1])) + 360.0) % 360.0
        val chroma = hypot(lab[1], lab[2])
        // Почти серый исходный цвет (белый, чёрный, серый) даёт нейтральную схему без случайного оттенка.
        val primaryChroma = if (chroma < 0.02) chroma else max(0.12, chroma)

        fun primary(t: Int) = tone(hue, primaryChroma, t)
        fun secondary(t: Int) = tone(hue, min(0.04, primaryChroma), t)
        fun tertiary(t: Int) = tone((hue + 60.0) % 360.0, min(0.06, primaryChroma), t)
        fun neutral(t: Int) = tone(hue, min(0.010, primaryChroma), t)
        fun neutralVariant(t: Int) = tone(hue, min(0.020, primaryChroma), t)
        fun error(t: Int) = tone(29.0, 0.19, t)

        return if (dark) {
            darkColorScheme(
                primary = primary(80), onPrimary = primary(20),
                primaryContainer = primary(30), onPrimaryContainer = primary(90),
                inversePrimary = primary(40),
                secondary = secondary(80), onSecondary = secondary(20),
                secondaryContainer = secondary(30), onSecondaryContainer = secondary(90),
                tertiary = tertiary(80), onTertiary = tertiary(20),
                tertiaryContainer = tertiary(30), onTertiaryContainer = tertiary(90),
                error = error(80), onError = error(20),
                errorContainer = error(30), onErrorContainer = error(90),
                background = neutral(6), onBackground = neutral(90),
                surface = neutral(6), onSurface = neutral(90),
                surfaceVariant = neutralVariant(30), onSurfaceVariant = neutralVariant(80),
                surfaceTint = primary(80),
                inverseSurface = neutral(90), inverseOnSurface = neutral(20),
                outline = neutralVariant(60), outlineVariant = neutralVariant(30),
                scrim = Color.Black,
                surfaceDim = neutral(6), surfaceBright = neutral(24),
                surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10),
                surfaceContainer = neutral(12), surfaceContainerHigh = neutral(17),
                surfaceContainerHighest = neutral(22),
            )
        } else {
            lightColorScheme(
                primary = primary(40), onPrimary = primary(100),
                primaryContainer = primary(90), onPrimaryContainer = primary(10),
                inversePrimary = primary(80),
                secondary = secondary(40), onSecondary = secondary(100),
                secondaryContainer = secondary(90), onSecondaryContainer = secondary(10),
                tertiary = tertiary(40), onTertiary = tertiary(100),
                tertiaryContainer = tertiary(90), onTertiaryContainer = tertiary(10),
                error = error(40), onError = error(100),
                errorContainer = error(90), onErrorContainer = error(10),
                background = neutral(98), onBackground = neutral(10),
                surface = neutral(98), onSurface = neutral(10),
                surfaceVariant = neutralVariant(90), onSurfaceVariant = neutralVariant(30),
                surfaceTint = primary(40),
                inverseSurface = neutral(20), inverseOnSurface = neutral(95),
                outline = neutralVariant(50), outlineVariant = neutralVariant(80),
                scrim = Color.Black,
                surfaceDim = neutral(87), surfaceBright = neutral(98),
                surfaceContainerLowest = neutral(100), surfaceContainerLow = neutral(96),
                surfaceContainer = neutral(94), surfaceContainerHigh = neutral(92),
                surfaceContainerHighest = neutral(90),
            )
        }
    }

    // ---- математика цвета ----------------------------------------------------------------------

    /** Цвет с заданными оттенком (градусы), насыщенностью (OKLCH) и тоном (0..100). */
    private fun tone(hueDeg: Double, chroma: Double, tone: Int): Color {
        if (tone <= 0) return Color.Black
        if (tone >= 100) return Color.White
        val hue = Math.toRadians(hueDeg)
        val targetY = luminanceOfTone(tone.toDouble())
        var rgb = solve(hue, chroma, targetY)
        if (!inGamut(rgb)) {
            // Такой яркости при такой насыщенности на экране не бывает: ищем максимальную насыщенность, которая влезает.
            var lo = 0.0
            var hi = chroma
            repeat(18) {
                val mid = (lo + hi) / 2
                if (inGamut(solve(hue, mid, targetY))) lo = mid else hi = mid
            }
            rgb = solve(hue, lo, targetY)
        }
        return Color(encode(rgb[0]).toFloat(), encode(rgb[1]).toFloat(), encode(rgb[2]).toFloat())
    }

    private fun luminanceOfTone(t: Double): Double =
        if (t > 8.0) ((t + 16.0) / 116.0).pow(3) else t / 903.2963

    /** Подбирает светлоту OKLab так, чтобы яркость (Y) цвета совпала с нужной. */
    private fun solve(hue: Double, chroma: Double, targetY: Double): DoubleArray {
        val a = chroma * cos(hue)
        val b = chroma * sin(hue)
        var lo = 0.0
        var hi = 1.0
        repeat(26) {
            val mid = (lo + hi) / 2
            if (luminance(fromOklab(mid, a, b)) < targetY) lo = mid else hi = mid
        }
        return fromOklab((lo + hi) / 2, a, b)
    }

    private fun inGamut(rgb: DoubleArray): Boolean = rgb.all { it > -0.0005 && it < 1.0005 }

    private fun luminance(rgb: DoubleArray): Double = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]

    private fun linear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun encode(c: Double): Double {
        val v = c.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
    }

    /** sRGB (0..1) → OKLab [L, a, b]. */
    private fun toOklab(r: Double, g: Double, b: Double): DoubleArray {
        val lr = linear(r)
        val lg = linear(g)
        val lb = linear(b)
        val l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
        val m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
        val s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /** OKLab → линейный RGB (может выходить за 0..1, если цвет вне гаммы экрана). */
    private fun fromOklab(L: Double, a: Double, b: Double): DoubleArray {
        val l1 = L + 0.3963377774 * a + 0.2158037573 * b
        val m1 = L - 0.1055613458 * a - 0.0638541728 * b
        val s1 = L - 0.0894841775 * a - 1.2914855480 * b
        val l = l1 * l1 * l1
        val m = m1 * m1 * m1
        val s = s1 * s1 * s1
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }
}
