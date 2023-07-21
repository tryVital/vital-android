package io.tryvital.vitalhealthconnect.ext

import java.time.Instant
import java.util.Date

fun Instant.toDate(): Date {
    return Date.from(this)
}
