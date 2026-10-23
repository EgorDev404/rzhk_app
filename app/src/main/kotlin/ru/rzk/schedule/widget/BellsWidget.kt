package ru.rzk.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import ru.rzk.schedule.MainActivity
import ru.rzk.schedule.R
import ru.rzk.schedule.data.BellSchedule
import ru.rzk.schedule.data.BellState
import ru.rzk.schedule.data.Dates
import ru.rzk.schedule.ui.theme.WidgetColors
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.roundToInt

class BellsWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            WidgetAlarm.ACTION_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> BellsWidgetUpdater.refreshAll(context)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetAlarm.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetAlarm.cancel(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BellsWidgetUpdater.refresh(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        BellsWidgetUpdater.refresh(context, manager, intArrayOf(id))
    }

    /** Три раскладки: что показываем, определяется РЕАЛЬНЫМИ шириной и высотой виджета (в dp). */
    internal enum class Variant { Strip, Compact, Full }

    /** Тексты и флаги для текущего состояния звонков (без привязки к размеру). */
    internal class Content(
        val chip: String,
        val big: String,
        val label: String,
        val statLeftLabel: String = "",
        val statLeftValue: String = "",
        val statRightLabel: String = "",
        val statRightValue: String = "",
        val footer: String,
        val hasStats: Boolean,
        val showRing: Boolean,
        val progress: Float,
    )

    /** Размеры одного экземпляра виджета в dp + пересчёт в пиксели. */
    internal class Metrics(context: Context, size: SizeF) {
        val density: Float = context.resources.displayMetrics.density
        val width: Float = size.width
        val height: Float = size.height

        /** Горизонтальный (и для Full — общий) отступ из ресурсов: 12dp на старых, 16dp на Android 12+. */
        val pad: Float = context.resources.getDimension(R.dimen.widget_padding) / density
        val contentWidth: Float get() = max(0f, width - 2 * pad)
    }

    private class Palette(val container: Int, val on: Int)

    companion object {
        // ---- пороги раскладок, dp --------------------------------------------------------
        private const val DEFAULT_WIDTH_DP = 250f
        private const val DEFAULT_HEIGHT_DP = 110f

        /** RemoteViews(Map) на Android 12+ принимает не больше 16 размеров. */
        private const val MAX_SIZES = 16

        /** Ниже этой высоты — однострочная полоса. */
        private const val STRIP_MAX_HEIGHT = 100f

        /** Full: нужна ширина, чтобы плитки «Начался/Закончится» не обрезались. */
        private const val FULL_MIN_WIDTH = 230f

        /** Compact: кольцо рисуем, только если рядом с ним остаётся место под текст. */
        private const val COMPACT_RING_MIN_WIDTH = 190f

        /** Strip: подпись справа показываем только на достаточно широком виджете. */
        private const val STRIP_LABEL_MIN_WIDTH = 200f

        // ---- фиксированные размеры элементов (должны совпадать с layout), dp --------------
        private const val CHIP_HEIGHT = 24f
        private const val FULL_RING = 56f
        private const val FULL_RING_GAP = 12f
        private const val FULL_ROW_GAP = 8f
        private const val FULL_STATS_HEIGHT = 48f // 6 margin + 2*6 padding + подпись 10sp + значение 15sp
        private const val FULL_FOOTER_HEIGHT = 31f // 4 margin + 2 строки по 11sp
        private const val COMPACT_PAD_V = 12f
        private const val COMPACT_RING = 40f
        private const val COMPACT_RING_GAP = 10f
        private const val COMPACT_ROW_GAP = 6f
        private const val COMPACT_BAR_SPACE = 10f // 6 margin + 4 высота
        private const val COMPACT_BAR_HEIGHT = 4f
        private const val LABEL_HEIGHT = 15f // подпись 12sp
        private const val LINE_HEIGHT_FACTOR = 1.25f
        private const val TEXT_SAFETY = 1.06f // запас на разницу шрифтов (tnum, MiSans/Roboto)

        /**
         * Собирает RemoteViews для виджета с параметрами [options].
         *
         * Android 12+: отдаём набор раскладок «размер → RemoteViews», и система сама мгновенно выбирает
         * подходящую при изменении размера (без рассинхронизации и «съехавшего» контента).
         * Android 8–11: берём один размер, рассчитанный с учётом ориентации.
         */
        fun buildViews(context: Context, options: Bundle): RemoteViews {
            val now = LocalDateTime.now(Dates.zone)
            val state = BellSchedule.state(now, BellsWidgetUpdater.lessonCount(context))
            val content = contentFor(state)
            val palette = paletteFor(context, state)
            val sizes = resolveSizes(context, options)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
                return RemoteViews(sizes.associateWith { render(context, content, palette, it) })
            }
            return render(context, content, palette, sizes.first())
        }

        @Suppress("DEPRECATION")
        private fun resolveSizes(context: Context, options: Bundle): List<SizeF> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val list = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                if (!list.isNullOrEmpty()) return list.distinct().take(MAX_SIZES)
            }
            // До Android 12: в портрете ширина = MIN_WIDTH, высота = MAX_HEIGHT; в ландшафте — наоборот.
            // Раньше везде брался MIN_HEIGHT (высота в ландшафте), из-за этого в портрете виджет считался ниже, чем есть.
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val w = options.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            )
            val h = options.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            )
            return listOf(
                SizeF(
                    if (w > 0) w.toFloat() else DEFAULT_WIDTH_DP,
                    if (h > 0) h.toFloat() else DEFAULT_HEIGHT_DP,
                ),
            )
        }

        // ---- выбор раскладки ----------------------------------------------------------------

        private fun fullMinHeight(m: Metrics, c: Content): Float =
            2 * m.pad + CHIP_HEIGHT + FULL_ROW_GAP + FULL_RING + if (c.hasStats) FULL_STATS_HEIGHT else 0f

        private fun variantFor(m: Metrics, c: Content): Variant = when {
            m.height < STRIP_MAX_HEIGHT -> Variant.Strip
            m.width >= FULL_MIN_WIDTH && m.height >= fullMinHeight(m, c) -> Variant.Full
            else -> Variant.Compact
        }

        private fun render(context: Context, c: Content, p: Palette, size: SizeF): RemoteViews {
            val m = Metrics(context, size)
            val views = when (variantFor(m, c)) {
                Variant.Strip -> renderStrip(context, c, m, p)
                Variant.Compact -> renderCompact(context, c, m, p)
                Variant.Full -> renderFull(context, c, m, p)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_BELLS)
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_bg, pending)
            return views
        }

        // ---- Strip -------------------------------------------------------------------------

        private fun renderStrip(context: Context, c: Content, m: Metrics, p: Palette): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bells_strip)
            val showLabel = m.width >= STRIP_LABEL_MIN_WIDTH && c.label.isNotEmpty()
            // Крупный текст не должен вытеснить подпись: при её наличии отдаём ему максимум 60% ширины.
            val bigWidth = if (showLabel) m.contentWidth * 0.6f else m.contentWidth

            views.setTextViewText(R.id.widget_big, c.big)
            views.setTextViewTextSize(
                R.id.widget_big, TypedValue.COMPLEX_UNIT_SP,
                fitTextSp(context, c.big, bigWidth, maxSp = 22f, minSp = 13f),
            )
            views.setTextViewText(R.id.widget_big_label, c.label)
            views.setViewVisibility(R.id.widget_big_label, if (showLabel) View.VISIBLE else View.GONE)

            views.tint(R.id.widget_bg, p.container)
            views.setTextColor(R.id.widget_big, p.on)
            views.setTextColor(R.id.widget_big_label, WidgetColors.withAlpha(p.on, 0.8f))
            return views
        }

        // ---- Compact -----------------------------------------------------------------------

        private fun renderCompact(context: Context, c: Content, m: Metrics, p: Palette): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bells_medium)

            val heightWithoutRow = 2 * COMPACT_PAD_V + CHIP_HEIGHT + COMPACT_ROW_GAP
            val ringShown = c.showRing && m.width >= COMPACT_RING_MIN_WIDTH && m.height - heightWithoutRow >= COMPACT_RING
            val barShown = c.showRing && !ringShown

            val rowHeight = m.height - heightWithoutRow - if (barShown) COMPACT_BAR_SPACE else 0f
            val textWidth = m.contentWidth - if (ringShown) COMPACT_RING + COMPACT_RING_GAP else 0f
            val bigMaxSp = ((rowHeight - LABEL_HEIGHT) / LINE_HEIGHT_FACTOR).coerceIn(14f, 24f)

            views.setTextViewText(R.id.widget_chip, c.chip)
            views.setTextViewText(R.id.widget_big, c.big)
            views.setTextViewTextSize(
                R.id.widget_big, TypedValue.COMPLEX_UNIT_SP,
                fitTextSp(context, c.big, textWidth, maxSp = bigMaxSp, minSp = 14f),
            )
            views.setTextViewText(R.id.widget_big_label, c.label)

            views.setViewVisibility(R.id.widget_ring, if (ringShown) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_bar, if (barShown) View.VISIBLE else View.GONE)

            val track = WidgetColors.withAlpha(p.on, 0.14f)
            if (ringShown) {
                views.setTextViewText(R.id.widget_ring_percent, percent(c.progress))
                views.setImageViewBitmap(
                    R.id.widget_ring_image,
                    renderRing(context, COMPACT_RING, c.progress, track, p.on),
                )
            }
            if (barShown) {
                views.setImageViewBitmap(
                    R.id.widget_bar,
                    renderBar(context, m.contentWidth, COMPACT_BAR_HEIGHT, c.progress, track, p.on),
                )
            }

            applyChipColors(views, p)
            views.tint(R.id.widget_bg, p.container)
            views.setTextColor(R.id.widget_big, p.on)
            views.setTextColor(R.id.widget_big_label, WidgetColors.withAlpha(p.on, 0.8f))
            views.setTextColor(R.id.widget_ring_percent, p.on)
            return views
        }

        // ---- Full --------------------------------------------------------------------------

        private fun renderFull(context: Context, c: Content, m: Metrics, p: Palette): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bells_large)

            val ringShown = c.showRing
            val textWidth = m.contentWidth - if (ringShown) FULL_RING + FULL_RING_GAP else 0f
            val showFooter = m.height >= fullMinHeight(m, c) + FULL_FOOTER_HEIGHT

            views.setTextViewText(R.id.widget_chip, c.chip)
            views.setTextViewText(R.id.widget_big, c.big)
            views.setTextViewTextSize(
                R.id.widget_big, TypedValue.COMPLEX_UNIT_SP,
                fitTextSp(context, c.big, textWidth, maxSp = 28f, minSp = 16f),
            )
            views.setTextViewText(R.id.widget_big_label, c.label)

            views.setViewVisibility(R.id.widget_ring, if (ringShown) View.VISIBLE else View.GONE)
            if (ringShown) {
                views.setTextViewText(R.id.widget_ring_percent, percent(c.progress))
                views.setImageViewBitmap(
                    R.id.widget_ring_image,
                    renderRing(context, FULL_RING, c.progress, WidgetColors.withAlpha(p.on, 0.14f), p.on),
                )
            }

            views.setViewVisibility(R.id.widget_stats, if (c.hasStats) View.VISIBLE else View.GONE)
            if (c.hasStats) {
                views.setTextViewText(R.id.widget_stat_left_label, c.statLeftLabel)
                views.setTextViewText(R.id.widget_stat_left_value, c.statLeftValue)
                views.setTextViewText(R.id.widget_stat_right_label, c.statRightLabel)
                views.setTextViewText(R.id.widget_stat_right_value, c.statRightValue)
                views.setViewVisibility(
                    R.id.widget_stat_right,
                    if (c.statRightValue.isEmpty()) View.GONE else View.VISIBLE,
                )
            }

            views.setTextViewText(R.id.widget_footer, c.footer)
            views.setViewVisibility(R.id.widget_footer, if (showFooter) View.VISIBLE else View.GONE)

            applyChipColors(views, p)
            views.tint(R.id.widget_bg, p.container)
            views.setTextColor(R.id.widget_big, p.on)
            views.setTextColor(R.id.widget_big_label, WidgetColors.withAlpha(p.on, 0.8f))
            views.setTextColor(R.id.widget_footer, WidgetColors.withAlpha(p.on, 0.85f))
            views.setTextColor(R.id.widget_ring_percent, p.on)

            val pill = WidgetColors.withAlpha(p.on, 0.10f)
            views.tint(R.id.widget_stat_left_bg, pill)
            views.tint(R.id.widget_stat_right_bg, pill)
            val statLabel = WidgetColors.withAlpha(p.on, 0.75f)
            views.setTextColor(R.id.widget_stat_left_label, statLabel)
            views.setTextColor(R.id.widget_stat_right_label, statLabel)
            views.setTextColor(R.id.widget_stat_left_value, p.on)
            views.setTextColor(R.id.widget_stat_right_value, p.on)
            return views
        }

        // ---- общие помощники ---------------------------------------------------------------

        private fun applyChipColors(views: RemoteViews, p: Palette) {
            views.tint(R.id.widget_chip_bg, WidgetColors.withAlpha(p.on, 0.12f))
            views.tint(R.id.widget_chip_icon, p.on)
            views.setTextColor(R.id.widget_chip, p.on)
        }

        /**
         * Красит белый ImageView цветом [color] с учётом его альфы. Работает на любой версии Android:
         * `setColorStateList` у RemoteViews появился только в API 31, а minSdk у проекта — 26.
         */
        private fun RemoteViews.tint(viewId: Int, color: Int) {
            val opaque = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
            setInt(viewId, "setColorFilter", opaque)
            setInt(viewId, "setImageAlpha", Color.alpha(color))
        }

        private fun percent(progress: Float): String = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"

        /**
         * Подбирает наибольший размер шрифта (sp) от [maxSp] до [minSp], при котором [text] помещается в [availableDp].
         * Вместо autoSize: у TextView с autoSize и wrap_content в RemoteViews при смене размера высота остаётся
         * от прошлого размера, из-за чего текст обрезался или налезал на соседний.
         */
        private fun fitTextSp(context: Context, text: String, availableDp: Float, maxSp: Float, minSp: Float): Float {
            val dm = context.resources.displayMetrics
            val availablePx = availableDp * dm.density
            if (availablePx <= 0f || text.isEmpty()) return minSp

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
            var sp = maxSp
            while (sp > minSp) {
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
                if (paint.measureText(text) * TEXT_SAFETY <= availablePx) return sp
                sp -= 1f
            }
            return minSp
        }

        /** Кольцо рисуется сразу в нужном пиксельном размере (раньше — 140px, растягивалось под 64dp). */
        private fun renderRing(context: Context, sizeDp: Float, progress: Float, trackColor: Int, progressColor: Int): Bitmap {
            val px = (sizeDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
            val stroke = px * 0.10f
            val inset = stroke / 2f
            val rect = RectF(inset, inset, px - inset, px - inset)

            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            paint.color = trackColor
            canvas.drawArc(rect, 0f, 360f, false, paint)

            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                paint.color = progressColor
                canvas.drawArc(rect, -90f, sweep, false, paint)
            }
            return bmp
        }

        /** Горизонтальная полоска прогресса точно под ширину виджета — без растяжения и искажения скруглений. */
        private fun renderBar(
            context: Context,
            widthDp: Float,
            heightDp: Float,
            progress: Float,
            trackColor: Int,
            progressColor: Int,
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val w = (widthDp * density).roundToInt().coerceAtLeast(1)
            val h = (heightDp * density).roundToInt().coerceAtLeast(1)
            val r = h / 2f

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            paint.color = trackColor
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)

            val fill = progress.coerceIn(0f, 1f)
            if (fill > 0f) {
                paint.color = progressColor
                val right = max(h.toFloat(), w * fill) // не меньше высоты, чтобы скругление не «ломалось»
                canvas.drawRoundRect(RectF(0f, 0f, right, h.toFloat()), r, r, paint)
            }
            return bmp
        }

        // ---- состояние → тексты ------------------------------------------------------------

        private fun paletteFor(context: Context, state: BellState): Palette {
            val c = WidgetColors.load(context)
            return when (state) {
                is BellState.InLesson -> Palette(c.primaryContainer, c.onPrimaryContainer)
                is BellState.InBreak -> Palette(c.tertiaryContainer, c.onTertiaryContainer)
                is BellState.BeforeClasses -> Palette(c.secondaryContainer, c.onSecondaryContainer)
                else -> Palette(c.primaryContainer, c.onPrimaryContainer)
            }
        }

        private fun contentFor(state: BellState): Content = when (state) {
            is BellState.InLesson -> Content(
                chip = "Идёт ${state.bell.number} урок",
                big = BellSchedule.remainingLabel(state.remaining),
                label = "до конца урока",
                statLeftLabel = "Начался",
                statLeftValue = BellSchedule.hm(state.bell.start),
                statRightLabel = "Закончится",
                statRightValue = BellSchedule.hm(state.bell.end),
                footer = state.next?.let {
                    "Дальше: перемена ${BellSchedule.minutes(state.breakAfter!!)}, затем ${it.number} урок в ${BellSchedule.hm(it.start)}"
                } ?: "Это последний урок на сегодня",
                hasStats = true,
                showRing = true,
                progress = state.progress,
            )
            is BellState.InBreak -> Content(
                chip = "Перемена",
                big = BellSchedule.remainingLabel(state.remaining),
                label = "до начала ${state.next.number} урока",
                statLeftLabel = "Урок ${state.after.number} закончился",
                statLeftValue = BellSchedule.hm(state.after.end),
                statRightLabel = "Урок ${state.next.number} начнётся",
                statRightValue = BellSchedule.hm(state.next.start),
                footer = "Перемена длится ${BellSchedule.minutes(state.total)}",
                hasStats = true,
                showRing = true,
                progress = state.progress,
            )
            is BellState.BeforeClasses -> Content(
                chip = "Занятия ещё не начались",
                big = BellSchedule.remainingLabel(state.remaining),
                label = "до начала ${state.first.number} урока",
                statLeftLabel = "Начало занятий",
                statLeftValue = BellSchedule.hm(state.first.start),
                footer = "${state.first.number} урок: ${BellSchedule.range(state.first)}",
                hasStats = true,
                showRing = false,
                progress = 0f,
            )
            is BellState.Finished -> Content(
                chip = "Занятия закончились",
                big = "На сегодня всё",
                label = "Последний урок закончился в ${BellSchedule.hm(state.last.end)}",
                footer = (if (state.tomorrowIsMonday) "В понедельник" else "Завтра") +
                    " занятия начнутся в ${BellSchedule.hm(state.firstTomorrow)}",
                hasStats = false,
                showRing = false,
                progress = 1f,
            )
            is BellState.DayOff -> Content(
                chip = "Воскресенье",
                big = "Выходной",
                label = "Звонков сегодня нет",
                footer = "В понедельник занятия начнутся в ${BellSchedule.hm(state.nextStart)}",
                hasStats = false,
                showRing = false,
                progress = 1f,
            )
        }
    }
}
