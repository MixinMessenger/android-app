package one.mixin.android.api.response

import com.google.gson.annotations.SerializedName

data class UserSession(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("platform")
    val platform: String?,
    @SerializedName("public_key")
    val publicKey: String?,
)
