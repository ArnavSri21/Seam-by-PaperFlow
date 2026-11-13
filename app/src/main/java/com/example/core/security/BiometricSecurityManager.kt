package com.example.core.security

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Represents the hardware and permission state evaluated by the Security Layer.
 */
enum class BiometricSecurityStatus {
    /** Biometric hardware is present, `USE_BIOMETRIC` permission is granted, and biometrics are enrolled. */
    BIOMETRIC_READY,

    /** Biometric sensor hardware is present on the device, but no fingerprint/face is enrolled in Android Settings. */
    HARDWARE_PRESENT_NONE_ENROLLED,

    /** Biometric hardware is present, but `USE_BIOMETRIC` / `USE_FINGERPRINT` permission is not granted. */
    PERMISSION_REQUIRED,

    /** Device lacks a biometric sensor (`BIOMETRIC_ERROR_NO_HARDWARE` or `BIOMETRIC_ERROR_HW_UNAVAILABLE`). Requires PIN/Password fallback. */
    NO_BIOMETRIC_HARDWARE_FALLBACK_REQUIRED
}

/**
 * Detailed snapshot of the device's biometric hardware, permission, and fallback state.
 */
data class SecurityLayerAssessment(
    val status: BiometricSecurityStatus,
    val hasBiometricPermission: Boolean,
    val hasPhysicalBiometricSensor: Boolean,
    val isBiometricEnrolled: Boolean,
    val isDeviceKeyguardSecure: Boolean,
    val statusLabel: String,
    val statusDetail: String
) {
    /**
     * True only when hardware is present, permission is granted, and biometrics are enrolled.
     * When false, the security layer enforces the PIN/Password fallback.
     */
    val canUseHardwareBiometricPrompt: Boolean
        get() = status == BiometricSecurityStatus.BIOMETRIC_READY
}

/**
 * Centralized Security Layer responsible for:
 * 1. Checking `USE_BIOMETRIC` and `USE_FINGERPRINT` permissions.
 * 2. Inspecting biometric sensor hardware availability (`BiometricManager` + `PackageManager` features).
 * 3. Executing hardware `BiometricPrompt` authentication when available.
 * 4. Routing devices that lack a biometric sensor (or lack enrolled biometrics) to the mandatory PIN/Password fallback.
 */
object BiometricSecurityManager {

    /**
     * Checks whether the application holds permission to use the device's biometric hardware.
     */
    fun hasBiometricPermission(context: Context): Boolean {
        val useBiometricGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.USE_BIOMETRIC
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        @Suppress("DEPRECATION")
        val useFingerprintGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.USE_FINGERPRINT
        ) == PackageManager.PERMISSION_GRANTED

