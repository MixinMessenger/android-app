package one.mixin.android.extension

import android.content.Context
import android.text.format.DateUtils
import one.mixin.android.R
import one.mixin.android.util.TimeCache
import one.mixin.android.util.language.Lingver
import one.mixin.android.util.reportException
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Instant
import org.threeten.bp.LocalTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import java.util.Date

private val LocaleZone by lazy {
    ZoneId.systemDefault()
}

private const val weekPatternEn = "E, d MMM"
private const val weekPatternCn = "MM 月 d 日 E"
private const val yearPatternEn = "E, d MMM, yyyy"
private const val yearPatternCn = "yyyy 年 MM 月 d 日 E"

fun nowInUtc() = Instant.now().toString()

fun oneWeekAgo() =
    Instant.ofEpochMilli(System.currentTimeMillis() - 60 * 60 * 1000 * 24 * 7).toString()

private const val DAY_DURATION = 24 * 3600 * 1000

fun String.date(): String {
    var date = TimeCache.singleton.getDate(this)
    if (date == null) {
        val time = ZonedDateTime.parse(this).toOffsetDateTime()
        date = time.format(DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(LocaleZone))
        TimeCache.singleton.putDate(this, date)
    }
    return date as String
}

fun String.within24Hours() = withinTime(60 * 60 * 1000 * 24)

fun String.within6Hours() = withinTime(60 * 60 * 1000 * 6)

fun String.withinTime(hours: Long): Boolean {
    val date = ZonedDateTime.parse(this).withZoneSameInstant(LocaleZone)
    val offset = System.currentTimeMillis() - date.toInstant().toEpochMilli()
    return offset < hours
}

fun String.timeAgo(context: Context): String {
    val today = ZonedDateTime.of(
        ZonedDateTime.now().toLocalDate(),
        LocalTime.MIN,
        LocaleZone.normalized()
    )
    var timeAgo = TimeCache.singleton.getTimeAgo(this + today)
    if (timeAgo == null) {
        val date = ZonedDateTime.parse(this).withZoneSameInstant(LocaleZone)
        val todayMilli = today.toInstant().toEpochMilli()
        val offset = todayMilli - date.toInstant().toEpochMilli()
        timeAgo = when {
            (todayMilli <= date.toInstant().toEpochMilli()) -> date.format(DateTimeFormatter.ofPattern("HH:mm").withZone(LocaleZone))
            (offset < 7 * DAY_DURATION) -> {
                when (date.dayOfWeek) {
                    DayOfWeek.MONDAY -> context.getString(R.string.week_monday)
                    DayOfWeek.TUESDAY -> context.getString(R.string.week_tuesday)
                    DayOfWeek.WEDNESDAY -> context.getString(R.string.week_wednesday)
                    DayOfWeek.THURSDAY -> context.getString(R.string.week_thursday)
                    DayOfWeek.FRIDAY -> context.getString(R.string.week_friday)
                    DayOfWeek.SATURDAY -> context.getString(R.string.week_saturday)
                    else -> context.getString(R.string.week_sunday)
                }
            }
            else -> {
                date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(LocaleZone))
            }
        }
        TimeCache.singleton.putTimeAgo(this, timeAgo)
    }
    return timeAgo as String
}

fun String.timeAgoDate(context: Context): String {
    val today = ZonedDateTime.of(
        ZonedDateTime.now().toLocalDate(),
        LocalTime.MIN,
        LocaleZone.normalized()
    )
    val todayMilli = today.toInstant().toEpochMilli()
    var timeAgoDate = TimeCache.singleton.getTimeAgoDate(this + today)
    if (timeAgoDate == null) {
        val date = ZonedDateTime.parse(this).withZoneSameInstant(LocaleZone)
        timeAgoDate = when {
            (todayMilli <= date.toInstant().toEpochMilli()) -> context.getString(R.string.today)
            (today.year == date.year) -> {
                date.format(
                    DateTimeFormatter.ofPattern(
                        if (Lingver.getInstance().isCurrChinese()) {
                            weekPatternCn
                        } else {
                            weekPatternEn
                        }
                    ).withZone(LocaleZone)
                )
            }
            else -> {
                date.format(
                    DateTimeFormatter.ofPattern(
                        if (Lingver.getInstance().isCurrChinese()) {
                            yearPatternCn
                        } else {
                            yearPatternEn
                        }
                    ).withZone(LocaleZone)
                )
            }
        }
        TimeCache.singleton.putTimeAgoDate(this + today, timeAgoDate)
    }
    return timeAgoDate as String
}

