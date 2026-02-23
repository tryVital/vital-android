package io.tryvital.vitalhealthcore.workers

import android.os.Build

fun foregroundServiceType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
    } else {
        0
    }
}
