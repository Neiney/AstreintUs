package com.example.domain.model

enum class SnoozeOption(val minutes: Int, val label: String) {
    FIVE_MINUTES(5, "5 min"),
    FIFTEEN_MINUTES(15, "15 min"),
    THIRTY_MINUTES(30, "30 min");

    val durationMillis: Long
        get() = minutes * 60 * 1000L
}
