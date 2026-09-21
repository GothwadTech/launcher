package com.gothwad.launcher.ui

import androidx.fragment.app.FragmentManager
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.service.LauncherAccessibilityService
import com.gothwad.launcher.ui.dialogs.PinEntryDialogFragment

/**
 * Single decision point for "may this app be launched right now?".
 *
 * Why this exists (issue #31): the same `if (appLock.enabled && pkg in lockedApps) { show
 * PinEntryDialogFragment }` block lived in both `MainActivity` and `TvLauncherFragment`,
 * while the *authoritative* enforcement sat in a third place (the accessibility service).
 * Three copies of a security decision drift apart - this file is the one place the UI
 * layers ask, so a rule change (vault scope, device lock, throttling) applies everywhere.
 *
 * NOTE: this is the UX layer that keeps the security prompt consistent. The unbypassable
 * enforcement (notification, recents, other launchers, ADB) still lives in
 * [LauncherAccessibilityService] and is what actually protects the app.
 */
object AppLockGate {

    /**
     * True when the device lock is active and has not been satisfied in this process.
     * The device-lock screen itself blocks the launcher, so nothing may be launched.
     */
    fun isDeviceLockBlocking(config: LauncherConfig, deviceUnlockedThisProcess: Boolean): Boolean =
        config.deviceLock.enabled && config.deviceLock.ready && !deviceUnlockedThisProcess

    /** What the caller has to do after [evaluate] returns. */
    enum class Result {
        /** Launch immediately. */
        PROCEED,

        /** A credential prompt was shown; the callback will launch on success. */
        PROMPTED,

        /** Access refused (device lock active) - do nothing. */
        BLOCKED,
    }

    /**
     * Evaluates the locks for [app] and shows the credential prompt when one is needed.
     *
     * @param onProceed invoked on the calling (main) thread once the app may be launched -
     *   either immediately, or after the user entered the correct credential.
     */
    fun evaluate(
        fragmentManager: FragmentManager,
        app: AppEntry,
        config: LauncherConfig,
        deviceUnlockedThisProcess: Boolean,
        onProceed: () -> Unit,
    ): Result {
        if (isDeviceLockBlocking(config, deviceUnlockedThisProcess)) {
            return Result.BLOCKED
        }

        val needsAppUnlock = config.appLock.enabled &&
            config.appLock.ready &&
            app.pkg in config.lockedApps &&
            !LauncherAccessibilityService.unlockedPackagesSession.contains(app.pkg)

        if (needsAppUnlock) {
            prompt(
                fragmentManager = fragmentManager,
                app = app,
                credential = config.appLock,
                scope = LockSecurity.SCOPE_APP,
                title = "App Locked",
                subtitlePrefix = "Enter PIN/Password to launch",
                onProceed = onProceed,
            )
            return Result.PROMPTED
        }

        // A hidden (vault) app has its own credential and its own throttling scope.
        val needsVaultUnlock = app.pkg in config.hidden &&
            config.hiddenAppsLock.ready &&
            !LauncherAccessibilityService.unlockedPackagesSession.contains(app.pkg)

        if (needsVaultUnlock) {
            prompt(
                fragmentManager = fragmentManager,
                app = app,
                credential = config.hiddenAppsLock,
                scope = LockSecurity.SCOPE_VAULT,
                title = "Hidden App",
                subtitlePrefix = "Enter vault credential to launch",
                onProceed = onProceed,
            )
            return Result.PROMPTED
        }

        // Deliberate launch of a hidden app: let the accessibility vault guard through.
        if (app.pkg in config.hidden) {
            LauncherAccessibilityService.unlockedPackagesSession.add(app.pkg)
        }
        onProceed()
        return Result.PROCEED
    }

    private fun prompt(
        fragmentManager: FragmentManager,
        app: AppEntry,
        credential: LockCredential,
        scope: String,
        title: String,
        subtitlePrefix: String,
        onProceed: () -> Unit,
    ) {
        PinEntryDialogFragment.newInstance(
            title = title,
            subtitle = "$subtitlePrefix ${app.label}",
            credential = credential,
            isCancelable = true,
            lockScope = scope,
            onSuccess = {
                // Remember the unlocked package for this foreground session so the
                // accessibility service does not immediately re-prompt.
                LauncherAccessibilityService.unlockedPackagesSession.add(app.pkg)
                onProceed()
            },
        ).show(fragmentManager, PinEntryDialogFragment.TAG)
    }
}
