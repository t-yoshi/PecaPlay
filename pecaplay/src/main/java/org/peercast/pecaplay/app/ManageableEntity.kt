package org.peercast.pecaplay.app

import android.os.Parcelable


abstract class ManageableEntity : Parcelable {
    abstract val name: String
    abstract val isEnabled: Boolean
}

