package ru.cian.rustore.publish.utils

import java.text.SimpleDateFormat
import java.util.Locale

const val RELEASE_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZZ"

const val ONE_HOUR_IN_MILLIS: Long = 1000 * 60 * 60

const val ONE_DAY_IN_MILLIS: Long = ONE_HOUR_IN_MILLIS * 24

val SIMPLE_DATE_TIME_WITH_TIMEZONE = SimpleDateFormat(
    RELEASE_DATE_TIME_FORMAT,
    Locale.ROOT,
)
