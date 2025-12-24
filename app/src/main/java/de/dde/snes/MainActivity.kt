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
            setBackgroundColor(0xFF202020.toInt()) // Cinza escuro para diferenciar do preto do jogo
        }

        emulatorView = EmulatorView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 
                1f
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
            text = "SNES Core Ready"
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
        // Usamos uma Thread separada para ler o arquivo pesado (I/O) para não travar a UI
        Thread {
            try {
                isEmulationRunning = false
                Thread.sleep(100) 

                val contentResolver = applicationContext.contentResolver
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                
                if (inputStream != null) {
                    // Isso pode demorar para ROMs grandes, por isso está aqui
                    val romBytes = inputStream.readBytes()
                    inputStream.close()

                    // Volta para a Thread Principal para inicializar o SNES
                    runOnUiThread {
                        try {
                            val cartridge = Cartridge(romBytes)
                            
                            // Debug: Verificar se o Header foi lido
                            println("ROM Carregada: ${cartridge.header.name}, MapMode: ${cartridge.header.mapMode}")
                            
                            snes.reset()
                            snes.insertCartridge(cartridge)
                            // Reset novamente após inserir o cartucho para garantir vetores corretos
                            snes.reset()
                            
                            statusText.text = "Rodando: ${cartridge.header.name}"
                            btnLoad.visibility = View.GONE
                            
                            isEmulationRunning = true
                            startEmulationLoop()
                        } catch (e: Exception) {
                            statusText.text = "Erro Init: ${e.message}"
                            e.printStackTrace()
                            btnLoad.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Erro Leitura: ${e.message}"
                    e.printStackTrace()
                    btnLoad.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun startEmulationLoop() {
        Thread {
            try {
                while (isEmulationRunning) {
                    // --- LOOP DO QUADRO (FRAME) ---
                    // Executa o processador loucamente até completar 1 quadro de vídeo
                    
                    snes.ppu.frameReady = false
                    
                    while (!snes.ppu.frameReady && isEmulationRunning) {
                        // 1. Guarda ciclos atuais
                        val cyclesBefore = snes.processor.cycles
                        
                        // 2. Executa UMA instrução do SNES
                        snes.processor.executeNextInstruction()
                        
                        // 3. Calcula quantos ciclos passaram
                        val cyclesDelta = snes.processor.cycles - cyclesBefore
                        
                        // 4. Atualiza o PPU (Placa de vídeo) com esse tempo
                        // O PPU vai desenhar a linha (scanline) quando tiver ciclos suficientes
                        snes.ppu.updateCycles(snes.processor.cycles, cyclesDelta)
                    }

                    // --- FIM DO QUADRO ---
                    // Se chegamos aqui, o PPU terminou de desenhar a tela (VBlank)
                    
                    if (isEmulationRunning) {
                        // IMPORTANTE: Troca os buffers para exibir o que foi desenhado
                        snes.ppu.swapBuffers()
                        
                        // Copia o buffer de vídeo para a UI
                        val pixels = snes.ppu.videoBuffer
                        
                        // runOnUiThread é pesado se chamado muitas vezes, 
                        // mas 60 vezes por segundo é aceitável.
                        runOnUiThread {
                            emulatorView.updateFrame(pixels)
                        }
                        
                        // Limitador de velocidade simples (60 FPS)
                        Thread.sleep(16)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    statusText.text = "Crash na CPU: ${e.message}"
                    btnLoad.visibility = View.VISIBLE
                }
            }
        }.start()
    }
}
