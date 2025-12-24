package de.dde.snes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class EmulatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Resolução nativa do SNES
    private val WIDTH = 256
    private val HEIGHT = 224
    
    // O Bitmap onde desenharemos os pixels do emulador
    private val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    private val srcRect = Rect(0, 0, WIDTH, HEIGHT)
    private val dstRect = Rect()
    private val paint = Paint().apply {
        isFilterBitmap = false // Desliga o filtro para manter pixels nítidos (estilo retro)
    }

    // Recebe os pixels brutos do PPU e atualiza a tela
    fun updateFrame(pixels: IntArray) {
        // Copia os pixels do emulador para o Bitmap do Android
        try {
            // System.arraycopy é mais rápido que loop manual
            System.arraycopy(pixels, 0, bitmapPixels, 0, pixels.size)
            invalidate() // Força o Android a redesenhar esta View (chama onDraw)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Helper para acessar os pixels do bitmap internamente de forma rápida
    private val bitmapPixels = IntArray(WIDTH * HEIGHT)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap.setPixels(bitmapPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Calcula a escala para preencher a tela mantendo proporção (ou esticando)
        dstRect.set(0, 0, width, height)
        
        // Desenha o bitmap do jogo na tela do celular
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }
}