        return useBiometricGranted || useFingerprintGranted
    }

    /**
     * Inspects the Android device for physical biometric sensor hardware (fingerprint, face, or iris),
     * permission status, and enrollment state.
     */
    fun assessSecurityLayer(context: Context): SecurityLayerAssessment {
        val permissionGranted = hasBiometricPermission(context)
        val pm = context.packageManager
        val hasFingerprintFeature = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasFaceFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.hasSystemFeature(PackageManager.FEATURE_FACE)
        } else {
            false
        }
        val hasIrisFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
        } else {
            false
        }
        val hasPackageFeatureSensor = hasFingerprintFeature || hasFaceFeature || hasIrisFeature

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true

        if (!permissionGranted) {
            return SecurityLayerAssessment(
                status = BiometricSecurityStatus.PERMISSION_REQUIRED,
                hasBiometricPermission = false,
                hasPhysicalBiometricSensor = hasPackageFeatureSensor,
                isBiometricEnrolled = false,
                isDeviceKeyguardSecure = isDeviceSecure,
                statusLabel = "Biometric Permission Required",
                statusDetail = "USE_BIOMETRIC permission is required to access hardware sensor. PIN/Password fallback active."
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bm = context.getSystemService(BiometricManager::class.java)
            val result = bm?.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

            return when (result) {
                BiometricManager.BIOMETRIC_SUCCESS -> SecurityLayerAssessment(
                    status = BiometricSecurityStatus.BIOMETRIC_READY,
                    hasBiometricPermission = true,
                    hasPhysicalBiometricSensor = true,
                    isBiometricEnrolled = true,
                    isDeviceKeyguardSecure = isDeviceSecure,
                    statusLabel = "Biometric Sensor Ready",
                    statusDetail = "Hardware biometric sensor and USE_BIOMETRIC permission verified."
                )

                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> SecurityLayerAssessment(
                    status = BiometricSecurityStatus.HARDWARE_PRESENT_NONE_ENROLLED,
                    hasBiometricPermission = true,
                    hasPhysicalBiometricSensor = true,
                    isBiometricEnrolled = false,
                    isDeviceKeyguardSecure = isDeviceSecure,
                    statusLabel = "Sensor Present · No Biometrics Enrolled",
                    statusDetail = "Biometric hardware detected, but no fingerprint/face is enrolled on this device. PIN/Password fallback required."
                )

                else -> SecurityLayerAssessment(
                    status = BiometricSecurityStatus.NO_BIOMETRIC_HARDWARE_FALLBACK_REQUIRED,
                    hasBiometricPermission = true,
                    hasPhysicalBiometricSensor = false,
                    isBiometricEnrolled = false,
                    isDeviceKeyguardSecure = isDeviceSecure,
                    statusLabel = "No Biometric Sensor · PIN/Password Mode",
                    statusDetail = "This device lacks an active biometric sensor. Security layer requires PIN/Password entry."
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(BiometricManager::class.java)
            @Suppress("DEPRECATION")
            val result = bm?.canAuthenticate() ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            return when (result) {
                BiometricManager.BIOMETRIC_SUCCESS -> SecurityLayerAssessment(
                    status = BiometricSecurityStatus.BIOMETRIC_READY,
                    hasBiometricPermission = true,
                    hasPhysicalBiometricSensor = true,
                    isBiometricEnrolled = true,
                    isDeviceKeyguardSecure = isDeviceSecure,
                    statusLabel = "Biometric Sensor Ready",
                    statusDetail = "Hardware biometric sensor and USE_BIOMETRIC permission verified."
                )

                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> SecurityLayerAssessment(
                    status = BiometricSecurityStatus.HARDWARE_PRESENT_NONE_ENROLLED,
                    hasBiometricPermission = true,
                    hasPhysicalBiometricSensor = true,
                    isBiometricEnrolled = false,
                    isDeviceKeyguardSecure = isDeviceSecure,
                    statusLabel = "Sensor Present · No Biometrics Enrolled",
                    statusDetail = "Biometric hardware detected, but no biometrics are enrolled. PIN/Password fallback required."
                )

                else -> SecurityLayerAssessment(
                    status = BiometricSecurityStatus.NO_BIOMETRIC_HARDWARE_FALLBACK_REQUIRED,
                    hasBiometricPermission = true,
                    hasPhysicalBiometricSensor = false,
                    isBiometricEnrolled = false,
                    isDeviceKeyguardSecure = isDeviceSecure,
                    statusLabel = "No Biometric Sensor · PIN/Password Mode",
                    statusDetail = "This device lacks an active biometric sensor. Security layer requires PIN/Password entry."
                )
            }
        } else {
            return if (hasPackageFeatureSensor && isDeviceSecure) {
                SecurityLayerAssessment(
                    status = BiometricSecurityStatus.BIOMETRIC_READY,
                    hasBiometricPermission = true,
                    hasPhysicalBiometricSensor = true,
                    isBiometricEnrolled = true,
                    isDeviceKeyguardSecure = true,
                    statusLabel = "Biometric Sensor Ready",
                    statusDetail = "Fingerprint hardware and permission verified."
                )
            } else {
                SecurityLayerAssessment(
                    status = BiometricSecurityStatus.NO_BIOMETRIC_HARDWARE_FALLBACK_REQUIRED,
                    hasBiometricPermission = permissionGranted,
                    hasPhysicalBiometricSensor = hasPackageFeatureSensor,
                    isBiometricEnrolled = false,
                    isDeviceKeyguardSecure = isDeviceSecure,
                    statusLabel = "No Biometric Sensor · PIN/Password Mode",
                    statusDetail = "This device lacks an active biometric sensor. Security layer requires PIN/Password entry."
                )
            }
        }
    }

    /**
     * Builds an Intent to open Android's system Biometric Enrollment or Security Settings
     * if the device has hardware but no enrolled biometrics.
     */
    fun createBiometricEnrollmentIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * Authenticates the user via the Android hardware `BiometricPrompt` if the security layer
     * confirms biometric hardware availability and permission; otherwise triggers `onRequirePinOrPasswordFallback`.
     */
    fun authenticateWithSecurityLayer(
        context: Context,
        title: String,
        subtitle: String,
        description: String = "Confirm your identity to unlock protected data.",
        onAuthenticated: () -> Unit,
        onRequirePinOrPasswordFallback: (SecurityLayerAssessment) -> Unit,
        onAuthenticationErrorOrCancelled: (String) -> Unit
    ) {
        val assessment = assessSecurityLayer(context)
        if (!assessment.canUseHardwareBiometricPrompt) {
            onRequirePinOrPasswordFallback(assessment)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val executor = ContextCompat.getMainExecutor(context)
                val promptBuilder = BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    promptBuilder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                }

                promptBuilder.setNegativeButton("Use PIN / Password", executor) { _, _ ->
                    onRequirePinOrPasswordFallback(assessment)
                }

                val prompt = promptBuilder.build()
                prompt.authenticate(
                    CancellationSignal(),
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onAuthenticated()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onAuthenticationErrorOrCancelled("Biometric not recognized by device sensor. Try again.")
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            if (
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE
                            ) {
                                onRequirePinOrPasswordFallback(assessSecurityLayer(context))
                            } else {
                                onAuthenticationErrorOrCancelled(
                                    errString?.toString() ?: "Biometric authentication cancelled."
                                )
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                onRequirePinOrPasswordFallback(assessment)
            }
        } else {
            onRequirePinOrPasswordFallback(assessment)
        }
    }
}
