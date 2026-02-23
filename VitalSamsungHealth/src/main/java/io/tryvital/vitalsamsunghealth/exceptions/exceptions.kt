package io.tryvital.vitalsamsunghealth.exceptions

import io.tryvital.vitalhealthcore.model.VitalResource

class RecordWriteUnsupported(resource: VitalResource): Throwable(
    message = "This SDK version does not support writing $resource."
)
