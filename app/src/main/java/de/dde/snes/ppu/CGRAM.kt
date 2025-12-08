package de.dde.snes.ppu

import de.dde.snes.Short
import de.dde.snes.highByte
import de.dde.snes.lowByte

class CGRAM {
    val colors = Array(0x100) {Color(0)}

    var address = 0

    private var high = false
    private var tempColor = 0

    fun reset() {
        address = 0
        high = false
        tempColor = 0
    }

    fun write(color: Int) {
        if (high) {
            colors[address].value = Short(tempColor, color)
            address++
        } else {
            tempColor = color
        }

        high = !high
    }

    fun read(): Int {
        val r = if (high) {
            colors[address].value.highByte()
            address++
        } else {
            colors[address].value.lowByte()
        }

        high = !high
        return r
    }

    fun get(index: Int): Int {
        val snesColor = colors[index].value
        
        // SNES color format: xBBBBBGGGGGRRRRR (15-bit)
        val r = snesColor and 0x1F
        val g = (snesColor shr 5) and 0x1F
        val b = (snesColor shr 10) and 0x1F
        
        // Conversão para 32-bit ARGB (8 bits por componente)
        // Multiplica por 8 (<< 3) e adiciona os 3 bits mais significativos (>> 2) para preencher os 8 bits.
        val red = (r shl 3) or (r shr 2)
        val green = (g shl 3) or (g shr 2)
        val blue = (b shl 3) or (b shr 2)
        
        // Retorna ARGB: 0xFF R G B
        return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }
}

data class Color(var value: Int)