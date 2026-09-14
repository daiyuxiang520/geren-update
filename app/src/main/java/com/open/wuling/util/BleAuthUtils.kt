package com.open.wuling.util

import android.util.Log
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object BleAuthUtils {
    private const val TAG = "BleAuthUtils"

    const val AUTH_SERVICE_UUID = "0000182A-0000-1000-8000-00805F9B34FB"
    const val CMD_SERVICE_UUID = "0000181A-0000-1000-8000-00805F9B34FB"
    const val AUTH_CHAR_UUID = "00002A7F-0000-1000-8000-00805F9B34FB"
    const val CMD_CHAR_UUID = "00002A7E-0000-1000-8000-00805F9B34FB"
    const val NOTIFY_CHAR_UUID = "00002A6F-0000-1000-8000-00805F9B34FB"
    const val NOTIFY_CHAR_UUID_2 = "00002A7F-0000-1000-8000-00805F9B34FB"
    // v2.0.0: 鉴权写入通道 (hello/challenge走这个，不是0x2A7F)
    const val AUTH_WRITE_CHAR_UUID = "00002A6E-0000-1000-8000-00805F9B34FB"

    private const val HEADER = 0xAA.toByte()
    private const val TAIL = 0x55.toByte()

    data class AuthPacket(
        val keyId: ByteArray,
        val timestamp: ByteArray,
        val nonce: ByteArray,
        val crc32: ByteArray
    ) {
        fun toByteArray(): ByteArray {
            return keyId + timestamp + nonce + crc32
        }

        companion object {
            fun fromByteArray(data: ByteArray): AuthPacket? {
                if (data.size != 28) return null
                return AuthPacket(
                    keyId = data.copyOfRange(0, 4),
                    timestamp = data.copyOfRange(4, 8),
                    nonce = data.copyOfRange(8, 24),
                    crc32 = data.copyOfRange(24, 28)
                )
            }
        }
    }

    fun generateAuthPacket(keyIdHex: String, nonceHex: String): AuthPacket {
        val keyId = hexStringToByteArray(keyIdHex)
        val timestamp = (System.currentTimeMillis() / 1000).toInt().toByteArray()
        val nonce = hexStringToByteArray(nonceHex)
        
        val dataWithoutCrc = keyId + timestamp + nonce
        val crc32 = calculateCRC32(dataWithoutCrc).toByteArray()
        
        return AuthPacket(keyId, timestamp, nonce, crc32)
    }

    fun encryptAesEcb(data: ByteArray, keyHex: String): ByteArray? {
        return try {
            val key = hexStringToByteArray(keyHex)
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "AES 加密失败", e)
            null
        }
    }

    fun decryptAesEcb(encryptedData: ByteArray, keyHex: String): ByteArray? {
        return try {
            val key = hexStringToByteArray(keyHex)
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "AES 解密失败", e)
            null
        }
    }

    fun wrapProtocolFrame(data: ByteArray): ByteArray {
        val length = data.size.toShort().toByteArray()
        val checksum = calculateChecksum(data)
        
        val frame = ByteArray(1 + 2 + data.size + 1 + 1)
        frame[0] = HEADER
        System.arraycopy(length, 0, frame, 1, 2)
        System.arraycopy(data, 0, frame, 3, data.size)
        frame[3 + data.size] = checksum
        frame[3 + data.size + 1] = TAIL
        
        return frame
    }

    fun unwrapProtocolFrame(frame: ByteArray): ByteArray? {
        Log.d(TAG, "收到原始数据（${frame.size}字节）: ${frame.joinToString("") { "%02X".format(it) }}")
        
        if (frame.size < 5) {
            Log.e(TAG, "协议帧太短: ${frame.size}")
            return null
        }
        
        val headerIndex = frame.indexOfFirst { it == HEADER }
        if (headerIndex == -1) {
            Log.e(TAG, "找不到帧头 0xAA")
            return null
        }
        Log.d(TAG, "帧头位置: $headerIndex")
        
        val tailIndex = frame.indexOfLast { it == TAIL }
        if (tailIndex == -1) {
            Log.e(TAG, "找不到帧尾 0x55")
            return null
        }
        Log.d(TAG, "帧尾位置: $tailIndex")
        
        if (tailIndex - headerIndex < 5) {
            Log.e(TAG, "帧头帧尾之间数据太短")
            return null
        }
        
        val validFrame = frame.copyOfRange(headerIndex, tailIndex + 1)
        Log.d(TAG, "有效帧（${validFrame.size}字节）: ${validFrame.joinToString("") { "%02X".format(it) }}")
        
        val length = ((validFrame[1].toInt() and 0xFF) shl 8) or (validFrame[2].toInt() and 0xFF)
        Log.d(TAG, "Length字段: $length")
        
        if (validFrame.size < 3 + length + 2) {
            Log.e(TAG, "帧长度不足: 需要 ${3 + length + 2}, 实际 ${validFrame.size}")
            return null
        }
        
        val data = validFrame.copyOfRange(3, 3 + length)
        val checksum = validFrame[3 + length]
        
        Log.d(TAG, "提取的数据（${data.size}字节）: ${data.joinToString("") { "%02X".format(it) }}")
        Log.d(TAG, "Checksum: 0x%02X".format(checksum.toInt() and 0xFF))
        Log.d(TAG, "计算Checksum: 0x%02X".format(calculateChecksum(data).toInt() and 0xFF))
        
        if (calculateChecksum(data) != checksum) {
            Log.e(TAG, "Checksum 校验失败")
            return null
        }
        
        return data
    }

    fun verifyAuthPacket(packet: AuthPacket, expectedKeyIdHex: String, expectedNonceHex: String): Boolean {
        val expectedKeyId = hexStringToByteArray(expectedKeyIdHex)
        if (!packet.keyId.contentEquals(expectedKeyId)) {
            Log.e(TAG, "KeyId 不匹配")
            return false
        }
        
        val expectedNonce = hexStringToByteArray(expectedNonceHex)
        if (!packet.nonce.contentEquals(expectedNonce)) {
            Log.e(TAG, "Nonce 不匹配")
            return false
        }
        
        val dataWithoutCrc = packet.keyId + packet.timestamp + packet.nonce
        val calculatedCrc = calculateCRC32(dataWithoutCrc).toByteArray()
        if (!packet.crc32.contentEquals(calculatedCrc)) {
            Log.e(TAG, "CRC32 校验失败")
            return false
        }
        
        return true
    }

    private fun calculateCRC32(data: ByteArray): Long {
        val crc32 = CRC32()
        crc32.update(data)
        return crc32.value
    }

    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (byte in data) {
            sum += byte.toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }

    private fun Int.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        )
    }

    private fun Short.toByteArray(): ByteArray {
        return byteArrayOf(
            (this.toInt() shr 8).toByte(),
            this.toByte()
        )
    }

    private fun Long.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        )
    }

    fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("-", "")
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4)
                    + Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
