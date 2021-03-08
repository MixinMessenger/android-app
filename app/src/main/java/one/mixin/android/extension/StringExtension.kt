@file:Suppress("NOTHING_TO_INLINE")

package one.mixin.android.extension

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import androidx.core.net.toUri
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import okhttp3.internal.and
import okio.Buffer
import okio.ByteString
import okio.GzipSink
import okio.GzipSource
import okio.Source
import okio.buffer
import one.mixin.android.util.GzipException
import org.threeten.bp.Instant
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.Arrays
import java.util.Formatter
import java.util.HashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.roundToInt

private const val QUIET_ZONE_SIZE = 4
private val radii = FloatArray(8)
fun String.generateQRCode(qrSize: Int, padding: Int = 32.dp): Pair<Bitmap, Int> {
    require(isNotEmpty()) { "Found empty contents" }
    require(qrSize >= 0) { "Requested dimensions are too small: $qrSize" }
    var errorCorrectionLevel = ErrorCorrectionLevel.M
    var quietZone = QUIET_ZONE_SIZE
    val hints = HashMap<EncodeHintType, String>()
    if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
        errorCorrectionLevel = ErrorCorrectionLevel.valueOf(hints[EncodeHintType.ERROR_CORRECTION].toString())
    }
    if (hints.containsKey(EncodeHintType.MARGIN)) {
        quietZone = hints[EncodeHintType.MARGIN].toString().toInt()
    }
    val code = Encoder.encode(this, errorCorrectionLevel, hints)
    val input = code.matrix
    checkNotNull(input)
    val inputWidth = input.width
    val inputHeight = input.height
    var sideQuadSize = 0
    for (x in 0 until inputWidth) {
        if (has(input, x, 0, 0, sideQuadSize, 0)) {
            sideQuadSize++
        } else {
            break
        }
    }
    val qrWidth = inputWidth + quietZone * 2
    val qrHeight = inputHeight + quietZone * 2
    val outputWidth = qrSize.coerceAtLeast(qrWidth)
    val outputHeight = qrSize.coerceAtLeast(qrHeight)
    val multiple = (outputWidth / qrWidth).coerceAtMost(outputHeight / qrHeight)
    val size = multiple * inputWidth + padding * 2
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val canvas = Canvas(bitmap)

    blackPaint.color = Color.WHITE
    blackPaint.style = Paint.Style.FILL
    canvas.drawRoundRect(
        RectF(padding / 2f, padding / 2f, size.toFloat() - padding / 2f, size.toFloat() - padding / 2f),
        padding / 2f,
        padding / 2f,
        blackPaint
    )
    blackPaint.color = Color.BLACK
    val rect = GradientDrawable()
    rect.shape = GradientDrawable.RECTANGLE
    rect.cornerRadii = radii
    var imageIgnore = ((size - padding * 2) / 4.65f / multiple).roundToInt()
    if (imageIgnore % 2 != inputWidth % 2) {
        imageIgnore++
    }
    val imageBlockX = (inputWidth - imageIgnore) / 2
    for (a in 0..2) {
        var x: Int
        var y: Int
        when (a) {
            0 -> {
                x = padding
                y = padding
            }
            1 -> {
                x = size - sideQuadSize * multiple - padding
                y = padding
            }
            else -> {
                x = padding
                y = size - sideQuadSize * multiple - padding
            }
        }
        var r = sideQuadSize * multiple / 3.0f
        Arrays.fill(radii, r)
        rect.setColor(-0x1000000)
        rect.setBounds(x, y, x + sideQuadSize * multiple, y + sideQuadSize * multiple)
        rect.draw(canvas)
        canvas.drawRect(
            (x + multiple).toFloat(),
            (y + multiple).toFloat(),
            (x + (sideQuadSize - 1) * multiple).toFloat(),
            (y + (sideQuadSize - 1) * multiple).toFloat(),
            blackPaint
        )
        r = sideQuadSize * multiple / 4.0f
        Arrays.fill(radii, r)
        rect.setColor(-0x1)
        rect.setBounds(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple)
        rect.draw(canvas)
        r = (sideQuadSize - 2) * multiple / 4.0f
        Arrays.fill(radii, r)
        rect.setColor(-0x1000000)
        rect.setBounds(x + multiple * 2, y + multiple * 2, x + (sideQuadSize - 2) * multiple, y + (sideQuadSize - 2) * multiple)
        rect.draw(canvas)
    }
    var y = 0
    var outputY = padding
    while (y < inputHeight) {
        var x = 0
        var outputX = padding
        while (x < inputWidth) {
            if (has(input, imageBlockX, imageIgnore, sideQuadSize, x, y)) {
                canvas.drawCircle(outputX + multiple / 2f, outputY + multiple / 2f, multiple / 2f, blackPaint)
            }
            x++
            outputX += multiple
        }
        y++
        outputY += multiple
    }
    canvas.setBitmap(null)
    return Pair(bitmap, imageIgnore * multiple - 2.dp)
}

