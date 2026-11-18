package com.example.smartm3uplayer

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

data class Channel(val id: Int, val name: String, val url: String)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var channelListView: ListView
    private lateinit var sidebarPanel: LinearLayout
    private lateinit var osdBottomBar: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var tvCurrentChannel: TextView
    private lateinit var etSearch: EditText

    private val allChannels = mutableListOf<Channel>()
    private val displayedChannels = mutableListOf<Channel>()
    private lateinit var adapter: ArrayAdapter<String>

    private var resizeModeIndex = 0
    private val resizeModes = listOf(
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Imagine: Fit"),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Imagine: Fill"),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Imagine: Zoom")
    )

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadM3UFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        channelListView = findViewById(R.id.channelListView)
        sidebarPanel = findViewById(R.id.sidebarPanel)
        osdBottomBar = findViewById(R.id.osdBottomBar)
        settingsPanel = findViewById(R.id.settingsPanel)
        tvCurrentChannel = findViewById(R.id.tvCurrentChannel)
        etSearch = findViewById(R.id.etSearch)

        val btnToggleList: Button = findViewById(R.id.btnToggleList)
        val btnLoadUrl: Button = findViewById(R.id.btnLoadUrl)
        val btnLoadFile: Button = findViewById(R.id.btnLoadFile)
        val btnAspectRatio: Button = findViewById(R.id.btnAspectRatio)
        val btnSettings: Button = findViewById(R.id.btnSettings)
        val btnCloseSettings: Button = findViewById(R.id.btnCloseSettings)
        val seekVolume: SeekBar = findViewById(R.id.seekVolume)
        val seekBrightness: SeekBar = findViewById(R.id.seekBrightness)

        // Inițializare ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // Ascundere / afișare listă canale prin butonul meniu
        btnToggleList.setOnClickListener {
            sidebarPanel.visibility = if (sidebarPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Tap pe player afișează bara OSD
        playerView.setOnClickListener {
            osdBottomBar.visibility = if (osdBottomBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Adapter listă canale
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        channelListView.adapter = adapter

        channelListView.setOnItemClickListener { _, _, position, _ ->
            val selected = displayedChannels[position]
            playChannel(selected)
            sidebarPanel.visibility = View.GONE
        }

        // Căutare în canale
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s.toString())
            }
        })

        // Încărcare M3U din link prestabilit sau introdus
        btnLoadUrl.setOnClickListener { showUrlDialog() }
        btnLoadFile.setOnClickListener { filePicker.launch("*/*") }

        // Setări imagine (aspect ratio)
        btnAspectRatio.setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
            playerView.resizeMode = resizeModes[resizeModeIndex].first
            btnAspectRatio.text = resizeModes[resizeModeIndex].second
        }

        // Setări volum / luminozitate panel
        btnSettings.setOnClickListener { settingsPanel.visibility = View.VISIBLE }
        btnCloseSettings.setOnClickListener { settingsPanel.visibility = View.GONE }

        // Control Volum Sistem
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        seekVolume.max = maxVol
        seekVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Control Luminozitate Ecran
        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val lp = window.attributes
                    lp.screenBrightness = (progress.coerceAtLeast(5)) / 100f
                    window.attributes = lp
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Încarcă automat link-ul tău preferat la pornire
        loadM3UFromUrl("https://raw.githubusercontent.com/slava-67/TV/refs/heads/IPTV/kino-plus.m3u")
    }

    private fun showUrlDialog() {
        val input = EditText(this)
        input.hint = "https://exemplu.com/playlist.m3u"
        AlertDialog.Builder(this)
            .setTitle("Introdu Link M3U / Host")
            .setView(input)
            .setPositiveButton("Încarcă") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) loadM3UFromUrl(url)
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    private fun loadM3UFromUrl(urlString: String) {
        Toast.makeText(this, "Se descarcă playlist-ul...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val content = URL(urlString).readText()
                withContext(Dispatchers.Main) { parseM3U(content) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Eroare descărcare: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadM3UFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val content = BufferedReader(InputStreamReader(inputStream)).readText()
            parseM3U(content)
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare fișier: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseM3U(content: String) {
        allChannels.clear()
        val lines = content.lines()
        var currentName = "Canal Necunoscut"
        var idCounter = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                currentName = trimmed.substringAfterLast(",").trim()
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                allChannels.add(Channel(idCounter++, currentName, trimmed))
                currentName = "Canal Necunoscut"
            }
        }

        filterChannels("")
        Toast.makeText(this, "Încărcate ${allChannels.size} canale", Toast.LENGTH_SHORT).show()
        if (allChannels.isNotEmpty()) {
            playChannel(allChannels[0])
        }
    }

    private fun filterChannels(query: String) {
        displayedChannels.clear()
        val q = query.lowercase()
        for (ch in allChannels) {
            if (q.isEmpty() || ch.name.lowercase().contains(q)) {
                displayedChannels.add(ch)
            }
        }

        val displayList = displayedChannels.map { ch ->
            String.format("%03d. %s", ch.id, ch.name)
        }
        adapter.clear()
        adapter.addAll(displayList)
        adapter.notifyDataSetChanged()
    }

    private fun playChannel(channel: Channel) {
        val title = String.format("%03d. %s", channel.id, channel.name)
        tvCurrentChannel.text = title
        Toast.makeText(this, "Redare: ${channel.name}", Toast.LENGTH_SHORT).show()
        
        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
