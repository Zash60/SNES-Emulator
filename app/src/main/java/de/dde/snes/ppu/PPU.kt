@file:Suppress("UNUSED_PARAMETER", "MemberVisibilityCanBePrivate", "UNUSED_VARIABLE", "PropertyName")

package de.dde.snes.ppu

import de.dde.snes.*
import de.dde.snes.memory.Bank
import de.dde.snes.memory.Memory
import de.dde.snes.memory.MemoryMapping
import de.dde.snes.memory.ShortAddress

class PPU(
    private val snes: SNES
) : MemoryMapping {
    
    // --- VIDEO OUTPUT ---
    var frameReady = false
    
    // Buffer que a Activity lê (tela visível)
    val videoBuffer = IntArray(256 * 224)
    
    // Buffer onde a PPU desenha (buffer de trabalho)
    // Usamos dois buffers para evitar flickering (Double Buffering)
    val renderBuffer = IntArray(256 * 224)
    // --------------------

    val oam = OAM()
    val vram = VRAM()
    val cgram = CGRAM()
    val window1 = MaskWindow()
    val window2 = MaskWindow()
    val colorMath = ColorMath()

    // Registradores PPU
    var forceBlank = true
    var brightness = 0x00
    var bgMode = 0
    var bg3Prio = false
    
    var objSize = ObjectSize.byCode(0)
    var nameSelect = 0
    var nameBaseSelect = 0
    var mosaicSize = 1

    var countersLatched = false
    var hoffset = 0
    var hoffsetLatched = 0
    var hoffsetHigh = false
    var voffset = 0
    var voffsetLatched = 0
    var voffsetHigh = false

    var inVBlank = false
        private set
    var inHBlank = false
        private set

    // Flags de Sincronia
    var timeOver = false
    var rangeOver = false
    var interlaceField = false
    var externalSync = false
    var overscan = false

    val backgrounds = arrayOf(
        Background("BG1"),
        Background("BG2"),
        Background("BG3"),
        Background("BG4"),
        Background("OBJ")
    )

    val mode7 = Mode7()
    
    // Controle de escrita na VRAM
    var vramIncrementOnHigh = false

    // Método chamado ao final de cada quadro (VBlank) para exibir a imagem
    fun swapBuffers() {
        // Copia o renderBuffer (pronto) para o videoBuffer (visualização)
        System.arraycopy(renderBuffer, 0, videoBuffer, 0, videoBuffer.size)
    }

    fun reset() {
        vram.reset()
        oam.reset()
        cgram.reset()
        window1.reset()
        window2.reset()
        colorMath.reset()
        mode7.reset()
        for (bg in backgrounds) { bg.reset() }
        
        // CORREÇÃO: Desabilitar forceBlank para permitir renderização
        forceBlank = false
        brightness = 0x0F // Brilho máximo
        bgMode = 1 // Modo 1 por padrão (mais compatível)
        bg3Prio = false
        
        hoffset = 0; voffset = 0
        inVBlank = false; inHBlank = false
        
        vramIncrementOnHigh = false
        
        // CORREÇÃO: Habilitar BG1 por padrão para compatibilidade
        backgrounds[0].enableMainScreen = true
        
        // CORREÇÃO: Inicializar registradores importantes do PPU
        backgrounds[0].tilemapAddress = 0x0000
        backgrounds[0].baseAddress = 0x0000
        
        // Limpa os buffers na inicialização
        videoBuffer.fill(0xFF000000.toInt())
        renderBuffer.fill(0xFF000000.toInt())
    }

    var delta = 0L
    
    fun updateCycles(cycles: Long, delta: Long) {
        this.delta += delta

        while(this.delta >= CYCLES_PER_TICK) {
            this.delta -= CYCLES_PER_TICK
            hoffset++

            // Lógica simplificada de timing H/V
            if (hoffset == FIRST_H_OFFSET) {
                inHBlank = false
            }

            if (hoffset == FIRST_H_OFFSET + snes.version.width) {
                inHBlank = true
                snes.dma.forEach { it.doHdmaForScanline(voffset) }
                renderScanline(voffset)
            }

            if (hoffset == FIRST_H_OFFSET + snes.version.width + snes.version.vWidthEnd) {
                hoffset = 0
                voffset++

                if (voffset == FIRST_V_OFFSET) {
                    inVBlank = false
                }

                if (voffset == FIRST_V_OFFSET + snes.version.heigth) {
                    inVBlank = true
                    // CORREÇÃO: Definir frameReady apenas uma vez por frame
                    if (!frameReady) {
                        frameReady = true
                        if (snes.processor.nmiEnabled)
                            snes.processor.nmiRequested = true
                    }
                }

                if (voffset == FIRST_V_OFFSET + snes.version.heigth + snes.version.vHeightEnd) {
                    voffset = 0
                    // CORREÇÃO: Reset frameReady para o próximo frame
                    frameReady = false
                }
            }
            
            // IRQs
            if (snes.processor.xIrqEnabled || snes.processor.yIrqEnabled) {
                val xIrq = if (snes.processor.xIrqEnabled) snes.processor.htime else 0
                val yIrq = if (snes.processor.yIrqEnabled) snes.processor.vtime else voffset
                if (hoffset == xIrq && voffset == yIrq) {
                    snes.processor.irqRequested = true
                }
            }
        }
    }

    fun latchCounter() {
        countersLatched = true
        hoffsetLatched = hoffset
        voffsetLatched = voffset
    }

    // --- RENDERIZADOR BÁSICO (BG1 + Cor de Fundo) ---
    private fun renderScanline(y: Int) {
        if (y < 0 || y >= 224) return

        val rowStart = y * 256

        // Se force blank, pinta de preto e sai
        if (forceBlank) {
            val black = 0xFF000000.toInt()
            // Escreve no renderBuffer, não no videoBuffer diretamente
            for (x in 0 until 256) renderBuffer[rowStart + x] = black
            return
        }

        // 1. Pinta a linha com a cor de fundo (Backdrop)
        val backdropColor = cgram.get(0)
        for (x in 0 until 256) renderBuffer[rowStart + x] = backdropColor

        // 2. Renderiza BG1 (Se estiver habilitado na Main Screen e for Mode 0 ou 1)
        if (backgrounds[0].enableMainScreen && (bgMode == 0 || bgMode == 1)) {
            renderBg1Line(y, rowStart)
        }
        
        // CORREÇÃO: Fallback - se nada foi renderizado, desenha um padrão simples
        if (renderBuffer[rowStart] == backdropColor && y % 16 < 8) {
            // Desenhar linhas horizontais simples para debug
            val debugColor = cgram.get(1) // Segunda cor da paleta
            for (x in 0 until 256 step 32) {
                renderBuffer[rowStart + x] = debugColor
            }
        }
    }

    private fun renderBg1Line(y: Int, rowStart: Int) {
        val bg1 = backgrounds[0]
        val tileMapBase = bg1.tilemapAddress
        val tileDataBase = bg1.baseAddress
        
        // Rolagem (Scroll) - Simplificado
        val scrollX = bg1.hScroll 
        val scrollY = (y + bg1.vScroll) 
        
        for (x in 0 until 256) {
            val absoluteX = (x + scrollX) and 0x3FF 
            val absoluteY = scrollY and 0x3FF

            // Descobre qual tile (bloco 8x8) estamos
            val tileX = absoluteX / 8
            val tileY = absoluteY / 8
            
            // Mapa 32x32 tiles (padrão)
            val mapAddress = tileMapBase + ((tileY % 32) * 32 + (tileX % 32))
            
            // Leitura de VRAM simplificada para atributos do tile
            val addrInBytes = (mapAddress and 0xFFFF) * 2
            val low = vram.vram[addrInBytes].toInt() and 0xFF
            val high = vram.vram[addrInBytes + 1].toInt() and 0xFF
            val tileAttr = low or (high shl 8)
            
            // Decodifica atributos
            val charIdx = tileAttr and 0x03FF
            val paletteIdx = (tileAttr shr 10) and 0x07
            val priority = (tileAttr and 0x2000) != 0
            val flipX = (tileAttr and 0x4000) != 0
            val flipY = (tileAttr and 0x8000) != 0
            
            // Pega o pixel dentro do tile (0-7)
            var pixelX = absoluteX % 8
            var pixelY = absoluteY % 8
            
            if (flipX) pixelX = 7 - pixelX
            if (flipY) pixelY = 7 - pixelY
            
            // Endereço do dado do caractere (4bpp = 32 bytes por tile)
            val charAddr = tileDataBase + (charIdx * 32)
            
            // Decodifica 4bpp planar
            val rowOffset = pixelY * 2
            
            val b0 = vram.vram[(charAddr + rowOffset) and 0xFFFF].toInt()
            val b1 = vram.vram[(charAddr + rowOffset + 1) and 0xFFFF].toInt()
            val b2 = vram.vram[(charAddr + rowOffset + 16) and 0xFFFF].toInt()
            val b3 = vram.vram[(charAddr + rowOffset + 17) and 0xFFFF].toInt()
            
            val mask = 0x80 shr pixelX
            var color = 0
            if ((b0 and mask) != 0) color = color or 1
            if ((b1 and mask) != 0) color = color or 2
            if ((b2 and mask) != 0) color = color or 4
            if ((b3 and mask) != 0) color = color or 8
            
            // Se cor for 0, é transparente
            if (color != 0) {
                // BG1 usa as primeiras 128 cores (8 paletas de 16 cores)
                val cgramIndex = (paletteIdx * 16) + color
                val rgb = cgram.get(cgramIndex)
                // Escreve no renderBuffer
                renderBuffer[rowStart + x] = rgb
            }
        }
    }

    override fun readByte(bank: Bank, address: ShortAddress): Int {
        return when (address) {
            0x2134, 0x2135, 0x2136 -> Memory.OPEN_BUS
            0x2139 -> { // VMDATAL Read
                val r = vram.read(VRAM.IncrementMode.LOW)
                r and 0xFF
            }
            0x213A -> { // VMDATAH Read
                val r = vram.read(VRAM.IncrementMode.HIGH)
                r shr 8
            }
            0x213E -> {
                 var r = 0x01 // PPU1 Version
                 if (timeOver) r = r or 0x80
                 if (rangeOver) r = r or 0x40
                 r
            }
            0x213F -> {
                 hoffsetHigh = false; voffsetHigh = false
                 var r = 0x02 // PPU2 Version & NTSC
                 if (interlaceField) r = r or 0x80
                 if (countersLatched) {
                     r = r or 0x40
                     if (snes.controllers.programmableIo2Line) countersLatched = false
                 }
                 r
            }
            else -> Memory.OPEN_BUS
        }
    }

    override fun writeByte(bank: Bank, address: ShortAddress, value: Int) {
        when (address) {
            0x2100 -> { // INIDISP
                forceBlank = value.isBitSet(0x80)
                brightness = value and 0x0F
            }
            0x2105 -> { // BGMODE
                bgMode = value and 0x07
                bg3Prio = value.isBitSet(0x08)
            }
            // BG Map Addresses
            0x2107 -> backgrounds[0].tilemapAddress = (value and 0x7C) shl 8
            0x2108 -> backgrounds[1].tilemapAddress = (value and 0x7C) shl 8
            0x2109 -> backgrounds[2].tilemapAddress = (value and 0x7C) shl 8
            0x210A -> backgrounds[3].tilemapAddress = (value and 0x7C) shl 8
            
            // BG Character Data Addresses
            0x210B -> {
                backgrounds[0].baseAddress = (value and 0x0F) * 0x2000
                backgrounds[1].baseAddress = (value shr 4) * 0x2000
            }
            0x210C -> {
                backgrounds[2].baseAddress = (value and 0x0F) * 0x2000
                backgrounds[3].baseAddress = (value shr 4) * 0x2000
            }

            // Scroll registers
            0x210D -> backgrounds[0].hScroll = (value shl 8) or (backgrounds[0].prev shl 8 shr 8)
            0x210E -> backgrounds[0].vScroll = (value shl 8) or (backgrounds[0].prev shl 8 shr 8)

            // VRAM Access
            0x2115 -> { // VMAIN
                vramIncrementOnHigh = value.isBitSet(0x80)
                val incMapping = (value and 0x0C) shr 2
                vram.mapping = VRAM.Mapping.byCode(incMapping)
                val incStep = value and 0x03
                vram.increment = when(incStep) {
                    0 -> VRAM.Increment._1
                    1 -> VRAM.Increment._32
                    2 -> VRAM.Increment._128
                    3 -> VRAM.Increment._128_2
                    else -> VRAM.Increment._1
                }
                vram.addressIncrementMode = if (vramIncrementOnHigh) VRAM.IncrementMode.HIGH else VRAM.IncrementMode.LOW
            }
            0x2116 -> vram.address = (vram.address and 0xFF00) or value
            0x2117 -> vram.address = (vram.address and 0x00FF) or (value shl 8)
            0x2118 -> { // VMDATAL
                vram.dataWrite = (vram.dataWrite and 0xFF00) or value
                if (!vramIncrementOnHigh) vram.write(VRAM.IncrementMode.LOW)
            }
            0x2119 -> { // VMDATAH
                vram.dataWrite = (vram.dataWrite and 0x00FF) or (value shl 8)
                if (vramIncrementOnHigh) vram.write(VRAM.IncrementMode.HIGH)
            }
            
            // CGRAM
            0x2121 -> cgram.address = value
            0x2122 -> cgram.write(value)

            0x212C -> { // TM
                for (i in 0..4) {
                    backgrounds[i].enableMainScreen = value.isBitSet(1 shl i)
                }
            }
            
            in 0x2100..0x213F -> { }
            M7HOFS -> { }
            M7VOFS -> { }
        }
        
        if (address == 0x210D || address == 0x210E) {
             backgrounds[0].prev = value
        }
    }

    companion object {
        const val M7HOFS = 0x10001
        const val M7VOFS = 0x10002
        const val CYCLES_PER_TICK = 4
        const val FIRST_H_OFFSET = 22
        const val FIRST_V_OFFSET = 1
    }
}

// --- Definições que faltavam no final do arquivo ---

class Mode7 {
    var hScroll = 0
    var vScroll = 0
    var prev = 0
    var matrixA = 0
    var matrixB = 0
    var matrixC = 0
    var matrixD = 0
    var centerX = 0
    var centerY = 0
    var bigSize = false
    var spaceFill = false
    var mirrorX = false
    var mirrorY = false
    var extBg = false
    fun reset() {}
}

enum class ObjectSize {
    _8x8_16x16, _8x8_32x32, _8x8_64x64, _16x16_32x32,
    _16x16_64x64, _32x32_64x64, _16x32_32x32, _16x32_32x64;
    val code get() = ordinal
    companion object {
        fun byCode(code: Int) = values()[code]
    }
}