private fun has(input: ByteMatrix, imageBlockX: Int, imageBloks: Int, sideQuadSize: Int, x: Int, y: Int): Boolean {
    if (x >= imageBlockX && x < imageBlockX + imageBloks && y >= imageBlockX && y < imageBlockX + imageBloks) {
        return false
    }
    if ((x < sideQuadSize || x >= input.width - sideQuadSize) && y < sideQuadSize) {
        return false
    }
    return if (x < sideQuadSize && y >= input.height - sideQuadSize) {
        false
    } else x >= 0 && y >= 0 && x < input.width && y < input.height && input[x, y].toInt() == 1
}

fun String.getEpochNano(): Long {
    val inst = Instant.parse(this)
    var time = inst.epochSecond
    time *= 1000000000L
    time += inst.nano
    return time
}

@Throws(IOException::class)
fun String.gzip(): ByteString {
    val result = Buffer()
    val sink = GzipSink(result).buffer()
    sink.use {
        sink.write(toByteArray())
    }
    return result.readByteString()
}

@Throws(GzipException::class)
fun ByteString.ungzip(): String {
    val buffer = Buffer().write(this)
    val gzip = GzipSource(buffer as Source)
    return gzip.buffer().readUtf8()
}

inline fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digested = md.digest(toByteArray())
    return digested.joinToString("") {
        String.format("%02x", it)
    }
}

inline fun String.sha256(): ByteArray {
    val md = MessageDigest.getInstance("SHA256")
    return md.digest(toByteArray())
}

inline fun String.isWebUrl(): Boolean {
    return startsWith("http://", true) || startsWith("https://", true)
}

inline fun <reified T> Gson.fromJson(json: JsonElement) = this.fromJson<T>(json, object : TypeToken<T>() {}.type)!!

private val HEX_CHARS = "0123456789abcdef"
fun ByteArray.toHex(): String {
    val hex = HEX_CHARS.toCharArray()
    val result = StringBuffer()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(hex[firstIndex])
        result.append(hex[secondIndex])
    }

    return result.toString()
}

fun String.hexStringToByteArray(): ByteArray {
    val result = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i])
        val secondIndex = HEX_CHARS.indexOf(this[i + 1])

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }
    return result
}

inline fun Long.toLeByteArray(): ByteArray {
    var num = this
    val result = ByteArray(8)
    for (i in (0..7)) {
        result[i] = (num and 0xffL).toByte()
        num = num shr 8
    }
    return result
}

@ExperimentalUnsignedTypes
fun toLeByteArray(v: UInt): ByteArray {
    val b = ByteArray(2)
    b[0] = v.toByte()
    b[1] = (v shr 8).toByte()
    return b
}

@ExperimentalUnsignedTypes
fun leByteArrayToInt(bytes: ByteArray): UInt {
    return bytes[0].toUInt() + (bytes[1].toUInt() shl 8)
}

fun UUID.toByteArray(): ByteArray {
    val bb = ByteBuffer.wrap(ByteArray(16))
    bb.putLong(this.mostSignificantBits)
    bb.putLong(this.leastSignificantBits)
    return bb.array()
}

