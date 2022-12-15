package io.tryvital.vitalhealthconnect.ext

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

fun Instant.toDate(): Date {
    return Date.from(this)
}


val Instant.dayStart: Instant
    get() = truncatedTo(ChronoUnit.DAYS)