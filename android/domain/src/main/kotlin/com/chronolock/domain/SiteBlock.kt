package com.chronolock.domain

/** A website blocked in supported browsers; [domain] also covers its subdomains (youtube.com blocks m.youtube.com). */
data class BlockedSite(val domain: String, val enabled: Boolean = true) {
    init { require(SiteDomains.valid(domain)) }
}

object SiteDomains {
    const val MAX_SITES = 50
    const val INVALID = "youtube.com처럼 사이트 주소를 입력해 주세요."
    private val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun valid(domain: String): Boolean {
        val labels = domain.split('.')
        return domain.length <= 253 && labels.size >= 2 && labels.all { label.matches(it) } && labels.last().any(Char::isLetter)
    }

    /** What the person typed ("https://www.YouTube.com/watch?v=1") as the domain to store ("youtube.com"). */
    fun normalize(input: String): String = requireNotNull(hostOf(input)?.removePrefix("www.")?.takeIf(::valid)) { INVALID }

    /**
     * The host in a browser address bar, which may omit the scheme ("m.youtube.com/watch") or show a search
     * phrase instead of an address; anything that is not a host gives null.
     */
    fun hostOf(text: String): String? {
        val trimmed = text.trim().lowercase()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
        val afterScheme = trimmed.substringAfter("://", trimmed)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':').trimEnd('.')
        return host.takeIf(::valid)
    }

    /** The enabled entry of [sites] that covers [host], if any. */
    fun match(host: String, sites: List<BlockedSite>): BlockedSite? =
        sites.firstOrNull { it.enabled && (host == it.domain || host.endsWith("." + it.domain)) }
}

/** Address bars of supported browsers by package; their text is read only while the browser is on screen. */
object Browsers {
    val urlBarIds: Map<String, String> = listOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "com.brave.browser",
        "com.microsoft.emmx", "com.naver.whale", "com.vivaldi.browser",
    ).associateWith { "$it:id/url_bar" } + mapOf(
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.sec.android.app.sbrowser.beta" to "com.sec.android.app.sbrowser.beta:id/location_bar_edit_text",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
    )
}
