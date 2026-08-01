package android.net

import android.os.Parcel

/** Non-null URI for local JVM tests, where the Android stub's [Uri.EMPTY] is null. */
object TestUri : Uri() {
    override fun buildUpon(): Builder = error("Not used")
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPath(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = true
    override fun toString(): String = "test://private-key"
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
