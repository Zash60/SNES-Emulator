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
    private val paint = Paint()

    // Recebe os pixels brutos do PPU e atualiza a tela
    fun updateFrame(pixels: IntArray) {
        // Copia os pixels do emulador para o Bitmap do Android
        // Nota: Estamos assumindo que o array tem pelo menos 256*224
        try {
            bitmap.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            invalidate() // Força o Android a redesenhar esta View (chama onDraw)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Calcula a escala para preencher a tela mantendo proporção (ou esticando)
        dstRect.set(0, 0, width, height)
        
        // Desenha o bitmap do jogo na tela do celular
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }
}
