package com.open.wuling.util

import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * v2.0.0 NativeFreeProtocol crypto & frame utilities.
 *
 * Replaces the old 0xAA/0x55 framing + CRC32 approach with the
 * hello/challenge + AES-128-ECB/NoPadding + CRC-16-CCITT protocol
 * reverse-engineered from 50Car_2.0.0 smali (Lj2/b, X.smali).
 */
object NativeFreeProtocolUtils {
    private const val TAG = "NativeFreeProtocol"

    // ── Frame size ───────────────────────────────────────────────
    const val FRAME_SIZE = 32
    const val PROTOCOL_VERSION: Byte = 0x06

    // ── Auth frame prefixes ──────────────────────────────────────
    val HELLO_PREFIX   = byteArrayOf(0x38.toByte(), 0xC7.toByte(), 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)
    val CHALLENGE_HDR  = byteArrayOf(0x38.toByte(), 0xC7.toByte(), 0x00, 0x02)

    // ── Control frame prefix ─────────────────────────────────────
    val CONTROL_PREFIX = byteArrayOf(0x39.toByte(), 0xD6.toByte(), 0x00, 0x01, 0x00, 0x00, 0x00, 0x00)

    // ── Lock/unlock markers (6 bytes) ────────────────────────────
    // v2.0.0: CLOSE_DOOR(Lock)→Lj2/b;->e=0102, OPEN_DOOR(Unlock)→Lj2/b;->d=0101
    val LOCK_MARKER   = hexToBytes("0102F2000000")
    val UNLOCK_MARKER = hexToBytes("0101F2000000")

    private const val CRC_OFFSET = 23

    // ── CRC-16-CCITT (polynomial 0x1021, initial 0xFFFF) ─────────

    fun crc16ccitt(bytes: ByteArray, len: Int = minOf(bytes.size, CRC_OFFSET)): Int {
        var crc = 0xFFFF
        for (i in 0 until len) {
            crc = crc xor ((bytes[i].toInt() and 0xFF) shl 8)
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) else (crc shl 1)
            }
        }
        return crc and 0xFFFF
    }

    fun writeCrc(crc: Int, bytes: ByteArray) {
        bytes[CRC_OFFSET] = ((crc shr 8) and 0xFF).toByte()
        bytes[CRC_OFFSET + 1] = (crc and 0xFF).toByte()
    }

    // ── Big-endian helpers ───────────────────────────────────────

    fun writeIntBE(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset]     = ((value shr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 8)  and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }

    fun readIntBE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8)  or
        (bytes[offset + 3].toInt() and 0xFF)

    // ── AES-128-ECB/NoPadding ────────────────────────────────────

    @Volatile private var cipher: Cipher? = null
    private fun getCipher() = cipher ?: Cipher.getInstance("AES/ECB/NoPadding").also { cipher = it }

    fun aesEcbDecrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == 16 && ciphertext.size % 16 == 0)
        val c = getCipher()
        synchronized(c) { c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES")); return c.doFinal(ciphertext) }
    }

    fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 16 && plaintext.size % 16 == 0)
        val c = getCipher()
        synchronized(c) { c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); return c.doFinal(plaintext) }
    }

    // ── Key derivation ───────────────────────────────────────────

    /** authSessionKey = masterKey XOR masterRandom (16 bytes each) */
    fun deriveAuthSessionKey(masterKeyHex: String, masterRandomHex: String): ByteArray {
        val mk = hexToBytes(masterKeyHex)
        val mr = hexToBytes(masterRandomHex)
        require(mk.size == 16 && mr.size == 16)
        return ByteArray(16) { i -> (mk[i].toInt() xor mr[i].toInt()).toByte() }
    }

    /** v2.0.0 control frame encryption key: [r1,r2,r1,r2] (16 bytes, Lj2/b;->a) */
    fun deriveSessionIv(r1: Int, r2: Int): ByteArray {
        val iv = ByteArray(16)
        writeIntBE(iv, 0, r1.toLong())
        writeIntBE(iv, 4, r2.toLong())
        writeIntBE(iv, 8, r1.toLong())
        writeIntBE(iv, 12, r2.toLong())
        return iv
    }

    /** bleKey → last 8 hex chars → 4 bytes */
    fun deriveBleKeyBytes(bleKey: String): ByteArray {
        val f = bleKey.uppercase(Locale.US).filter { it in '0'..'9' || it in 'A'..'F' }
        return hexToBytes(f.takeLast(8).padStart(8, '0'))
    }

    // ── Hex ──────────────────────────────────────────────────────

    fun hexToBytes(hex: String): ByteArray {
        val s = hex.replace(" ", "").replace("-", "")
        return ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
        }
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

    // ── Hello frame builder ──────────────────────────────────────

    fun buildHelloFrame(unixTime: Long, bleKeyBytes: ByteArray): ByteArray {
        val buf = ByteArray(FRAME_SIZE)
        System.arraycopy(HELLO_PREFIX, 0, buf, 0, 8)
        writeIntBE(buf, 8, unixTime and 0xFFFFFFFFL)
        System.arraycopy(bleKeyBytes, 0, buf, 12, minOf(bleKeyBytes.size, 4))
        buf[16] = PROTOCOL_VERSION
        writeCrc(crc16ccitt(buf), buf)
        return buf
    }

    // ── Challenge reply builder ──────────────────────────────────

    fun buildChallengeReply(
        authSessionKey: ByteArray,
        authRandom2Local: Int,
        authRandom1Remote: Int,
        bleKeyBytes: ByteArray
    ): ByteArray {
        val buf = ByteArray(FRAME_SIZE)
        System.arraycopy(CHALLENGE_HDR, 0, buf, 0, 4)           // [0-3]: 38C7 0002
        writeIntBE(buf, 4, authRandom2Local.toLong())            // [4-7]: random2Local
        writeIntBE(buf, 8, authRandom1Remote.toLong())           // [8-11]: random1Remote
        System.arraycopy(bleKeyBytes, 0, buf, 12, minOf(bleKeyBytes.size, 4)) // [12-15]: bleKey
        buf[16] = PROTOCOL_VERSION                                // [16]: 0x06
        // [17-22] remain zero
        writeCrc(crc16ccitt(buf), buf)                            // [23-24]: CRC
        return aesEcbEncrypt(authSessionKey, buf)
    }

    // ── Control frame builder (lock/unlock) ──────────────────────

    fun buildControlFrame(
        authSessionKey: ByteArray,
        marker: ByteArray,    // LOCK_MARKER or UNLOCK_MARKER
        bleKeyBytes: ByteArray,
        controlRandom: Int
    ): ByteArray {
        val buf = ByteArray(FRAME_SIZE)
        System.arraycopy(CONTROL_PREFIX, 0, buf, 0, 8)
        writeIntBE(buf, 8, controlRandom.toLong())
        System.arraycopy(bleKeyBytes, 0, buf, 12, minOf(bleKeyBytes.size, 4))
        buf[16] = PROTOCOL_VERSION
        System.arraycopy(marker, 0, buf, 17, 6)
        writeCrc(crc16ccitt(buf), buf)
        return aesEcbEncrypt(authSessionKey, buf)
    }
}
