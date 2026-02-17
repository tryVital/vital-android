package io.tryvital.vitalsamsunghealth.ext

import io.tryvital.vitalhealthcore.ext.toDate as coreToDate
import java.time.Instant
import java.util.Date

fun Instant.toDate(): Date = this.coreToDate()
