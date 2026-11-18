package com.example.data.local

import android.content.Context
import com.example.R

/**
 * Curated list of Russian Ministry of Digital Development (Список Минцифры)
 * socially significant & infrastructure domains + private LAN CIDRs.
 */
object MincifryWhitelist {

    private val fallbackDomains: List<String> = listOf(
        "gosuslugi.ru",
        "esia.gosuslugi.ru",
        "lk.gosuslugi.ru",
        "gu-st.ru",
        "sberbank.ru",
        "online.sberbank.ru",
        "id.sber.ru",
        "id.tbank.ru",
        "vtb.ru",
        "alfabank.ru",
        "yandex.ru",
        "ya.ru",
        "yandex.net",
        "yandex.com",
        "yastatic.net",
        "dzen.ru",
        "vk.com",
        "vk.ru",
        "userapi.com",
        "ok.ru",
        "mail.ru",
        "my.mail.ru",
        "rutube.ru",
        "ozon.ru",
        "wb.ru",
        "avito.ru",
        "avito.st",
        "img.avito.st",
        "kinopoisk.ru",
        "2gis.ru",
        "2gis.com",
        "rzd.ru",
        "pochta.ru",
        "tutu.ru",
        "rambler.ru",
        "rbc.ru",
        "lenta.ru",
        "kp.ru",
        "kremlin.ru",
        "government.ru",
        "duma.gov.ru",
        "digital.gov.ru",
        "xn--80ajghhoc2aj1c8b.xn--p1ai"
    )

    @Volatile
    private var loadedDomains: List<String>? = null

    private val initLock = Any()

    val defaultDomains: List<String>
        get() = loadedDomains ?: fallbackDomains

    fun init(context: Context) {
        if (loadedDomains != null) return
        synchronized(initLock) {
            if (loadedDomains != null) return
            runCatching {
                val raw = context.resources.openRawResource(R.raw.mincifry_whitelist)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readLines() }
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .distinct()
                if (raw.isNotEmpty()) {
                    loadedDomains = raw
                }
            }
        }
    }

    val privateLanCidrs: List<String> = listOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "100.64.0.0/10",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.0.0.0/24",
        "192.0.2.0/24",
        "192.168.0.0/16",
        "198.18.0.0/15",
        "198.51.100.0/24",
        "203.0.113.0/24",
        "224.0.0.0/4",
        "240.0.0.0/4",
        "::1/128",
        "fc00::/7",
        "fe80::/10"
    )

    /**
     * Validates that input is a real domain name (ASCII like example.com or Cyrillic .ru / .рф),
     * rejecting arbitrary text, spaces, protocols, or non-domain strings.
     */
    fun normalizeAndValidateDomain(raw: String): String? {
        val cleaned = raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
            .substringBefore(":")
            .trim('.')

        if (cleaned.length < 4 || cleaned.length > 253) return null
        if (cleaned.contains(" ") || !cleaned.contains(".")) return null

        val labels = cleaned.split(".")
        if (labels.size < 2) return null

        val labelRegex = Regex("^[a-z0-9а-яё](?:[a-z0-9а-яё-]{0,61}[a-z0-9а-яё])?$")
        if (labels.any { !labelRegex.matches(it) }) return null

        val tld = labels.last()
        val hasCyrillic = cleaned.any { it in 'а'..'я' || it == 'ё' }
        val allowedTlds = setOf(
            "ru", "рф", "su", "com", "net", "org", "io", "dev", "app", "me", "tv",
            "info", "biz", "co", "ai", "pro", "one", "space", "site", "online",
            "store", "tech", "xyz", "club", "cloud", "de", "uk", "nl", "fi", "us",
            "eu", "kz", "by", "ua", "aero", "gov", "edu", "xn--p1ai"
        )

        if (hasCyrillic && tld !in setOf("ru", "рф", "su", "xn--p1ai")) return null
        if (tld !in allowedTlds) return null

        return cleaned
    }
}
