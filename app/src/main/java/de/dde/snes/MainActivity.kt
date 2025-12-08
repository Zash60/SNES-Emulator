package de.dde.snes

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

// Imports do Core do Emulador
import de.dde.snes.cartridge.Cartridge
import de.dde.snes.SNES

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    
    // Instância do emulador
    private val snes = SNES()

    private val loadRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            loadAndStartRom(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        val btnLoad = Button(this).apply {
            text = "LOAD ROM & START"
            textSize = 18f
            setOnClickListener {
                loadRomLauncher.launch(arrayOf("*/*"))
            }
        }

        statusText = TextView(this).apply {
            text = "SNES Android Core Ready.\nWaiting for ROM..."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 0)
        }

        layout.addView(btnLoad)
        layout.addView(statusText)
        setContentView(layout)
    }

    private fun loadAndStartRom(uri: Uri) {
        try {
            val contentResolver = applicationContext.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            
            if (inputStream != null) {
                // 1. Ler bytes
                val romBytes = inputStream.readBytes()
                inputStream.close()

                statusText.text = "ROM Carregada: ${romBytes.size} bytes.\nInicializando Cartucho..."
                
                // 2. Criar Cartucho (Agora aceita ByteArray graças ao script!)
                val cartridge = Cartridge(romBytes)
                
                // 3. Resetar e Inserir no Console
                snes.reset()
                snes.insertCartridge(cartridge)
                
                statusText.text = "Cartucho inserido!\nHeader: ${cartridge.header.name}\nIniciando CPU..."
                
                // 4. Iniciar Emulação (Em Thread separada para não travar UI)
                Thread {
                    try {
                        // snes.reset() já foi chamado
                        snes.start() 
                    } catch (e: Exception) {
                        runOnUiThread {
                            statusText.text = "Crash no Emulador:\n${e.message}"
                        }
                        e.printStackTrace()
                    }
                }.start()
                
                Toast.makeText(this, "Emulação Iniciada!", Toast.LENGTH_SHORT).show()
                
            } else {
                statusText.text = "Erro: Stream nulo."
            }
        } catch (e: Exception) {
            statusText.text = "Erro Fatal:\n${e.message}"
            e.printStackTrace()
        }
    }
}
