package de.dde.snes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class EmulatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val WIDTH = 256
    private val HEIGHT = 224
    
    // Configuração correta de bitmap para SNES
    private val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    private val srcRect = Rect(0, 0, WIDTH, HEIGHT)
    private val dstRect = Rect()
    
    // Paint sem filtro para manter o visual "pixel art" nítido
    private val paint = Paint().apply {
        isFilterBitmap = false 
    }

    fun updateFrame(pixels: IntArray) {
        try {
            // Copia direto os pixels do PPU para o Bitmap nativo do Android
            // Isso é mais rápido que copiar para um array temporário
            bitmap.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            
            // Avisa a UI que precisa redesenhar
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Ajusta o retângulo de destino para caber na View
        dstRect.set(0, 0, width, height)
        
        // Desenha o bitmap
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }
}
