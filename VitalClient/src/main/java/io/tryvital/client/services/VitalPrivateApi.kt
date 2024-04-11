package io.tryvital.client.services

@RequiresOptIn(
    message = "For Vital SDK private usage. Vital offers no support and API stability on SDK private API.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class VitalPrivateApi
