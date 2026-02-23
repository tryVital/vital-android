package io.tryvital.vitalsamsunghealth.model

import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.model.BackfillType
import io.tryvital.vitalhealthcore.model.backfillType as coreBackfillType

internal val VitalResource.backfillType: BackfillType
    get() = this.coreBackfillType
