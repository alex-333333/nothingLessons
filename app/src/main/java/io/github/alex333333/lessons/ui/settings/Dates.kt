package io.github.alex333333.lessons.ui.settings

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

// Russian date labels for the settings screen, built by hand so they never depend on the
// phone's locale.

private val shortMonths = listOf("ЯНВ", "ФЕВ", "МАР", "АПР", "МАЯ", "ИЮН", "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК")

private val genitiveMonths = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private val monthNames = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
)

/** "30 ОКТ" */
internal val LocalDate.dayMonth: String get() = "$dayOfMonth ${shortMonths[monthValue - 1]}"

/** "ПТ, 30 ОКТ" */
internal val LocalDate.shortDate: String get() = "${dayOfWeek.shortLabel}, $dayMonth"

/** "30 октября", for spoken labels and sentences. */
internal val LocalDate.spoken: String get() = "$dayOfMonth ${genitiveMonths[monthValue - 1]}"

/** "СЕГОДНЯ, 29 ОКТ", "ЗАВТРА, 30 ОКТ", otherwise "ЧТ, 12 НОЯ". */
internal fun LocalDate.relativeDate(today: LocalDate): String = when (this) {
    today -> "СЕГОДНЯ, $dayMonth"
    today.plusDays(1) -> "ЗАВТРА, $dayMonth"
    else -> shortDate
}

/** "февраль 2027" */
internal val YearMonth.title: String get() = "${monthNames[monthValue - 1]} $year"

/** "13 — 21 ФЕВ", "31 ОКТ — 8 НОЯ", or "13 ФЕВ" for a single day. */
internal fun rangeLabel(first: LocalDate, last: LocalDate): String = when {
    first == last -> first.dayMonth
    first.month == last.month && first.year == last.year -> "${first.dayOfMonth} — ${last.dayMonth}"
    else -> "${first.dayMonth} — ${last.dayMonth}"
}

/** "с 31 октября по 8 ноября" */
internal fun spokenRange(first: LocalDate, last: LocalDate): String =
    if (first == last) first.spoken else "с ${first.spoken} по ${last.spoken}"

/** Russian plural: 1 урок, 2 урока, 5 уроков, 11 уроков, 21 урок. */
internal fun plural(n: Int, one: String, few: String, many: String): String {
    val tens = n % 100
    return when {
        n % 10 == 1 && tens != 11 -> one
        n % 10 in 2..4 && tens !in 12..14 -> few
        else -> many
    }
}

/** "9 ДНЕЙ" */
internal fun daysLabel(n: Int): String = "$n ${plural(n, "ДЕНЬ", "ДНЯ", "ДНЕЙ")}"

/** "5 уроков" */
internal fun lessonsLabel(n: Int): String = "$n ${plural(n, "урок", "урока", "уроков")}"

/** "8:00", without the leading zero, for sentences. */
internal fun LocalTime.hm(): String = "$hour:${minute.toString().padStart(2, '0')}"
