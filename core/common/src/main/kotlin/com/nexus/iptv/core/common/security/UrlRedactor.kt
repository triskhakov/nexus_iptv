package com.nexus.iptv.core.common.security

object UrlRedactor {
    private val SENSITIVE_QUERY_REGEX = Regex("(?i)(password|pass|token|auth|key|secret|pwd|api_key)=([^&\\s]+)")
    private val XTREAM_PATH_REGEX = Regex("(https?://[^/]+/(?:live|movie|series)/)([^/]+)/([^/]+)(/[^\\s]+)")

    fun redact(rawInput: String): String {
        if (rawInput.isBlank()) return rawInput
        val queryMasked = SENSITIVE_QUERY_REGEX.replace(rawInput) { match -> "${match.groupValues[1]}=***REDACTED***" }
        return XTREAM_PATH_REGEX.replace(queryMasked) { match -> "${match.groupValues[1]}***USER***/***PASS***${match.groupValues[4]}" }
    }
}
