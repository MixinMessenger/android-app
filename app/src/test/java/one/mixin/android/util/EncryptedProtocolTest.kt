package one.mixin.android.util

import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import one.mixin.android.crypto.Base64
import one.mixin.android.crypto.EncryptedProtocol
import one.mixin.android.crypto.aesDecrypt
import one.mixin.android.crypto.aesEncrypt
import one.mixin.android.crypto.generateAesKey
import one.mixin.android.crypto.generateEd25519KeyPair
import one.mixin.android.crypto.publicKeyToCurve25519
import one.mixin.android.extension.base64Encode
import one.mixin.android.extension.toByteArray
import one.mixin.android.websocket.AttachmentMessagePayload
import one.mixin.android.websocket.StickerMessagePayload
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptedProtocolTest {

    @Test
    fun testText() {
        val content = "L".toByteArray()
        testEncryptAndDecrypt(content)
    }

    @Test
    fun testSticker() {
        val mockStickerId = UUID.randomUUID().toString()
        val mockStickerPayload = StickerMessagePayload(mockStickerId)
        val content = GsonHelper.customGson.toJson(mockStickerPayload).base64Encode().toByteArray()
        testEncryptAndDecrypt(content)
    }

    @Test
    fun testAes() {
        val content = "LA".toByteArray()
        val aesGcmKey = generateAesKey()
        val encodedContent = aesEncrypt(aesGcmKey, content)
        val decryptedContent = aesDecrypt(
            aesGcmKey,
            encodedContent.slice(IntRange(0, 15)).toByteArray(),
            encodedContent.slice(IntRange(16, encodedContent.size - 1)).toByteArray(),
        )
        assertEquals("LA", String(decryptedContent))
    }

    @Test
    fun testImage() {
        val mockAttachmentMessagePayload = AttachmentMessagePayload(
            key = Base64.decode("2IFv82k/nPZJlQFYRCD7SgWNtDK+Bi5vo0VXhk4A9DAp/RE5r+Shfgn+xEuQiyn8Hjf+Ox9356geoceH926BJQ=="),
            digest = Base64.decode("z9YuqavioY+hYLB1slFaRzSc9ggBlp+nUOZGHwS8LaU="),
            attachmentId = "5a3574ca-cc17-470d-88dc-845613d471b4",
            mimeType = "image/jpeg",
            height = 949,
            width = 1080,
            size = 168540,
            name = null,
            thumbnail = """/9j/4AAQSkZJRgABAQAAAQABAAD/4gIoSUNDX1BST0ZJTEUAAQEAAAIYAAAAAAIQAABtbnRyUkdCIFhZWiAAAAAAAAAAAAAAAABhY3
                NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
                lkZXNjAAAA8AAAAHRyWFlaAAABZAAAABRnWFlaAAABeAAAABRiWFlaAAABjAAAABRyVFJDAAABoAAAAChnVFJDAAABoAAAAChiVFJDAAABoAAAACh3dHB0AAABy
                AAAABRjcHJ0AAAB3AAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAFgAAAAcAHMAUgBHAEIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
                AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFhZWiAAAAAAAABvogAAOPUAAAOQWFlaIAAAAAAAAGKZAAC3hQAAGNpYWVogAAAAAAAAJKAAAA
                +EAAC2z3BhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABYWVogAAAAAAAA9tYAAQAAAADTLW1sdWMAAAAAAAAAAQAAAAxlblVTAAAAIAAAABwA
                RwBvAG8AZwBsAGUAIABJAG4AYwAuACAAMgAwADEANv/bAEMAAwICAwICAwMDAwQDAwQFCAUFBAQFCgcHBggMCgwMCwoLCw0OEhANDhEOCwsQFhARExQVFRUMDxcYFh
                QYEhQVFP/bAEMBAwQEBQQFCQUFCRQNCw0UFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFP/AABEIADcAPwMBIgACEQEDEQH/
                xAAbAAACAwEBAQAAAAAAAAAAAAAFBgAECAMHAf/EACsQAAEDAwIEBgIDAAAAAAAAAAEAAgMEBREGEgchMZETFCIyUaEjQRYzQ//EABkBAAMBAQEAAAAAAAAAAAAAAA
                IDBAEABf/EAB4RAAICAwEAAwAAAAAAAAAAAAABAhEDEiExBDJR/9oADAMBAAIRAxEAPwABWcHGsB/GOyXLjwl2ZwwdlpG4VMDwdu1LVfGyQnACli2PcfwzhW8L3NziM
                dkKm4YSHpF9LRFVbml3QKq22sz7QjcqDWK1ZnaThdLn+n6VeThRLJ/iey0xHa4nOGWDsilHYqZ5G6NvZEm2IlGjO2kOFUtPVsd4JGD8LWvCuwvttExpaR6cLlZrFRR
                vB2NXoVn8vSxANwOSkzp0FDh4NS3h1UB6lZMpPPckS3XYQt5uVt+pmtB9f2mvjH3rGxnqJOR5obJVFruTkCl1IDETvQiq1Mxp96H7CsefaWo7w3N7D1XV+qvKjm4DC
                8/GqWBmd4SpqLWfhhwD/tPhJeFeXG6s9pp+JLYZAN47pio+JzS0fk/XysjN1o51R7/38or/ADt8IHrPT5Q5e8IfBnqbm6EHBKXqnUUjHO9RRiekfKDyKWbpbJGhxw
                UxxsO7jR0fql/hEb/tBazUUrskOKpupJXPI5q7DYHzR5IWKFEsFrKwfJqmWNhG4pbu+oZKgn1FH7tp18TTyStVWh4kxhCoU7PTl8jaNFWC4yCTOSrc1zkf0X2Gzu
                B9qsstbt3RH6Q+mmpraIAcgJZu8LTuGAoojTDfBd8g0y5wEWpYWsYBhRRaKKV2p2SM6JPrbe3xeiiixnHyGhbu6BdPIgSHkFFEs4//2Q==""".trimMargin()
        )
        val content = GsonHelper.customGson.toJson(mockAttachmentMessagePayload).toByteArray()
        testEncryptAndDecrypt(content)
    }

    private fun testEncryptAndDecrypt(content: ByteArray) {
        val otherSessionId = UUID.randomUUID().toString()
        val encryptedProtocol = EncryptedProtocol()

        val senderKeyPair = generateEd25519KeyPair()
        val senderPrivateKey = senderKeyPair.private as EdDSAPrivateKey

        val receiverKeyPair = generateEd25519KeyPair()
        val receiverPrivateKey = receiverKeyPair.private as EdDSAPrivateKey
        val receiverPublicKey = receiverKeyPair.public as EdDSAPublicKey
        val receiverCurvePublicKey = publicKeyToCurve25519(receiverPublicKey)

        val encodedContent = encryptedProtocol.encryptMessage(senderPrivateKey, content, receiverCurvePublicKey, otherSessionId)

        val decryptedContent = encryptedProtocol.decryptMessage(receiverPrivateKey, UUID.fromString(otherSessionId).toByteArray(), encodedContent)

        assert(decryptedContent.contentEquals(content))
    }
}
