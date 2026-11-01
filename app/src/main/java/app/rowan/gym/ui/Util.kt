package app.rowan.gym.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

const val KG_TO_LB = 2.2046226218

fun toDisplay(kg: Double, lb: Boolean) = if (lb) kg * KG_TO_LB else kg
fun fromDisplay(v: Double, lb: Boolean) = if (lb) v / KG_TO_LB else v
fun unitLabel(lb: Boolean) = if (lb) "lb" else "kg"

/** "82.5", "80", never "80.0". */
fun num(v: Double, maxDec: Int = 1): String {
    val f = "%.${maxDec}f".format(Locale.US, v)
    return if (f.contains('.')) f.trimEnd('0').trimEnd('.') else f
}

fun fmtWeight(kg: Double, lb: Boolean) = num(toDisplay(kg, lb))

fun fmtBig(v: Double, lb: Boolean = false): String {
    val x = toDisplay(v, lb)
    return when {
        x >= 100_000 -> num(x / 1000, 0) + "k"
        x >= 10_000 -> num(x / 1000, 1) + "k"
        else -> "%,d".format(Locale.getDefault(), x.toInt())
    }
}

fun fmtClock(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

fun fmtDuration(sec: Int): String = if (sec >= 3600) "%dh %02dm".format(sec / 3600, (sec % 3600) / 60) else "%d min".format(maxOf(1, sec / 60))

private fun loc() = Locale.getDefault()

fun fmtDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE d MMM", loc())).replaceFirstChar { it.uppercase() }
fun fmtDateLong(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", loc())).replaceFirstChar { it.uppercase() }
fun fmtDateShort(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", loc()))
fun fmtMonth(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("LLLL yyyy", loc())).replaceFirstChar { it.uppercase() }
fun fmtMonthShort(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, loc()).replaceFirstChar { it.uppercase() }
fun weekdayShort(isoDow: Int): String =
    java.time.DayOfWeek.of(isoDow).getDisplayName(TextStyle.SHORT, loc()).replaceFirstChar { it.uppercase() }
fun weekdayNarrow(isoDow: Int): String =
    java.time.DayOfWeek.of(isoDow).getDisplayName(TextStyle.NARROW, loc()).uppercase()

fun currentLang(): String = if (Locale.getDefault().language == "it") "it" else "en"

@Suppress("DEPRECATION")
fun buzz(ctx: Context, ms: Long = 18, strong: Boolean = false) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(ms, if (strong) 255 else 90))
    }
}

fun buzzPattern(ctx: Context, pattern: LongArray) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        @Suppress("DEPRECATION") v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
