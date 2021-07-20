package one.mixin.android

import okhttp3.Dns
import one.mixin.android.net.CustomDns
import one.mixin.android.net.SequentialDns

object Constants {

    object API {
        const val DOMAIN = "https://mixin.one"
        const val URL = "https://mixin-api.zeromesh.net/"
        const val WS_URL = "wss://mixin-blaze.zeromesh.net"
        const val Mixin_URL = "https://api.mixin.one/"
        const val Mixin_WS_URL = "wss://blaze.mixin.one"

        const val GIPHY_URL = "http://api.giphy.com/v1/"
        const val FOURSQUARE_URL = "https://api.foursquare.com/v2/"
    }

    object HelpLink {
        const val CENTER = "https://mixinmessenger.zendesk.com"
        const val EMERGENCY = "https://mixinmessenger.zendesk.com/hc/articles/360029154692"
        const val DEPOSIT = "https://mixinmessenger.zendesk.com/hc/articles/360018789931"
    }

    object Account {
        const val PREF_PIN_CHECK = "pref_pin_check"
        const val PREF_BIOMETRICS = "pref_biometrics"
        const val PREF_WRONG_TIME = "pref_wrong_time"
        const val PREF_FTS4_UPGRADE = "pref_fts4_upgrade"
        const val PREF_SYNC_FTS4_OFFSET = "sync_fts4_offset"
        const val PREF_RESTORE = "pref_restore"
        const val PREF_RECALL_SHOW = "pref_recall_show"
        const val PREF_HAS_WITHDRAWAL_ADDRESS_SET = "pref_has_withdrawal_address_set"
        const val PREF_RECENT_USED_BOTS = "pref_recent_used_bots"
        const val PREF_DELETE_MOBILE_CONTACTS = "pref_delete_mobile_contacts"
        const val PREF_FIAT_MAP = "pref_fiat_map"
        const val PREF_NOTIFICATION_ON = "pref_notification_on"
        const val PREF_EMERGENCY_CONTACT = "pref_emergency_contact"
        const val PREF_BATTERY_OPTIMIZE = "pref_battery_optimize"
        const val PREF_SYNC_CIRCLE = "pref_sync_circle"
        const val PREF_ATTACHMENT = "pref_attachment"
        const val PREF_ATTACHMENT_LAST = "pref_attachment_last"
        const val PREF_ATTACHMENT_OFFSET = "pref_attachment_row_offset"
        const val PREF_BACKUP = "pref_attachment_backup"
        const val PREF_CHECK_STORAGE = "pref_check_storage"
        const val PREF_TRIED_UPDATE_KEY = "pref_tried_update_key"
        const val PREF_DUPLICATE_TRANSFER = "pref_duplicate_transfer"
        const val PREF_STRANGER_TRANSFER = "pref_stranger_transfer"
        const val PREF_RECENT_SEARCH_ASSETS = "pref_recent_search_assets"
        const val PREF_INCOGNITO_KEYBOARD = "pref_incognito_keyboard"
        const val PREF_APP_AUTH = "pref_app_auth"
        const val PREF_APP_ENTER_BACKGROUND = "pref_app_enter_background"
    }

    object Scheme {
        const val CODES = "mixin://codes"
        const val PAY = "mixin://pay"
        const val USERS = "mixin://users"
        const val TRANSFER = "mixin://transfer"
        const val DEVICE = "mixin://device/auth"
        const val SEND = "mixin://send"
        const val ADDRESS = "mixin://address"
        const val WITHDRAWAL = "mixin://withdrawal"
        const val APPS = "mixin://apps"
        const val SNAPSHOTS = "mixin://snapshots"
        const val CONVERSATIONS = "mixin://conversations"

        const val HTTPS_CODES = "https://mixin.one/codes"
        const val HTTPS_PAY = "https://mixin.one/pay"
        const val HTTPS_USERS = "https://mixin.one/users"
        const val HTTPS_TRANSFER = "https://mixin.one/transfer"
        const val HTTPS_ADDRESS = "https://mixin.one/address"
        const val HTTPS_WITHDRAWAL = "https://mixin.one/withdrawal"
        const val HTTPS_APPS = "https://mixin.one/apps"
    }

    object DataBase {
        const val DB_NAME = "mixin.db"
        const val MINI_VERSION = 15
        const val CURRENT_VERSION = 39
    }

    object Storage {
        const val IMAGE = "IMAGE"
        const val VIDEO = "VIDEO"
        const val AUDIO = "AUDIO"
        const val DATA = "DATA"
        const val TRANSCRIPT = "TRANSCRIPT"
    }

    object BackUp {
        const val BACKUP_PERIOD = "backup_period"
        const val BACKUP_LAST_TIME = "backup_last_time"
    }

    object CIRCLE {
        const val CIRCLE_ID = "circle_id"
        const val CIRCLE_NAME = "circle_name"
    }

