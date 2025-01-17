package me.magnum.melonds.domain.model

import java.util.*

data class RomInfo(val gameCode: String, val headerChecksum: UInt, val gameTitle: String) {
    fun headerChecksumString(): String {
        return headerChecksum.toString(16).padStart(8, '0').toUpperCase(Locale.getDefault())
    }
}