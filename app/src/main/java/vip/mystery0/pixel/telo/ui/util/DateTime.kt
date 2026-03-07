package vip.mystery0.pixel.telo.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField

val backupDateTimeFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendLiteral('_')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .toFormatter()

val listDateTimeFormatter1: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendLiteral(' ')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .toFormatter()

val listDateTimeFormatter2: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendLiteral('-')
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendLiteral(' ')
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .toFormatter()

fun formatMills(mills: Long): String {
    val localDateTime = Instant.ofEpochMilli(mills)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    if (localDateTime.year == LocalDate.now().year) {
        return localDateTime.format(listDateTimeFormatter1)
    }
    return localDateTime.format(listDateTimeFormatter2)
}