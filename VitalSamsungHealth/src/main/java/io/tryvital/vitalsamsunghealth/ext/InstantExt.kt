package io.tryvital.vitalsamsunghealth.ext

import java.time.Instant
import java.util.Date

fun Instant.toDate(): Date {
    return Date.from(this)
}
