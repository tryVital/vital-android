package io.tryvital.vitalsamsunghealth


@RequiresOptIn(message = "This API is experimental. It may be changed or retracted based on customer feedback with short notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ExperimentalVitalApi
