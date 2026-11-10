package com.jarvis.core.terminal

/**
 * Decides whether a shell command needs an explicit user confirmation before
 * Jarvis is allowed to run it (module rule 2 + Global Safety Rule 4).
 *
 * The list is intentionally broad: package installs, network downloads,
 * anything that deletes or overwrites, permission changes and process control
 * all stop the run until the user presses "Haan, chalao".
 */
object CommandGuard {

    data class Verdict(
        val dangerous: Boolean,
        val reasons: List<String>,
    ) {
        val reasonText: String get() = reasons.joinToString("; ")
    }

    private data class Rule(
        val pattern: Regex,
        val reason: String,
    )

    private val rules: List<Rule> = listOf(
        Rule(Regex("(^|[;&|(\\s])rm\\s"), "delete (rm)"),
        Rule(Regex("(^|[;&|(\\s])rmdir\\s"), "remove folder (rmdir)"),
        Rule(Regex("(^|[;&|(\\s])mv\\s"), "move / overwrite (mv)"),
        Rule(Regex(">\\s*/?(?!dev/null)"), "overwrite file (>)"),
        Rule(Regex("(^|[;&|(\\s])cp\\s"), "copy / overwrite (cp)"),
        Rule(Regex("(^|[;&|(\\s])chmod\\s"), "permission change (chmod)"),
        Rule(Regex("(^|[;&|(\\s])chown\\s"), "owner change (chown)"),
        Rule(Regex("(^|[;&|(\\s])(pkg|apt|apt-get|dpkg)\\s+(install|add|update|upgrade|remove)"), "package install"),
        Rule(Regex("(^|[;&|(\\s])(pip|pip3)\\s+(install|uninstall|download)"), "python package install"),
        Rule(Regex("(^|[;&|(\\s])(npm|yarn|pnpm|npx)\\s+(install|i|add|create|exec)"), "node package install"),
        Rule(Regex("(^|[;&|(\\s])gem\\s+install"), "ruby package install"),
        Rule(Regex("(^|[;&|(\\s])(curl|wget|nc|ncat|telnet|ssh|scp|sftp|ftp)\\s"), "network access / download"),
        Rule(Regex("(^|[;&|(\\s])git\\s+(clone|pull|fetch|push)"), "git network operation"),
        Rule(Regex("(^|[;&|(\\s])dd\\s"), "raw disk write (dd)"),
        Rule(Regex("(^|[;&|(\\s])(mkfs|format|fdisk|mke2fs)\\b"), "format disk"),
        Rule(Regex("(^|[;&|(\\s])(pm|am)\\s+(uninstall|install|force-stop|clear)"), "android package manager"),
        Rule(Regex("(^|[;&|(\\s])(kill|killall|pkill)\\s"), "kill process"),
        Rule(Regex("(^|[;&|(\\s])(shutdown|reboot|halt|poweroff)\\b"), "shutdown / reboot"),
        Rule(Regex("(^|[;&|(\\s])(sudo|su)\\s"), "privilege escalation"),
        Rule(Regex("(^|[;&|(\\s])export\\s+(PATH|LD_|HOME)"), "environment override"),
        Rule(Regex(":\\s*\\(\\s*\\)\\s*\\{"), "fork bomb"),
        Rule(Regex("(^|[;&|(\\s])unzip\\s+(-o|/)"), "unzip with overwrite"),
    )

    private val shellOperators = Regex("[;&|`\\$]")

    fun inspect(command: String): Verdict {
        val text = command.trim()
        if (text.isEmpty()) return Verdict(false, emptyList())

        val reasons = linkedSetOf<String>()
        rules.forEach { rule ->
            if (rule.pattern.containsMatchIn(text)) reasons += rule.reason
        }
        // A bare pipeline of harmless commands is fine, but chained commands are
        // shown to the user anyway because they hide intent.
        if (shellOperators.containsMatchIn(text) && reasons.isEmpty()) {
            reasons += "chained command"
        }
        return Verdict(reasons.isNotEmpty(), reasons.toList())
    }

    /** Normalised form used for duplicate-command prevention. */
    fun normalise(command: String): String = command
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd(';')

    /** Commands that can never run, even with confirmation. */
    fun isBlocked(command: String): String? {
        val text = command.lowercase()
        return when {
            text.contains("rm -rf /") && !text.contains("workspace") ->
                "Poore system ko delete karne wali command block hai."
            text.contains("/data/data/") && text.contains("rm") ->
                "Doosre apps ka data delete karna allowed nahi hai."
            text.contains("pm uninstall") ->
                "App uninstall command is module se allowed nahi hai."
            text.contains("su -c") || text.startsWith("su ") ->
                "Root command allowed nahi hai."
            else -> null
        }
    }
}
