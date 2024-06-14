package io.tryvital.vitaldevices

import io.tryvital.client.services.data.ManualProviderSlug

sealed class Brand(val name: String) {
    object Omron : Brand("Omron")
    object AccuChek : Brand("Accu-Chek")
    object Contour : Brand("Contour")
    object Beurer : Brand("Beurer")
    object Libre : Brand("FreeStyle Libre")

    fun toManualProviderSlug(): ManualProviderSlug = when (this) {
        is Omron -> ManualProviderSlug.OmronBLE
        is AccuChek -> ManualProviderSlug.AccuchekBLE
        is Contour -> ManualProviderSlug.ContourBLE
        is Beurer -> ManualProviderSlug.BeurerBLE
        is Libre -> ManualProviderSlug.LibreBLE
    }
}