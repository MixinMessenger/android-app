package one.mixin.android.vo

interface ICategory {
    val type: String?
}

fun ICategory.isPlain(): Boolean {
    return type?.startsWith("PLAIN_") == true
}

fun ICategory.isEncrypted(): Boolean {
    return type?.startsWith("ENCRYPTED_") == true
}

fun ICategory.isSignal(): Boolean {
    return type?.startsWith("SIGNAL_") == true
}

fun ICategory.isCall() = type?.startsWith("WEBRTC_") == true || type?.startsWith("KRAKEN_") == true

fun ICategory.isKraken() = type?.startsWith("KRAKEN_") == true

fun ICategory.isRecall() = type == MessageCategory.MESSAGE_RECALL.name

fun ICategory.isFtsMessage() =
    type?.endsWith("_TEXT") == true || type?.endsWith("_DATA") == true || type?.endsWith("_POST") == true

fun ICategory.isText() =
    type == MessageCategory.SIGNAL_TEXT.name || type == MessageCategory.PLAIN_TEXT.name || type == MessageCategory.ENCRYPTED_TEXT.name

fun ICategory.isLive() =
    type == MessageCategory.SIGNAL_LIVE.name || type == MessageCategory.PLAIN_LIVE.name || type == MessageCategory.ENCRYPTED_LIVE.name

fun ICategory.isImage() =
    type == MessageCategory.SIGNAL_IMAGE.name || type == MessageCategory.PLAIN_IMAGE.name || type == MessageCategory.ENCRYPTED_IMAGE.name

fun ICategory.isVideo() =
    type == MessageCategory.SIGNAL_VIDEO.name || type == MessageCategory.PLAIN_VIDEO.name || type == MessageCategory.ENCRYPTED_VIDEO.name

fun ICategory.isSticker() =
    type == MessageCategory.SIGNAL_STICKER.name || type == MessageCategory.PLAIN_STICKER.name || type == MessageCategory.ENCRYPTED_STICKER.name

fun ICategory.isPost() =
    type == MessageCategory.SIGNAL_POST.name || type == MessageCategory.PLAIN_POST.name || type == MessageCategory.ENCRYPTED_POST.name

fun ICategory.isAudio() =
    type == MessageCategory.SIGNAL_AUDIO.name || type == MessageCategory.PLAIN_AUDIO.name || type == MessageCategory.ENCRYPTED_AUDIO.name

fun ICategory.isData() =
    type == MessageCategory.SIGNAL_DATA.name || type == MessageCategory.PLAIN_DATA.name || type == MessageCategory.ENCRYPTED_DATA.name

fun ICategory.isLocation() =
    type == MessageCategory.SIGNAL_LOCATION.name || type == MessageCategory.PLAIN_LOCATION.name || type == MessageCategory.ENCRYPTED_LOCATION.name

fun ICategory.isContact() =
    type == MessageCategory.SIGNAL_CONTACT.name || type == MessageCategory.PLAIN_CONTACT.name || type == MessageCategory.ENCRYPTED_CONTACT.name

fun ICategory.isMedia(): Boolean = isData() || isImage() || isVideo()

fun ICategory.isAttachment(): Boolean = isData() || isImage() || isVideo() || isAudio()

fun ICategory.isGroupCall() = type?.isGroupCallType() == true

fun String.isGroupCallType() =
    this == MessageCategory.KRAKEN_END.name ||
        this == MessageCategory.KRAKEN_DECLINE.name ||
        this == MessageCategory.KRAKEN_CANCEL.name ||
        this == MessageCategory.KRAKEN_INVITE.name

fun ICategory.supportSticker(): Boolean = isSticker() || isImage()

fun ICategory.isCallMessage() =
    type == MessageCategory.WEBRTC_AUDIO_CANCEL.name ||
        type == MessageCategory.WEBRTC_AUDIO_DECLINE.name ||
        type == MessageCategory.WEBRTC_AUDIO_END.name ||
        type == MessageCategory.WEBRTC_AUDIO_BUSY.name ||
        type == MessageCategory.WEBRTC_AUDIO_FAILED.name

fun ICategory.canRecall(): Boolean {
    return type == MessageCategory.SIGNAL_TEXT.name ||
        type == MessageCategory.SIGNAL_IMAGE.name ||
        type == MessageCategory.SIGNAL_VIDEO.name ||
        type == MessageCategory.SIGNAL_STICKER.name ||
        type == MessageCategory.SIGNAL_DATA.name ||
        type == MessageCategory.SIGNAL_CONTACT.name ||
        type == MessageCategory.SIGNAL_AUDIO.name ||
        type == MessageCategory.SIGNAL_LIVE.name ||
        type == MessageCategory.SIGNAL_POST.name ||
        type == MessageCategory.SIGNAL_LOCATION.name ||
        type == MessageCategory.PLAIN_TEXT.name ||
        type == MessageCategory.PLAIN_IMAGE.name ||
        type == MessageCategory.PLAIN_VIDEO.name ||
        type == MessageCategory.PLAIN_STICKER.name ||
        type == MessageCategory.PLAIN_DATA.name ||
        type == MessageCategory.PLAIN_CONTACT.name ||
        type == MessageCategory.PLAIN_AUDIO.name ||
        type == MessageCategory.PLAIN_LIVE.name ||
        type == MessageCategory.PLAIN_POST.name ||
        type == MessageCategory.PLAIN_LOCATION.name ||
        type == MessageCategory.ENCRYPTED_TEXT.name ||
        type == MessageCategory.ENCRYPTED_IMAGE.name ||
        type == MessageCategory.ENCRYPTED_VIDEO.name ||
        type == MessageCategory.ENCRYPTED_STICKER.name ||
        type == MessageCategory.ENCRYPTED_DATA.name ||
        type == MessageCategory.ENCRYPTED_CONTACT.name ||
        type == MessageCategory.ENCRYPTED_AUDIO.name ||
        type == MessageCategory.ENCRYPTED_LIVE.name ||
        type == MessageCategory.ENCRYPTED_POST.name ||
        type == MessageCategory.ENCRYPTED_LOCATION.name ||
        type == MessageCategory.APP_CARD.name
}
