package io.tryvital.vitalsamsunghealth.exceptions

import io.tryvital.vitalsamsunghealth.model.VitalResource

class RecordWriteUnsupported(resource: VitalResource): Throwable(
    message = "This SDK version does not support writing $resource."
)

class ConnectionPaused: Throwable()
class ConnectionDestroyed: Throwable()
