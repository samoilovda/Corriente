package corriente.buildsupport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A2.12: страж итогового манифеста. */
class MergedManifestGuardTest {

    private val appId = "com.corriente.app"

    private fun manifest(vararg permissions: String) = buildString {
        appendLine("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">")
        appendLine("    <!-- <uses-permission android:name=\"android.permission.INTERNET\" /> в комментарии не считается -->")
        permissions.forEach { appendLine("    <uses-permission android:name=\"$it\" />") }
        appendLine("</manifest>")
    }

    @Test
    fun `the expected permission set is clean`() {
        val ok = manifest(
            "android.permission.WAKE_LOCK", "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.FOREGROUND_SERVICE",
            "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT",
            "com.corriente.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        assertEquals(emptyList<String>(), MergedManifestGuard.scan(ok, appId))
    }

    @Test
    fun `INTERNET and unknown permissions fail the build`() {
        val bad = manifest("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.CAMERA")
        val violations = MergedManifestGuard.scan(bad, appId)
        assertEquals(3, violations.size)
        assertTrue(violations.first().contains("INTERNET"))
    }

    @Test
    fun `a permission spread over several lines and sdk-specific variants are parsed`() {
        val xml = """
            <manifest>
                <uses-permission-sdk-23
                    android:maxSdkVersion="32"
                    android:name="android.permission.INTERNET" />
            </manifest>
        """.trimIndent()
        assertEquals(listOf("android.permission.INTERNET"), MergedManifestGuard.permissions(xml))
    }

    @Test
    fun `another application id does not get the internal androidx permission for free`() {
        val xml = manifest("com.other.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        assertEquals(1, MergedManifestGuard.scan(xml, appId).size)
    }
}