fun String.formatPublicKey(): String {
    if (this.length <= 10) return this
    return substring(0, 6) + "..." + substring(length - 4, length)
}

fun String.numberFormat(): String {
    if (this.isEmpty()) return this

    return try {
        val big = BigDecimal(this)
        DecimalFormat(big.toPlainString().getPattern(32)).format(big)
    } catch (e: NumberFormatException) {
        this
    } catch (e: IllegalArgumentException) {
        this
    }
}

fun String.numberFormat8(): String {
    if (this.isEmpty()) return this

    return try {
        val big = BigDecimal(this)
        DecimalFormat(big.toPlainString().getPattern()).format(big)
    } catch (e: NumberFormatException) {
        this
    } catch (e: IllegalArgumentException) {
        this
    }
}

fun String.numberFormat2(): String {
    if (this.isEmpty()) return this

    return try {
        val big = BigDecimal(this)
        DecimalFormat(",###.##").format(big)
    } catch (e: NumberFormatException) {
        this
    } catch (e: IllegalArgumentException) {
        this
    }
}

fun String.priceFormat2(): String {
    return try {
        val big = BigDecimal(this)
        DecimalFormat(",##0.00").format(big)
    } catch (e: NumberFormatException) {
        this
    } catch (e: IllegalArgumentException) {
        this
    }
}

fun BigDecimal.priceFormat(): String {
    return if (this.compareTo(BigDecimal.ONE) == 1 || this.compareTo(BigDecimal.ONE) == 0) {
        priceFormat2()
    } else {
        numberFormat8()
    }
}

fun BigDecimal.numberFormat8(): String {
    return try {
        DecimalFormat(this.toPlainString().getPattern()).format(this)
    } catch (e: NumberFormatException) {
        this.toPlainString()
    } catch (e: IllegalArgumentException) {
        this.toPlainString()
    }
}

fun BigDecimal.priceFormat2(): String {
    return try {
        DecimalFormat(",##0.00").format(this)
    } catch (e: NumberFormatException) {
        this.toPlainString()
    } catch (e: IllegalArgumentException) {
        this.toPlainString()
    }
}

fun BigDecimal.numberFormat2(): String {
    return try {
        DecimalFormat(",###.##").format(this)
    } catch (e: NumberFormatException) {
        this.toPlainString()
    } catch (e: IllegalArgumentException) {
        this.toPlainString()
    }
}

fun String.getPattern(count: Int = 8): String {
    if (this.isEmpty()) return ""

    val index = this.indexOf('.')
    if (index == -1) return ",###"
    if (index >= count) return ",###"

    val bit = if (index == 1 && this[0] == '0')
        count + 1
    else if (index == 2 && this[0] == '-' && this[1] == '0')
        count + 2
    else count

    val sb = StringBuilder(",###.")
    for (i in 0 until (bit - index)) {
        sb.append('#')
    }
    return sb.toString()
}

fun Long.formatMillis(): String {
    val formatBuilder = StringBuilder()
    val formatter = Formatter(formatBuilder, Locale.getDefault())
    Util.getStringForTime(formatBuilder, formatter, this)
    return formatBuilder.toString()
}

fun Editable.maxDecimal(bit: Int = 8) {
    val index = this.indexOf('.')
    if (index > -1) {
        val max = if (index == 1 && this[0] == '0')
            bit
        else if (index == 2 && this[0] == '-' && this[1] == '0')
            bit + 1
        else bit - 1
        if (this.length - 1 - index > max) {
            this.delete(this.length - 1, this.length)
        }
    }
}

fun String.checkNumber(): Boolean {
    return try {
        BigDecimal(this)
        true
    } catch (e: NumberFormatException) {
        false
    }
}

val idAvatarCodeMap = ConcurrentHashMap<String, Int>()
val idNameCodeMap = ConcurrentHashMap<String, Int>()

sealed class CodeType(val count: Int) {
    class Name(count: Int) : CodeType(count)
    class Avatar(count: Int) : CodeType(count)
}

