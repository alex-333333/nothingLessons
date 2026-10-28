package io.github.alex333333.lessons.core

enum class CountdownUnit { Minutes, Hours }

/** The big number on the widget: "23" minutes, or "1:05" hours once it is an hour or more. */
data class Countdown(val text: String, val unit: CountdownUnit) {
    companion object {
        fun of(minutes: Int): Countdown =
            if (minutes < 60) {
                Countdown(minutes.toString(), CountdownUnit.Minutes)
            } else {
                Countdown("${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}", CountdownUnit.Hours)
            }
    }
}
