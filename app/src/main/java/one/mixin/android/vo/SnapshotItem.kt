package one.mixin.android.vo

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@SuppressLint("ParcelCreator")
@Parcelize
@Entity
data class SnapshotItem(
    @PrimaryKey
    @SerializedName("snapshot_id")
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String,
    @SerializedName("type")
    @ColumnInfo(name = "type")
    val type: String,
    @SerializedName("asset_id")
    @ColumnInfo(name = "asset_id")
    val assetId: String,
    @SerializedName("amount")
    @ColumnInfo(name = "amount")
    val amount: String,
    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @SerializedName("opponent_id")
    @ColumnInfo(name = "opponent_id")
    val opponentId: String?,
    @Deprecated(
        "Replace with opponent_id",
        ReplaceWith("@{link opponentId}", "one.mixin.android.vo.Snapshot.opponentId"),
        DeprecationLevel.ERROR
    )
    @SerializedName("counter_user_id")
    @ColumnInfo(name = "counter_user_id")
    val counterUserId: String?,
    val opponentFullName: String?,
    @SerializedName("transaction_hash")
    @ColumnInfo(name = "transaction_hash")
    val transactionHash: String?,
    @SerializedName("sender")
    @ColumnInfo(name = "sender")
    val sender: String?,
    @SerializedName("receiver")
    @ColumnInfo(name = "receiver")
    val receiver: String?,
    @SerializedName("memo")
    @ColumnInfo(name = "memo")
    val memo: String?,
    @SerializedName("asset_symbol")
    @ColumnInfo(name = "asset_symbol")
    val assetSymbol: String?,
    @SerializedName("confirmations")
    @ColumnInfo(name = "confirmations")
    val confirmations: Int?
) : Parcelable
