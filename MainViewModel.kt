package com.mailscout.app

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Mode { URLS, SEARCH }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val extractor = Extractor()
    private var job: Job? = null

    var mode by mutableStateOf(Mode.URLS)
    var input by mutableStateOf("")
    var maxPages by mutableStateOf(5f)
    var verifyMx by mutableStateOf(true)
    var respectRobots by mutableStateOf(true)
    var filter by mutableStateOf("")

    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("Ready")
        private set

    val results = mutableStateListOf<Hit>()

    val visible: List<Hit>
        get() = if (filter.isBlank()) results.toList()
        else results.filter { it.email.contains(filter.trim(), true) }

    fun start() {
        if (running || input.isBlank()) return
        running = true
        status = "Starting…"
        job = viewModelScope.launch {
            try {
                val sites = if (mode == Mode.SEARCH) {
                    status = "Searching the web…"
                    extractor.search(input.trim())
                } else {
                    input.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
                }
                if (sites.isEmpty()) {
                    status = "No websites found. Check your input or connection."
                    return@launch
                }
                extractor.run(
                    sites = sites,
                    maxPages = maxPages.toInt(),
                    verifyMx = verifyMx,
                    respectRobots = respectRobots,
                    onHit = { hit -> withContext(Dispatchers.Main) { results.add(hit) } },
                    onStatus = { s -> withContext(Dispatchers.Main) { status = s } },
                )
                status = "Done · ${results.size} emails from ${sites.size} sites"
            } catch (e: kotlinx.coroutines.CancellationException) {
                status = "Stopped · ${results.size} emails"
                throw e
            } catch (e: Exception) {
                status = "Error: ${e.message}"
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        results.clear()
        status = "Ready"
    }

    fun allEmails(): String = visible.joinToString("\n") { it.email }

    fun shareCsv(context: Context) {
        if (visible.isEmpty()) return
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "mailscout-emails.csv")
        file.writeText(
            "email,source,mx_valid\n" + visible.joinToString("\n") {
                "${it.email},${it.source},${it.mx?.toString() ?: "unchecked"}"
            }
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export emails").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
