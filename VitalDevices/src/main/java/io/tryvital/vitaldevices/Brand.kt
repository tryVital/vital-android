package io.tryvital.vitaldevices

sealed class Brand(val name: String) {
    object Omron : Brand("Omron")
    object AccuChek : Brand("Accu-Chek")
    object Contour : Brand("Contour")
    object Beurer : Brand("Beurer")
    object Libre : Brand("FreeStyle Libre")
}