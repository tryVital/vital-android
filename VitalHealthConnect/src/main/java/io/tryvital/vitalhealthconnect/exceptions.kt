package io.tryvital.vitalhealthconnect

import io.tryvital.vitalhealthconnect.model.VitalResource

class RecordWriteUnsupported(resource: VitalResource): Throwable(
    message = "This SDK version does not support writing $resource."
)