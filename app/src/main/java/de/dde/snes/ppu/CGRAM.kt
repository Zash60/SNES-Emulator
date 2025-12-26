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
        
        // CORREÇÃO: Inicializar paleta de cores padrão do SNES
        // As primeiras 256 cores incluem uma paleta de cores padrão
        initializeDefaultPalette()
    }
    
    private fun initializeDefaultPalette() {
        // Paleta de cores padrão do SNES (CGA-like)
        val defaultColors = intArrayOf(
            // Cores 0-15: Paleta básica
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
            
            // Cores 16-255: Tons de cinza e cores adicionais
            // Gerar tons de cinza
            *IntArray(240) { i ->
                val gray = ((i * 255) / 239).coerceIn(0, 255)
                (gray shl 16) or (gray shl 8) or gray
            }
        )
        
        for (i in defaultColors.indices) {
            if (i < colors.size) {
                // Converter cor RGB para formato SNES (15-bit)
                val r = (defaultColors[i] shr 16) and 0xFF
                val g = (defaultColors[i] shr 8) and 0xFF
                val b = defaultColors[i] and 0xFF
                
                // Converter para 15-bit: xBBBBBGGGGGRRRRR
                val snesColor = ((r shr 3) and 0x1F) or
                                (((g shr 3) and 0x1F) shl 5) or
                                (((b shr 3) and 0x1F) shl 10)
                
                colors[i].value = snesColor
            }
        }
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