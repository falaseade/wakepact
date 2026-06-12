package app.wakepact.core.util

import kotlin.random.Random

/** 6-char invite codes without visually confusable characters (0/O, 1/I/L). */
object InviteCodes {
    const val LENGTH = 6
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun random(random: Random = Random.Default): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun normalize(input: String): String = input.trim().uppercase()

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}
