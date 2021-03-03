package one.mixin.android.vo.github

import com.google.gson.annotations.SerializedName
import one.mixin.android.BuildConfig

data class Latest(
    @SerializedName("tag_name")
    val tagName: String?
) {
    val versionCode: Int?
        get() {
            return tagName?.run {
                this.substring(1).replace(".", "").toInt() * 100
            }
        }

    fun hasNewVersion(): Boolean {
        return versionCode?.run { this > BuildConfig.VERSION_CODE }
            ?: false
    }
}