fun String.getColorCode(codeType: CodeType): Int {
    val cacheMap = when (codeType) {
        is CodeType.Name -> idNameCodeMap
        is CodeType.Avatar -> idAvatarCodeMap
    }
    var code = cacheMap[this]
    if (code != null) return code

    val hashcode = try {
        UUID.fromString(this).hashCode()
    } catch (e: IllegalArgumentException) {
        hashCode()
    }
    code = abs(hashcode).rem(codeType.count)
    cacheMap[this] = code
    return code
}

inline fun <reified T : Serializable> String.deserialize(): T? {
    if (isNullOrEmpty()) return null

    try {
        ObjectInputStream(ByteArrayInputStream(this.toByteArray(charset("ISO-8859-1")))).use {
            return it.readObject() as T
        }
    } catch (e: Exception) {
        throw IOException("Deserialization error: ${e.message}, $e")
    }
}

inline fun <T : Serializable> T.serialize(): String? {
    try {
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use {
            it.writeObject(this)
        }
        return baos.toString("ISO-8859-1")
    } catch (e: Exception) {
        throw IOException("Serialization error: ${e.message}, $e")
    }
}

private val escapeSqlChars = charArrayOf('\\', '%', '_', '[', ']')

fun String.escapeSql(): String {
    var result = this
    escapeSqlChars.forEach { c ->
        result = result.replace(c.toString(), "\\$c")
    }
    return result
}

fun String.getBotNumber(): String? {
    if (this.startsWith("@7000")) {
        val pattern = Pattern.compile("^@7000\\d* ")
        val matcher = pattern.matcher(this)
        if (matcher.find()) {
            return matcher.group().substring(1, matcher.end() - 1)
        }
    }
    return null
}

inline fun String?.getDeviceId(): Int {
    return if (this == null || this.isEmpty()) {
        1
    } else {
        UUID.fromString(this).hashCode()
    }
}

fun String.filterNonAscii() = replace("[^\\p{ASCII}]".toRegex(), "")

fun String.postOptimize(): String {
    return split("\n").take(20).joinToString("\n").postLengthOptimize()
}

fun String.postLengthOptimize(): String {
    return if (length > 1024) {
        substring(0, 1024)
    } else {
        this
    }
}

fun String.maxLimit(maxSize: Int = 64 * 1024): String {
    if (length < 32 * 1024) return this
    val bytes = toByteArray()
    return String(bytes.take(maxSize).toByteArray())
}

fun String?.joinWhiteSpace(): String {
    if (this == null) return ""

    return joinWithCharacter(' ')
}

fun String.replaceQuotationMark(): String {
    return this.replace("\"", "")
}

fun String.joinStar() = joinWithCharacter('*')

fun String.joinWithCharacter(char: Char): String {
    val result = StringBuilder()
    this.trim().forEachIndexed { i, c ->
        val lookAhead = try {
            this[i + 1]
        } catch (ignored: IndexOutOfBoundsException) {
            char
        }
        val isSameType = if (c.isAlphabet() && lookAhead.isAlphabet()) {
            true
        } else {
            c.isDigit() && lookAhead.isDigit()
        }

        val needWhiteSpace = !isSameType && !c.isWhitespace()
        result.append(c)
        if (needWhiteSpace) {
            result.append(char)
        }
    }
    return result.toString().trim()
}

private fun Char.isAlphabet() = this in 'a'..'z' || this in 'A'..'Z'

fun String.appendQueryParamsFromOtherUri(otherUri: Uri, exclusiveKey: String = "action"): String =
    this.toUri().appendQueryParamsFromOtherUri(otherUri, exclusiveKey)

fun Uri.appendQueryParamsFromOtherUri(otherUri: Uri, exclusiveKey: String = "action"): String {
    val builder = this.buildUpon()
    otherUri.queryParameterNames
        .filter { it != exclusiveKey }
        .forEach { key ->
            val value = otherUri.getQueryParameter(key)
            builder.appendQueryParameter(key, value)
        }
    return builder.build().toString()
}