fun String.timeAgoDay(patten: String = "dd/MM/yyyy"): String {
    val today = ZonedDateTime.of(
        ZonedDateTime.now().toLocalDate(),
        LocalTime.MIN,
        LocaleZone.normalized()
    ).toInstant().toEpochMilli()
    var timeAgoDate = TimeCache.singleton.getTimeAgoDate(this + today)
    if (timeAgoDate == null) {
        val date = ZonedDateTime.parse(this).withZoneSameInstant(LocaleZone)
        try {
            timeAgoDate = date.format(DateTimeFormatter.ofPattern(patten).withZone(LocaleZone))
            TimeCache.singleton.putTimeAgoDate(this + today, timeAgoDate)
        } catch (e: IllegalArgumentException) {
            reportException("TimeExtension timeAgoDay()", e)
            Timber.w(e)
        }
    }
    return timeAgoDate as? String ?: ""
}

private val dateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern("yyyy/MM/dd hh:mm")
}

fun String.timeFormat(): String {
    val date = ZonedDateTime.parse(this).withZoneSameInstant(LocaleZone)
    return requireNotNull(date.format(dateTimeFormatter.withZone(LocaleZone)))
}

fun String.toUtcTime(): String {
    return ZonedDateTime.parse(this, dateTimeFormatter.withZone(LocaleZone)).toString()
}

fun String.lateOneHours(): Boolean {
    val offset = ZonedDateTime.now().toInstant().toEpochMilli() - ZonedDateTime.parse(this).withZoneSameInstant(LocaleZone).toInstant().toEpochMilli()
    return offset > 3600000L
}

fun String.hashForDate(): Long {
    var hashForDate = TimeCache.singleton.getHashForDate(this)
    if (hashForDate == null) {
        val date = ZonedDateTime.parse(this).toOffsetDateTime()
        val time = date.format(DateTimeFormatter.ofPattern("yyyMMdd").withZone(LocaleZone))
        hashForDate = time.hashCode().toLong()
        TimeCache.singleton.putHashForDate(this, hashForDate)
    }

    return hashForDate as Long
}

fun String.timeAgoClock(): String {
    var timeAgoClock = TimeCache.singleton.getTimeAgoClock(this)
    if (timeAgoClock == null) {
        val date = ZonedDateTime.parse(this).toOffsetDateTime()
        val time = date.format(DateTimeFormatter.ofPattern("HH:mm").withZone(LocaleZone))
        timeAgoClock = if (time.startsWith("0")) {
            time.substring(1)
        } else {
            time
        }
        TimeCache.singleton.putTimeAgoClock(this, timeAgoClock)
    }
    return timeAgoClock as String
}

fun isSameDay(time: String?, otherTime: String?): Boolean {
    if (time == null || otherTime == null) {
        return false
    }
    val date = time.hashForDate()
    val otherDate = otherTime.hashForDate()
    return date == otherDate
}

fun String.fullDate(): String {
    val date = ZonedDateTime.parse(this).toOffsetDateTime()
    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(LocaleZone)) as String
}

fun String.localTime(): String {
    val date = ZonedDateTime.parse(this).toOffsetDateTime()
    return date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd, hh:mm a").withZone(LocaleZone)) as String
}

fun String.dayTime(): String {
    val date = ZonedDateTime.parse(this).toOffsetDateTime()
    return date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(LocaleZone)) as String
}

fun String.createAtToLong(): Long {
    val date = ZonedDateTime.parse(this).withZoneSameInstant(LocaleZone)
    return date.toInstant().toEpochMilli()
}

fun String.getRFC3339Nano(): String {
    val date = ZonedDateTime.parse(this).toOffsetDateTime()
    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(LocaleZone))
}

fun Long.getRelativeTimeSpan(): String {
    val now = Date().time
    val time = DateUtils.getRelativeTimeSpanString(
        this,
        now,
        when {
            ((now - this) < 60000L) -> DateUtils.SECOND_IN_MILLIS
            ((now - this) < 3600000L) -> DateUtils.MINUTE_IN_MILLIS
            ((now - this) < 86400000L) -> DateUtils.HOUR_IN_MILLIS
            else -> DateUtils.DAY_IN_MILLIS
        }
    )
    return time.toString()
}

fun String.getRelativeTimeSpan(): String {
    val createTime = ZonedDateTime.parse(this).toOffsetDateTime().toEpochSecond() * 1000L
    return createTime.getRelativeTimeSpan()
}
