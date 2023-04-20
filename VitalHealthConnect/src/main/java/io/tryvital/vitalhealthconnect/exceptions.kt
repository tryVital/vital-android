package io.tryvital.vitalhealthconnect

import io.tryvital.vitalhealthconnect.model.HealthResource

class RecordWriteUnsupported(resource: HealthResource): Throwable(
    message = "This SDK version does not support writing $resource."
)