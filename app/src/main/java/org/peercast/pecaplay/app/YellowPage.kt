package org.peercast.pecaplay.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.parcelize.Parcelize

/**
 * イエローページ
 * @version 50000
 */
@Entity(primaryKeys = ["name"])
@Parcelize
data class YellowPage(
    override val name: String,
    val url: String,
    @ColumnInfo(name = "enabled")
    override val isEnabled: Boolean = true,
) : ManageableEntity() {

    companion object {
        fun isValidUrl(u: String): Boolean {
            return u.matches("""^https?://\S+/$""".toRegex())
        }
    }
}