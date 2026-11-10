package com.jarvis.core.security

/**
 * Global Safety Rule 3 + 7.
 *
 * Every string that leaves the phone (AI request) and every string written to
 * the audit log passes through here first. The redactor is deliberately
 * conservative: when in doubt it masks more, never less.
 *
 * Order matters. Secrets and e-mail are handled first, then long digit runs
 * (card numbers), then OTP-like digits that sit next to a keyword, then phone
 * numbers, because a card number also looks like a phone number.
 */
object Redactor {

    data class Outcome(
        val text: String,
        val hits: Int,
        val kinds: List<String>
    ) {
        val changed: Boolean get() = hits > 0
    }

    private const val MASK = "[REDACTED]"

    private val secretPatterns: List<Pair<String, Regex>> = listOf(
        "google_api_key" to Regex("AIza[0-9A-Za-z_\\-]{30,}"),
        "openai_key" to Regex("sk-[A-Za-z0-9_\\-]{16,}"),
        "bearer_token" to Regex("(?i)bearer\\s+[A-Za-z0-9._~+/\\-]{12,}=*"),
        "api_key_assignment" to Regex("(?i)api[_-]?key\\s*[:=]\\s*[A-Za-z0-9._\\-]{12,}")
    )

    private val emailPattern = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    /** 13-19 digit runs, optionally grouped with spaces or dashes. */
    private val cardPattern = Regex("\\b(?:\\d[ \\-]?){13,19}\\b")

    /** OTP / PIN / verification code that appears close to a keyword. */
    private val otpPattern = Regex(
        "(?i)\\b(otp|verification code|one[- ]time code|code|cvv|pin|passcode)\\b" +
            "[^\\n]{0,24}?\\b(\\d{4,8})\\b"
    )

    /** Loose phone candidate; the digit count is validated in the lambda. */
    private val phonePattern = Regex("\\+?\\d[\\d\\s\\-()]{7,18}\\d")

    fun redact(input: String): Outcome {
        if (input.isEmpty()) return Outcome(input, 0, emptyList())

        var text = input
        val kinds = mutableListOf<String>()
        var hits = 0

        for ((name, pattern) in secretPatterns) {
            var count = 0
            text = pattern.replace(text) {
                count++
                MASK
            }
            if (count > 0) {
                hits += count
                kinds += name
            }
        }

        var emailCount = 0
        text = emailPattern.replace(text) {
            emailCount++
            "[EMAIL]"
        }
        if (emailCount > 0) {
            hits += emailCount
            kinds += "email"
        }

        var cardCount = 0
        text = cardPattern.replace(text) { match ->
            val digits = match.value.count { it.isDigit() }
            if (digits in 13..19) {
                cardCount++
                "[CARD]"
            } else {
                match.value
            }
        }
        if (cardCount > 0) {
            hits += cardCount
            kinds += "card"
        }

        var otpCount = 0
        text = otpPattern.replace(text) { match ->
            otpCount++
            val keyword = match.groupValues.getOrNull(1) ?: "code"
            "$keyword [OTP REDACTED]"
        }
        if (otpCount > 0) {
            hits += otpCount
            kinds += "otp"
        }

        var phoneCount = 0
        text = phonePattern.replace(text) { match ->
            val digits = match.value.count { it.isDigit() }
            if (digits in 10..15 && !match.value.contains('@')) {
                phoneCount++
                "[PHONE]"
            } else {
                match.value
            }
        }
        if (phoneCount > 0) {
            hits += phoneCount
            kinds += "phone"
        }

        return Outcome(text, hits, kinds.distinct())
    }

    /** Convenience for log lines where only the text matters. */
    fun redactText(input: String): String = redact(input).text

    /**
     * Masks a phone number for on-screen / spoken output (Privacy Mode).
     * +919876543210 -> +91 98XXX XX210  (keeps the shape, hides the digits).
     */
    fun maskPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return "••••"
        val country = if (raw.trim().startsWith("+")) "+${digits.take(2)} " else ""
        val local = digits.takeLast(9)
        val head = local.take(2)
        val tail = local.takeLast(3)
        return "$country$head" + "XXX XX" + tail
    }
}