    object Download {
        const val AUTO_DOWNLOAD_MOBILE = "auto_download_mobile"
        const val AUTO_DOWNLOAD_WIFI = "auto_download_wifi"
        const val AUTO_DOWNLOAD_ROAMING = "auto_download_roaming"

        const val AUTO_DOWNLOAD_PHOTO = 0x001
        const val AUTO_DOWNLOAD_VIDEO = 0x010
        const val AUTO_DOWNLOAD_DOCUMENT = 0x100

        const val MOBILE_DEFAULT = 0x001
        const val WIFI_DEFAULT = 0x111
        const val ROAMING_DEFAULT = 0x000
    }

    object Theme {
        const val THEME_CURRENT_ID = "theme_current_id"
        const val THEME_DEFAULT_ID = 0
        const val THEME_NIGHT_ID = 1
        const val THEME_AUTO_ID = 2
    }

    object ChainId {
        const val RIPPLE_CHAIN_ID = "23dfb5a5-5d7b-48b6-905f-3970e3176e27"
        const val BITCOIN_CHAIN_ID = "c6d0c728-2624-429b-8e0d-d9d19b6592fa"
        const val ETHEREUM_CHAIN_ID = "43d61dcd-e413-450d-80b8-101d5e903357"
        const val EOS_CHAIN_ID = "6cfe566e-4aad-470b-8c9a-2fd35b49c68d"
        const val TRON_CHAIN_ID = "25dabac5-056a-48ff-b9f9-f67395dc407c"
    }

    object Mute {
        const val MUTE_1_HOUR = 1 * 60 * 60
        const val MUTE_8_HOURS = 8 * 60 * 60
        const val MUTE_1_WEEK = 7 * 24 * 60 * 60
        const val MUTE_1_YEAR = 365 * 24 * 60 * 60
    }

    object Locale {
        object Indonesian {
            const val Language = "id"
            const val Country = "ID"
        }
        object Malay {
            const val Language = "ms"
            const val Country = ""
        }
    }

    object Debug {
        const val WEB_DEBUG = "web_debug"
        const val DB_DEBUG = "db_debug"
        const val DB_DEBUG_WARNING = "db_debug_warning"
    }

    const val DEVICE_ID = "device_id"

    const val SLEEP_MILLIS: Long = 1000
    const val INTERVAL_24_HOURS: Long = 1000 * 60 * 60 * 24
    const val INTERVAL_48_HOURS: Long = 1000 * 60 * 60 * 48
    const val INTERVAL_10_MINS: Long = 1000 * 60 * 10
    const val INTERVAL_30_MINS: Long = 1000 * 60 * 30
    const val INTERVAL_1_MIN: Long = 1000 * 60
    const val INTERVAL_7_DAYS: Long = INTERVAL_24_HOURS * 7
    const val DELAY_SECOND = 60
    const val ALLOW_INTERVAL: Long = 5 * 60 * 1000

    const val SAFETY_NET_INTERVAL_KEY = "safety_net_interval_key"

    const val ARGS_USER = "args_user"
    const val ARGS_USER_ID = "args_user_id"
    const val ARGS_CONVERSATION_ID = "args_conversation_id"
    const val ARGS_ASSET_ID = "args_asset_id"

    const val MY_QR = "my_qr"

    const val Mixin_Conversation_ID_HEADER = "Mixin-Conversation-ID"
    val KEYS = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<<")

    const val BATCH_SIZE = 700
    const val MARK_LIMIT = 10000

    const val PAGE_SIZE = 15
    const val CONVERSATION_PAGE_SIZE = 15

    const val BIOMETRICS_ALIAS = "biometrics_alias"
    const val BIOMETRICS_PIN = "biometrics_pin"
    const val BIOMETRICS_IV = "biometrics_iv"
    const val BIOMETRIC_INTERVAL = "biometric_interval"
    const val BIOMETRIC_INTERVAL_DEFAULT: Long = 1000 * 60 * 60 * 2
    const val BIOMETRIC_PIN_CHECK = "biometric_pin_check"

    const val RECENT_USED_BOTS_MAX_COUNT = 12
    const val RECENT_SEARCH_ASSETS_MAX_COUNT = 8

    const val PIN_ERROR_MAX = 5

    const val BIG_IMAGE_SIZE = 5 * 1024 * 1024

    const val DB_DELETE_THRESHOLD = 5000
    const val DB_DELETE_LIMIT = 500

    val DNS: Dns = SequentialDns(CustomDns("8.8.8.8"), CustomDns("1.1.1.1"), CustomDns("2001:4860:4860::8888"), Dns.SYSTEM)

    const val TEAM_MIXIN_USER_ID = "773e5e77-4107-45c2-b648-8fc722ed77f5"
    // Only for third-party messenger user
    const val TEAM_BOT_ID = ""
    const val TEAM_BOT_NAME = ""
}
