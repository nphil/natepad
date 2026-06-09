package com.natepad.app.data

import kotlinx.serialization.Serializable

@Serializable
data class KeyRecord(
    val id: String,              // fingerprint uppercase, used as stable key
    val fingerprint: String,     // full uppercase hex fingerprint
    val userIds: List<String>,   // e.g. ["Alice <alice@example.com>"]
    val hasPrivate: Boolean,
    val hasPublic: Boolean,
    val armoredPublic: String,
    val armoredPrivate: String,  // empty string if public-only
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Returns the primary User ID string (first entry, or "Unknown Key") */
    val primaryUserId: String
        get() = userIds.firstOrNull() ?: "Unknown Key"

    /** Parses "Name <email>" into a Pair of (name, email). */
    val parsedUser: Pair<String, String>
        get() {
            val uid = primaryUserId
            val emailRegex = Regex("""^(.*?)\s*<([^>]+)>$""")
            val match = emailRegex.find(uid.trim())
            return if (match != null) {
                val name = match.groupValues[1].trim().ifEmpty { "Unknown" }
                val email = match.groupValues[2].trim()
                Pair(name, email)
            } else {
                Pair(uid, "")
            }
        }

    /** Human-readable display name. */
    val displayName: String
        get() = parsedUser.first

    /** Email extracted from UID. */
    val displayEmail: String
        get() = parsedUser.second

    /** Fingerprint split into groups of 4 for readability. */
    val prettyFingerprint: String
        get() = fingerprint.chunked(4).joinToString(" ")

    /** Last 8 hex chars of fingerprint (short key ID). */
    val shortId: String
        get() = if (fingerprint.length >= 8) fingerprint.takeLast(8) else fingerprint

    /** One-line label: "Name <email> · SHORTID" */
    val label: String
        get() {
            val (name, email) = parsedUser
            val idPart = shortId.uppercase()
            return if (email.isNotEmpty()) "$name <$email> · $idPart"
            else "$name · $idPart"
        }
}
