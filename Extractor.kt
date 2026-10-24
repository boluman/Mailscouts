package com.mailscout.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class Hit(val email: String, val source: String, val mx: Boolean?)

class Extractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent = "Mozilla/5.0 (Linux; Android 14) MailScout/1.0"

    private val emailRegex =
        Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}""")
    private val atRegex = Regex("""\s*[\[(]\s*at\s*[\])]\s*""", RegexOption.IGNORE_CASE)
    private val dotRegex = Regex("""\s*[\[(]\s*dot\s*[\])]\s*""", RegexOption.IGNORE_CASE)

    private val junkTlds = setOf(
        "png", "jpg", "jpeg", "gif", "svg", "webp", "css", "js", "woff", "woff2", "ico", "map"
    )
    private val junkDomains = listOf("example.com", "sentry.io", "wixpress.com", "domain.com")
    private val pageHints = listOf(
        "contact", "about", "team", "impressum", "support", "reach", "connect", "staff", "people"
    )

    private val robotsCache = ConcurrentHashMap<String, List<String>>()
    private val mxCache = ConcurrentHashMap<String, Boolean>()

    // ---------- networking ----------

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val type = resp.header("Content-Type").orEmpty()
                if (type.isNotEmpty() && !type.contains("html") && !type.contains("text")) {
                    return@use null
                }
                resp.body?.string()?.take(2_000_000)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    // ---------- robots.txt ----------

    private suspend fun disallowRules(host: String): List<String> {
        robotsCache[host]?.let { return it }
        val body = fetch("https://$host/robots.txt").orEmpty()
        val rules = mutableListOf<String>()
        var inStar = false
        for (line in body.lines()) {
            val l = line.substringBefore("#").trim()
            when {
                l.startsWith("user-agent:", true) ->
                    inStar = l.substringAfter(":").trim() == "*"
                inStar && l.startsWith("disallow:", true) -> {
                    val path = l.substringAfter(":").trim()
                    if (path.isNotEmpty()) rules.add(path)
                }
            }
        }
        robotsCache[host] = rules
        return rules
    }

    private suspend fun allowed(url: String): Boolean {
        return try {
            val uri = URI(url)
            val path = (uri.rawPath ?: "/").ifEmpty { "/" }
            disallowRules(uri.host ?: return false).none { path.startsWith(it) }
        } catch (e: Exception) {
            true
        }
    }

    // ---------- extraction ----------

    private fun emailsIn(html: String, baseUrl: String): Set<String> {
        val doc = Jsoup.parse(html, baseUrl)
        val found = mutableSetOf<String>()

        doc.select("a[href^=mailto:]").forEach { a ->
            val addr = a.attr("href").substringAfter("mailto:").substringBefore("?")
            val decoded = try {
                URLDecoder.decode(addr, "UTF-8")
            } catch (e: Exception) {
                addr
            }
            found += decoded.split(",")
        }

        val text = doc.text().replace(atRegex, "@").replace(dotRegex, ".")
        emailRegex.findAll(text).forEach { found += it.value }
        emailRegex.findAll(html).forEach { found += it.value }

        return found.map { it.trim().lowercase() }.filter(::looksValid).toSet()
    }

    private fun looksValid(email: String): Boolean {
        if (email.length > 254 || email.contains("..")) return false
        if (!emailRegex.matches(email)) return false
        val domain = email.substringAfter("@")
        if (domain.substringAfterLast(".") in junkTlds) return false
        if (junkDomains.any { domain.endsWith(it) }) return false
        return true
    }

    private fun contactLinks(html: String, baseUrl: String): List<String> {
        val host = try { URI(baseUrl).host } catch (e: Exception) { null } ?: return emptyList()
        return Jsoup.parse(html, baseUrl).select("a[href]")
            .map { it.absUrl("href").substringBefore("#") }
            .filter { link ->
                val h = try { URI(link).host } catch (e: Exception) { null }
                h == host && pageHints.any { link.lowercase().contains(it) }
            }
            .distinct()
    }

    // ---------- MX verification (DNS over HTTPS) ----------

    private suspend fun hasMx(domain: String): Boolean? {
        mxCache[domain]?.let { return it }
        val body = withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://cloudflare-dns.com/dns-query?name=$domain&type=MX")
                    .header("accept", "application/dns-json")
                    .build()
                client.newCall(req).execute().use { it.body?.string() }
            } catch (e: Exception) {
                null
            }
        } ?: return null
        return try {
            val json = JSONObject(body)
            val ok = json.optInt("Status", -1) == 0 && json.optJSONArray("Answer") != null
            mxCache[domain] = ok
            ok
        } catch (e: Exception) {
            null
        }
    }

    // ---------- public API ----------

    /** Finds website addresses for a keyword search using DuckDuckGo's HTML endpoint. */
    suspend fun search(query: String, limit: Int = 15): List<String> {
        val q = URLEncoder.encode(query, "UTF-8")
        val html = fetch("https://html.duckduckgo.com/html/?q=$q") ?: return emptyList()
        return Jsoup.parse(html).select("a.result__a")
            .mapNotNull { a ->
                val href = a.attr("href")
                val target = if (href.contains("uddg=")) {
                    URLDecoder.decode(href.substringAfter("uddg=").substringBefore("&"), "UTF-8")
                } else href
                target.takeIf { it.startsWith("http") }
            }
            .distinctBy { try { URI(it).host } catch (e: Exception) { it } }
            .take(limit)
    }

    /**
     * Crawls each site (home page plus likely contact pages) and reports every new email.
     * onHit and onStatus may be called from background threads.
     */
    suspend fun run(
        sites: List<String>,
        maxPages: Int,
        verifyMx: Boolean,
        respectRobots: Boolean,
        onHit: suspend (Hit) -> Unit,
        onStatus: suspend (String) -> Unit,
    ) {
        val seen = ConcurrentHashMap.newKeySet<String>()
        val gate = Semaphore(4)

        coroutineScope {
            sites.map { raw ->
                async {
                    gate.withPermit {
                        val start = normalize(raw)
                        val host = try { URI(start).host } catch (e: Exception) { null }
                        onStatus("Scanning ${host ?: start}")
                        if (host == null) return@withPermit

                        val queue = ArrayDeque<String>().apply { add(start) }
                        val visited = mutableSetOf<String>()

                        while (queue.isNotEmpty() && visited.size < maxPages) {
                            val url = queue.removeFirst()
                            if (!visited.add(url)) continue
                            if (respectRobots && !allowed(url)) continue
                            val html = fetch(url) ?: continue

                            if (visited.size == 1) {
                                contactLinks(html, url).forEach { if (it !in visited) queue.add(it) }
                            }

                            for (email in emailsIn(html, url)) {
                                if (!seen.add(email)) continue
                                val mx = if (verifyMx) hasMx(email.substringAfter("@")) else null
                                onHit(Hit(email, host, mx))
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }
}
