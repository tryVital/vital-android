package io.tryvital.vitalhealthconnect

import android.app.Notification
import android.content.Context
import io.tryvital.vitalhealthconnect.model.VitalResource

interface SyncNotificationBuilder {
    fun build(context: Context, resource: VitalResource): Notification
}
