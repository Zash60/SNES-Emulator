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

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    // Configura o contrato moderno para abrir arquivos (Scoped Storage)
    private val loadRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            loadRom(uri)
        } else {
            Toast.makeText(this, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout Simples criado via código (sem XML)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        // Botão Load ROM
        val btnLoad = Button(this).apply {
            text = "LOAD ROM (SMC/SFC)"
            textSize = 18f
            setOnClickListener {
                // Abre o seletor de arquivos
                // Filtra por qualquer tipo para garantir que mostre as ROMs
                loadRomLauncher.launch(arrayOf("*/*"))
            }
        }

        // Texto de Status
        statusText = TextView(this).apply {
            text = "SNES Core Initialized.\nSelect a ROM to load into memory."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 0)
        }

        layout.addView(btnLoad)
        layout.addView(statusText)
        setContentView(layout)
    }

    private fun loadRom(uri: Uri) {
        try {
            val contentResolver = applicationContext.contentResolver
            
            // Abre o arquivo usando Stream (Android não usa Paths de disco diretamente)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            
            if (inputStream != null) {
                // Lê os bytes para a memória (Simulando o carregamento do cartucho)
                val bytes = inputStream.readBytes()
                inputStream.close()

                statusText.text = "SUCESSO!\nArquivo: $uri\nTamanho: ${bytes.size} bytes carregados na RAM.\n\n(Pronto para passar ao Emulator Core)"
                
                Toast.makeText(this, "ROM Carregada!", Toast.LENGTH_LONG).show()
                
                // TODO: Aqui você chamará o Core do emulador:
                // val cartridge = Cartridge(bytes) 
                // snes.insertCartridge(cartridge)
                // snes.start()
                
            } else {
                statusText.text = "Erro: Não foi possível abrir o stream do arquivo."
            }
        } catch (e: Exception) {
            statusText.text = "Erro ao ler ROM:\n${e.message}"
            e.printStackTrace()
        }
    }
}
