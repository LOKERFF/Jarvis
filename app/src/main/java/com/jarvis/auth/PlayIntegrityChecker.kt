package com.jarvis.auth

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityServiceException
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.jarvis.BuildConfig
import com.jarvis.data.prefs.AppPreferences
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Play Integrity check.
 *
 * The truth about this API: the verdict (MEETS_DEVICE_INTEGRITY,
 * LICENSED, ...) is encrypted inside the token and can only be decrypted by
 * Google's server or your own backend. A phone-side app cannot read it.
 *
 * So this checker reports exactly what it can prove:
 *  - VERIFIED : Play services issued a token for this app on this device.
 *  - LIMITED  : the API answered with an error (typical for a sideloaded APK,
 *               missing Play services, or a debug build).
 *  - ERROR    : something unexpected happened.
 *
 * It never claims "verified" without a token. A backend verdict check is
 * listed as a known limitation in README.md.
 */
class PlayIntegrityChecker(private val context: Context) {

    data class Outcome(val status: String, val detail: String)

    suspend fun check(): Outcome {
        return try {
            val manager = IntegrityManagerFactory.create(context)
            val nonce = newNonce()

            val builder = IntegrityTokenRequest.builder().setNonce(nonce)
            BuildConfig.PLAY_INTEGRITY_PROJECT.trim().toLongOrNull()?.let { project ->
                if (project > 0L) builder.setCloudProjectNumber(project)
            }

            val response = manager.requestIntegrityToken(builder.build()).await()
            val token = response.token()

            if (token.isBlank()) {
                Outcome(
                    AppPreferences.STATUS_LIMITED,
                    "Play Integrity returned an empty token, so the app cannot prove itself."
                )
            } else {
                Outcome(
                    AppPreferences.STATUS_VERIFIED,
                    "Play services issued an integrity token (${token.length} characters). " +
                        "The verdict itself is encrypted and can only be read by a server, " +
                        "so device-side verification stops here."
                )
            }
        } catch (e: IntegrityServiceException) {
            Outcome(
                AppPreferences.STATUS_LIMITED,
                "Play Integrity error ${e.errorCode}: ${e.message ?: "not available"}. " +
                    "Sideloaded, debug or unsigned builds normally land here. " +
                    "Jarvis keeps working in limited mode."
            )
        } catch (t: Throwable) {
            Outcome(
                AppPreferences.STATUS_ERROR,
                "Play Integrity could not run: ${t.message ?: t.javaClass.simpleName}"
            )
        }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
