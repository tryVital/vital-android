package io.tryvital.vitalhealthconnect

import android.content.Context
import io.tryvital.vitalhealthcore.isConnectedToInternet as coreIsConnectedToInternet

internal val Context.isConnectedToInternet: Boolean
    get() = this.coreIsConnectedToInternet
