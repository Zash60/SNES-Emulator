package de.dde.snes

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

import de.dde.snes.cartridge.Cartridge
import de.dde.snes.SNES

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var emulatorView: EmulatorView
    private lateinit var layoutContainer: LinearLayout
    private lateinit var btnLoad: Button
    
    private val snes = SNES()
    @Volatile private var isEmulationRunning = false

    private val loadRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            loadAndStartRom(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        layoutContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt()) // Fundo Preto
        }

        // Tela do Emulador (Inicialmente invisível ou placeholder)
        emulatorView = EmulatorView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 
                1f // Peso 1 para ocupar o espaço
            )
        }

        btnLoad = Button(this).apply {
            text = "CARREGAR JOGO"
            textSize = 20f
            setOnClickListener {
                loadRomLauncher.launch(arrayOf("*/*"))
            }
        }

        statusText = TextView(this).apply {
            text = "SNES Kotlin Droid"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }

        layoutContainer.addView(emulatorView)
        layoutContainer.addView(statusText)
        layoutContainer.addView(btnLoad)
        
        setContentView(layoutContainer)
    }

    private fun loadAndStartRom(uri: Uri) {
        try {
            // Parar emulação anterior se houver
            isEmulationRunning = false
            Thread.sleep(100) 

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            
            if (inputStream != null) {
                val romBytes = inputStream.readBytes()
                inputStream.close()

                val cartridge = Cartridge(romBytes)
                
                snes.reset()
                snes.insertCartridge(cartridge)
                
                statusText.text = "Rodando: ${cartridge.header.name}"
                btnLoad.visibility = View.GONE // Esconde botão para imersão
                
                isEmulationRunning = true
                startEmulationLoop()
                
            }
        } catch (e: Exception) {
            statusText.text = "Erro: ${e.message}"
        }
    }

    private fun startEmulationLoop() {
        Thread {
            try {
                // Loop infinito do emulador
                while (isEmulationRunning) {
                    // Executa instruções até completar um frame (V-Blank)
                    // Nota: O método snes.start() original tem um while(true). 
                    // Precisamos de um método que execute apenas UM passo ou quadro.
                    // Como não podemos mudar o SNES.kt facilmente aqui sem risco, 
                    // vamos simular chamando o processador manualmente se necessário
                    // ou assumir que modificamos o SNES.kt para expor um método 'stepFrame'
                    
                    // HACK: Chamamos step() repetidamente até o frame ficar pronto
                    // Isso substitui o snes.start() que era bloqueante
                    
                    snes.processor.executeNextInstruction()
                    // Ciclos aproximados (cálculo simplificado para sincronia básica)
                    val cycles = snes.processor.cycles
                    snes.ppu.updateCycles(cycles, 4) // Delta fixo simulado
                    
                    // Se o PPU sinalizou frame pronto
                    if (snes.ppu.frameReady) {
                        snes.ppu.frameReady = false
                        
                        // Pegamos os pixels
                        // ATENÇÃO: Se o emulador ainda não gera pixels reais, 
                        // isso desenhará preto, mas a lógica está pronta.
                        val pixels = snes.ppu.videoBuffer
                        
                        // Atualiza a UI
                        runOnUiThread {
                            emulatorView.updateFrame(pixels)
                        }
                        
                        // Pequeno delay para não rodar a 1000 FPS (Speed limit simples)
                        Thread.sleep(16) 
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    statusText.text = "Crash: ${e.message}"
                    btnLoad.visibility = View.VISIBLE
                }
            }
        }.start()
    }
}
