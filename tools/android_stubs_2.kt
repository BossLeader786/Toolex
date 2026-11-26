/*
 * Part 2 of the JVM type-check stubs (see android_stubs.kt). Not part of the app.
 */
package android.net

class Uri private constructor(val value: String) {
    fun lastPathSegment(): String? = value.substringAfterLast('/', "")
    companion object {
        fun parse(s: String): Uri = Uri(s)
    }
}
