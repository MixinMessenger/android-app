package one.mixin.android.vo

import android.annotation.SuppressLint
import android.os.Parcelable
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@SuppressLint("ParcelCreator")
@Parcelize
@Entity(
    tableName = "users",
    indices = [
        Index(value = arrayOf("relationship", "full_name")),
    ]
)
data class User(
    @PrimaryKey
    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String,
    @SerializedName("identity_number")
    @ColumnInfo(name = "identity_number")
    val identityNumber: String,
    /**
     * @see UserRelationship
     */
    @SerializedName("relationship")
    @ColumnInfo(name = "relationship")
    var relationship: String,
    @SerializedName("biography")
    @ColumnInfo(name = "biography")
    val biography: String,
    @SerializedName("full_name")
    @ColumnInfo(name = "full_name")
    val fullName: String?,
    @SerializedName("avatar_url")
    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String?,
    @ColumnInfo(name = "phone")
    val phone: String?,
    @SerializedName("is_verified")
    @ColumnInfo(name = "is_verified")
    val isVerified: Boolean?,
    @SerializedName("create_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String?,
    @SerializedName("mute_until")
    @ColumnInfo(name = "mute_until")
    var muteUntil: String?,
    @SerializedName("has_pin")
    @ColumnInfo(name = "has_pin")
    val hasPin: Boolean? = null,
    @SerializedName("app_id")
    @ColumnInfo(name = "app_id")
    var appId: String? = null,
    @SerializedName("is_scam")
    @ColumnInfo(name = "is_scam")
    var isScam: Boolean? = null
) : Parcelable {
    @SerializedName("app")
    @Ignore
    @IgnoredOnParcel
    var app: App? = null

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User) =
                oldItem.userId == newItem.userId

            override fun areContentsTheSame(oldItem: User, newItem: User) =
                oldItem == newItem
        }
    }

    fun isBot(): Boolean {
        return appId != null
    }
}

const val SYSTEM_USER = "00000000-0000-0000-0000-000000000000"

fun createSystemUser(): User {
    return User(SYSTEM_USER, "0", "", "", "0", null, null, false, null, null)
}

fun User.notMessengerUser(): Boolean {
    return identityNumber == "0"
}

fun User.showVerifiedOrBot(verifiedView: View, botView: View) {
    when {
        isVerified == true -> {
            verifiedView.isVisible = true
            botView.isVisible = false
        }
        isBot() -> {
            verifiedView.isVisible = false
            botView.isVisible = true
        }
        else -> {
            verifiedView.isVisible = false
            botView.isVisible = false
        }
    }
}
