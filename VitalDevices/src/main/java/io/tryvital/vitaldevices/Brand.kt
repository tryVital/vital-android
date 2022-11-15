package io.tryvital.vitaldevices

import java.io.Serializable

sealed class Brand(val name: String): Serializable {
    object Omron : Brand("Omron")
    object AccuChek : Brand("Accu-Chek")
    object Contour : Brand("Contour")
    object Beurer : Brand("Beurer")
    object Libre : Brand("FreeStyle Libre")
}