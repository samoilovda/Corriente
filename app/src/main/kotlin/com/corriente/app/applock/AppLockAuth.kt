package com.corriente.app.applock

import android.content.Context
import androidx.biometric.BiometricManager

/**
 * R5.2: биометрия ИЛИ PIN/графический ключ устройства — оба варианта явно перечислены в
 * ROADMAP.md §7 («по биометрии/PIN устройства»). `BIOMETRIC_WEAK`, а не `_STRONG` — приложению
 * не нужна криптографически привязанная к ключу разблокировка (данные не шифруются, см.
 * `app_lock_disclaimer`), только жест «это тот же человек, что настраивал экран блокировки».
 */
val APP_LOCK_ALLOWED_AUTHENTICATORS: Int =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** true — на устройстве есть чем пройти [APP_LOCK_ALLOWED_AUTHENTICATORS] прямо сейчас. */
fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(APP_LOCK_ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS
