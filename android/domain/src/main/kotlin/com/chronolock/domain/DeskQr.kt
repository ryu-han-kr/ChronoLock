package com.chronolock.domain

/**
 * A focus that only stops after scanning a QR code the person printed and put out of reach. The code holds a random
 * secret made on this device; nothing else is in it and nothing is sent anywhere.
 */
object DeskQr {
    private const val PREFIX = "chronolock-desk:"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** A new secret from [random] bytes (at least 16), in letters that read clearly if typed. */
    fun newSecret(random: ByteArray): String {
        require(random.size >= 16) { "QR 코드를 만들지 못했습니다." }
        return random.joinToString("") { ALPHABET[(it.toInt() and 0xFF) % ALPHABET.length].toString() }
    }

    /** What the printed code says. */
    fun payload(secret: String): String = PREFIX + secret

    /** Whether [scanned] is this device's code, not another phone's or an unrelated QR. */
    fun matches(secret: String?, scanned: String?): Boolean =
        secret != null && scanned != null && scanned.trim() == payload(secret)
}
