// SPDX-License-Identifier: AGPL-3.0-only
// UI copy and visual language adapted from DiAuto. See docs/THIRD_PARTY_NOTICES.md.
package com.shilapi.xcertplay

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.airplay.CarPlaySize
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** DiAuto's visual language, with a connection flow for an independent CarPlay receiver. */
class DiPlayActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var page = "home"
    /** Set just before a language change recreates this activity; carried through saved state. */
    private var recreatingForLanguage = false
    /** A language change must re-render the page without starting a connection. */
    private var skipAutoConnectOnResume = false
    private var pendingCarHotspotSetup = false
    private var setupError: String? = null
    private var status: TextView? = null
    private var connectButton: Button? = null
    private var disconnectButton: Button? = null
    private var lastRunning: Boolean? = null
    private var pendingWireless = false
    private var initialLaunch = true
    private var notificationTransport = true
    private var exportInProgress = false
    private var exportButton: Button? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        connect(notificationTransport)
    }
    private val tick = object : Runnable {
        override fun run() { refreshStatus(); handler.postDelayed(this, 1000) }
    }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) choosePhone() else permissionHelp(getString(R.string.permission_help_nearby_title), getString(R.string.permission_help_nearby_body))
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) exportDiagnostics(uri)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.shilapi.xcertplay.hud.BydNavigationOutputs.onAppOpened(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = BG; window.navigationBarColor = BG
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            hide(WindowInsetsCompat.Type.statusBars())
        }
        setupError = runCatching { DiPlayBootstrap.ensure(this) }.exceptionOrNull()?.let {
            android.util.Log.e("DiPlaySetup", "CarPlay authentication could not be loaded", it)
            getString(R.string.setup_error)
        }
        pendingCarHotspotSetup = savedInstanceState?.getBoolean("pending_car_hotspot") ?: false
        page = savedInstanceState?.getString("page") ?: intent.getStringExtra("page") ?: "home"
        skipAutoConnectOnResume = savedInstanceState?.getBoolean(KEY_SKIP_AUTO_CONNECT) == true
        render()
        handleWirelessRecovery()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page != "home") { page = "home"; render() }
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        page = intent.getStringExtra("page") ?: "home"; render()
        handleWirelessRecovery()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page)
        outState.putBoolean("pending_car_hotspot", pendingCarHotspotSetup)
        // A recreation for a language change must not look like a fresh launch to onResume.
        outState.putBoolean(KEY_SKIP_AUTO_CONNECT, recreatingForLanguage)
        super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); render() }
    override fun onResume() {
        super.onResume(); handler.removeCallbacks(tick); handler.post(tick)
        // Back from the car settings: refresh the car hotspot reminder on the home page.
        if (!initialLaunch && (page == "home" || page == "settings" || page == "connection")) render()
        if (initialLaunch) {
            initialLaunch = false
            if (setupError == null && !skipAutoConnectOnResume && !CarPlayBackgroundSession.hasSession() &&
                DiPlayPreferences.autoConnect(this) && intent.getStringExtra("page") == null) {
                handler.post { connect(AirPlayPersistence.loadWirelessEnabled(this)) }
            }
        }
    }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun render() {
        status = null; connectButton = null; disconnectButton = null; lastRunning = null
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; clipToPadding = false }
        val content = column().apply { setPadding(dp(32), dp(24), dp(32), dp(32)) }
        scroll.addView(content)
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_carplay); contentDescription = getString(R.string.content_desc_carplay) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        header.addView(label(getString(R.string.app_name), 26, TEXT, true).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(button(if (page == "home") getString(R.string.home_action_car_home) else getString(R.string.action_back), false) {
            if (page == "home") startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            else { page = "home"; render() }
        }, LinearLayout.LayoutParams(dp(130), dp(56)))
        content.addView(header)
        content.addView(space(24))
        when (page) {
            "connection" -> connectionSetup(content)
            "settings" -> settings(content)
            "about" -> about(content)
            else -> home(content)
        }
        setContentView(scroll)
        refreshStatus()
    }

    private fun home(content: LinearLayout) {
        val wide = resources.configuration.screenWidthDp >= 850
        val body = column()
        val left = column()
        left.addView(label(getString(R.string.home_tagline), 12, ACCENT, true).apply { letterSpacing = tracking(.16f) })
        left.addView(label(getString(R.string.home_headline), if (wide) 42 else 36, TEXT, true).apply { setPadding(0, dp(12), 0, dp(10)) })
        left.addView(label(getString(R.string.home_body), 19, MUTED))
        val card = card()
        card.addView(label(getString(R.string.home_card_label), 12, ACCENT, true).apply { letterSpacing = tracking(.12f) })
        status = label(getString(R.string.status_ready), 24, TEXT, true).apply { setPadding(0, dp(10), 0, dp(16)) }
        card.addView(status)
        connectButton = button(getString(R.string.action_connect_phone), true) {
            if (CarPlayBackgroundSession.hasSession()) openProjection()
            else connect(true)
        }
        card.addView(connectButton, matchButton())
        val connectionHint = when (AirPlayPersistence.loadWirelessHotspotMode(this)) {
            WirelessHotspotMode.MANUAL -> getString(R.string.home_hint_builtin_hotspot)
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT -> getString(R.string.home_hint_open_connection_setup)
            else -> getString(R.string.home_hint_wifi_direct)
        }
        card.addView(label(connectionHint, 15, MUTED).apply { setPadding(0, dp(14), 0, 0) })
        if (carHotspotOff()) {
            card.addView(label(getString(R.string.home_hotspot_off, AirPlayPersistence.loadManualHotspotSsid(this)), 15, WARNING).apply { setPadding(0, dp(14), 0, 0) })
            card.addView(button(getString(R.string.action_open_car_hotspot_settings), false) { openCarWifiSettings() }, matchButton(10, 56))
        }
        card.addView(button(getString(R.string.action_choose_iphone), false) { choosePhone() }, matchButton(16, 56))
        disconnectButton = button(getString(R.string.action_disconnect), false) {
            disconnectButton?.isEnabled = false
            CarPlayBackgroundSession.stop { runOnUiThread { refreshStatus() } }
        }.apply { visibility = View.GONE }
        card.addView(disconnectButton, matchButton(10, 56))
        val right = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_carplay)
            contentDescription = getString(R.string.content_desc_carplay_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val branding = column().apply {
            gravity = Gravity.CENTER
            addView(logo, LinearLayout.LayoutParams(dp(96), dp(96)))
        }
        right.addView(button(getString(R.string.action_connect_usb), false) { connect(false) }, matchButton())
        right.addView(label(getString(R.string.home_usb_hint), 14, MUTED).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(24)) })
        right.addView(button(getString(R.string.action_settings), false) { page = "settings"; render() }, matchButton())
        right.addView(label(getString(R.string.home_settings_hint), 14, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(24)) })
        right.addView(label(getString(R.string.home_preview_version, version()), 12, MUTED).apply { letterSpacing = tracking(.08f) })
        if (wide) {
            // Both rows share column widths. The USB button starts at the wireless
            // card's top edge, independently of hero wrapping or font scaling.
            fun columns(first: View, second: View, stretchSecond: Boolean = false) = row().apply {
                gravity = Gravity.TOP
                addView(first, LinearLayout.LayoutParams(0, -2, 1.6f))
                addView(space(40), LinearLayout.LayoutParams(dp(40), 1))
                addView(second, LinearLayout.LayoutParams(0, if (stretchSecond) -1 else -2, 1f))
            }
            body.addView(columns(left, branding, true))
            body.addView(space(26))
            body.addView(columns(card, right))
        } else {
            body.addView(left)
            body.addView(space(26))
            body.addView(card)
            body.addView(space(26))
            body.addView(branding)
            body.addView(space(24))
            body.addView(right)
        }
        setupError?.let { body.addView(label(it, 16, WARNING).apply { setPadding(0, dp(16), 0, 0) }) }
        content.addView(body)
    }

    private fun settings(content: LinearLayout) {
        content.addView(label(getString(R.string.settings_headline), 34, TEXT, true))
        content.addView(label(getString(R.string.settings_body), 17, MUTED).apply { setPadding(0, dp(8), 0, dp(24)) })
        section(content, getString(R.string.section_connection_setup), R.drawable.ic_dp_connection) { card ->
            card.addView(label(getString(R.string.connection_setup_body), 16, MUTED))
            card.addView(button(getString(R.string.action_open_connection_setup), false) { page = "connection"; render() }, matchButton(12, 60))
        }
        section(content, getString(R.string.section_diagnostics), R.drawable.ic_dp_diagnostics) { card ->
            exportButton = button(getString(if (exportInProgress) R.string.action_saving_report else R.string.action_save_report), false) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportDiagnostics()
                else chooseReportDestination()
            }.apply { isEnabled = !exportInProgress }
            card.addView(exportButton, matchButton(10, 60))
            card.addView(button(getString(R.string.action_choose_save_location), false) { chooseReportDestination() }, matchButton(10, 60))
            card.addView(label(getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) R.string.report_note_downloads else R.string.report_note_choose), 14, MUTED).apply { setPadding(0, dp(12), 0, 0) })
        }
        section(content, getString(R.string.section_auto_connect), R.drawable.ic_dp_automation) { card ->
            toggle(card, getString(R.string.toggle_connect_on_open), getString(R.string.toggle_connect_on_open_desc), DiPlayPreferences.autoConnect(this)) { DiPlayPreferences.saveAutoConnect(this, it) }
            toggle(card, getString(R.string.toggle_autostart_boot), getString(R.string.toggle_autostart_boot_desc), AirPlayPersistence.loadAutoStartOnBoot(this)) { AirPlayPersistence.saveAutoStartOnBoot(this, it) }
            card.addView(button(getString(R.string.action_choose_iphone_named, DiPlayPreferences.phoneName(this)), false) { choosePhone() }, matchButton(12, 60))
        }
        section(content, getString(R.string.section_display_performance), R.drawable.ic_dp_display) { card ->
            carPlaySizeControl(card)
            choice(card, getString(R.string.choice_resolution), listOf(
                getString(R.string.choice_resolution_native),
                getString(R.string.choice_resolution_80),
                getString(R.string.choice_resolution_60),
            ), listOf(10, 8, 6).indexOf(AirPlayPersistence.loadDisplayScaleTenths(this)).coerceAtLeast(0)) { AirPlayPersistence.saveDisplayScaleTenths(this, listOf(10, 8, 6)[it]) }
            val bufferPresets = com.shilapi.xcertplay.media.MediaAudioBuffer.presets
            choice(card, getString(R.string.choice_music_buffer), listOf(
                getString(R.string.choice_music_buffer_300),
                getString(R.string.choice_music_buffer_500),
                getString(R.string.choice_music_buffer_1000),
            ), bufferPresets.indexOf(AirPlayPersistence.loadMediaBufferMillis(this)).coerceAtLeast(0)) {
                AirPlayPersistence.saveMediaBufferMillis(this, bufferPresets[it])
            }
            choice(card, getString(R.string.choice_frame_rate), listOf(
                getString(R.string.choice_frame_rate_30),
                getString(R.string.choice_frame_rate_60),
            ), if (AirPlayPersistence.loadFps(this) == 60) 1 else 0) { AirPlayPersistence.saveFps(this, if (it == 1) 60 else 30) }
            toggle(card, getString(R.string.toggle_efficient_video), getString(R.string.toggle_efficient_video_desc), AirPlayPersistence.loadHevcEnabled(this)) { AirPlayPersistence.saveHevcEnabled(this, it) }
            toggle(card, getString(R.string.toggle_right_hand_drive), getString(R.string.toggle_right_hand_drive_desc), AirPlayPersistence.loadRightHandDrive(this)) { AirPlayPersistence.saveRightHandDrive(this, it) }
            toggle(card, getString(R.string.toggle_full_screen), getString(R.string.toggle_full_screen_desc), AirPlayPersistence.loadHideTopBar(this) && AirPlayPersistence.loadHideBottomBar(this)) {
                AirPlayPersistence.saveHideTopBar(this, it); AirPlayPersistence.saveHideBottomBar(this, it)
            }
        }
        if (com.shilapi.xcertplay.hud.BydOutputSettings.available(this)) section(content, getString(R.string.section_byd_navigation), R.drawable.ic_dp_navigation) { card ->
            toggle(card, getString(R.string.toggle_byd_hud), getString(R.string.toggle_byd_hud_desc),
                com.shilapi.xcertplay.hud.BydOutputSettings.enabled(this)) { com.shilapi.xcertplay.hud.BydOutputSettings.setEnabled(this, it) }
            if (ClusterMapPresentation.findDisplay(this) != null) {
                toggle(card, getString(R.string.toggle_cluster_map), getString(R.string.toggle_cluster_map_desc),
                    AirPlayPersistence.loadClusterMapEnabled(this)) {
                    AirPlayPersistence.saveClusterMapEnabled(this, it)
                    reconnectForClusterMap()
                }
                if (DiLink51ClusterLayout.supported()) {
                    val automatic = DiLink51ClusterLayout.automatic(this)
                    toggle(card, getString(R.string.toggle_cluster_theme_follow), getString(R.string.toggle_cluster_theme_follow_desc), automatic) {
                        DiLink51ClusterLayout.saveAutomatic(this, it)
                        render()
                        reconnectForClusterMap()
                    }
                    val allowed = DiLink51ClusterMonitor.hasAccess(this)
                    card.addView(label(if (allowed) getString(R.string.cluster_access_enabled)
                        else getString(R.string.cluster_access_needed), 14, if (allowed) MUTED else WARNING))
                    card.addView(button(getString(R.string.action_cluster_access_setup), false) { showClusterAccessSetup() }, matchButton(10, 56))
                    if (!automatic) {
                        val themes = DiLink51ClusterLayout.Theme.entries
                        choice(card, getString(R.string.choice_cluster_theme), themes.map { clusterThemeLabel(it) }, themes.indexOf(DiLink51ClusterLayout.theme(this))) {
                            DiLink51ClusterLayout.saveTheme(this, themes[it])
                            reconnectForClusterMap()
                        }
                        card.addView(label(getString(R.string.cluster_theme_manual_note), 14, MUTED))
                    }
                    val contrasts = DiLink51ClusterLayout.Contrast.entries
                    choice(card, getString(R.string.choice_cluster_contrast), contrasts.map { clusterContrastLabel(it) }, contrasts.indexOf(DiLink51ClusterLayout.contrast(this))) {
                        DiLink51ClusterLayout.saveContrast(this, contrasts[it])
                        reconnectForClusterMap()
                    }
                } else {
                    val sizes = CarPlayClusterDisplay.scalePresets
                    choice(card, getString(R.string.choice_cluster_map_size), listOf(
                        getString(R.string.cluster_size_standard),
                        getString(R.string.cluster_size_larger),
                        getString(R.string.cluster_size_largest),
                    ), sizes.indexOf(AirPlayPersistence.loadClusterMapScalePercent(this)).coerceAtLeast(0)) {
                        AirPlayPersistence.saveClusterMapScalePercent(this, sizes[it])
                    }
                    val across = CarPlayClusterDisplay.horizontalSteps.toList()
                    choice(card, getString(R.string.choice_cluster_marker_horizontal), across.map {
                        markerStepLabel(it, getString(R.string.cluster_marker_left), getString(R.string.cluster_marker_right))
                    }, across.indexOf(AirPlayPersistence.loadClusterMarkerHorizontalStep(this)).coerceAtLeast(0)) {
                        AirPlayPersistence.saveClusterMarkerHorizontalStep(this, across[it])
                    }
                    val upDown = CarPlayClusterDisplay.verticalSteps.toList()
                    choice(card, getString(R.string.choice_cluster_marker_vertical), upDown.map {
                        markerStepLabel(it, getString(R.string.cluster_marker_up), getString(R.string.cluster_marker_down))
                    }, upDown.indexOf(AirPlayPersistence.loadClusterMarkerVerticalStep(this)).coerceAtLeast(0)) {
                        AirPlayPersistence.saveClusterMarkerVerticalStep(this, upDown[it])
                    }
                    card.addView(button(getString(R.string.action_reset_marker), false) {
                        AirPlayPersistence.saveClusterMarkerHorizontalStep(this, 0)
                        AirPlayPersistence.saveClusterMarkerVerticalStep(this, 0)
                        render()
                        reconnectForClusterMap()
                    }, matchButton(10, 56))
                }
            }
        }
        section(content, getString(R.string.section_permissions_help), R.drawable.ic_dp_permissions) { card ->
            card.addView(label(getString(R.string.permissions_body), 16, MUTED))
            card.addView(button(getString(R.string.action_app_permissions), false) { openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }, matchButton(16, 60))
            card.addView(button(getString(R.string.action_bluetooth_settings), false) { openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, matchButton(10, 60))
            card.addView(button(getString(R.string.action_wireless_help), false) { wirelessHelp() }, matchButton(10, 60))
        }
        section(content, getString(R.string.section_language)) { card -> languageControl(card) }
        section(content, getString(R.string.section_about), R.drawable.ic_dp_about) { card ->
            card.addView(button(getString(R.string.action_about_diplay), false) { page = "about"; render() }, matchButton(0, 60))
        }
    }

    /**
     * The language row. Unlike [choice] it never reconnects CarPlay: the projection interface keeps
     * its current language until the next connection, and only this settings page is rebuilt.
     */
    private fun languageControl(parent: LinearLayout) {
        val stored = DiPlayPreferences.language(this)
        val options = arrayOf(getString(R.string.language_english), getString(R.string.language_chinese))
        val current = if (AppLanguage.effective(this, stored) == AppLanguage.CHINESE) 1 else 0
        val control = button(getString(R.string.choice_language_named, options[current]), false) {}
        control.setOnClickListener {
            var selection = current
            AlertDialog.Builder(this).setTitle(R.string.choice_language)
                .setSingleChoiceItems(options, current) { _, index -> selection = index }
                .setPositiveButton(R.string.action_apply) { _, _ ->
                    if (selection == current) return@setPositiveButton
                    DiPlayPreferences.saveLanguage(this, if (selection == 1) AppLanguage.CHINESE else AppLanguage.ENGLISH)
                    recreatingForLanguage = true
                    recreate()
                }
                .setNegativeButton(R.string.action_cancel, null).show()
        }
        parent.addView(control, matchButton(0, 60))
        parent.addView(label(getString(R.string.language_note), 14, MUTED).apply { setPadding(0, dp(8), 0, dp(18)) })
    }

    /** Chinese copy needs tighter tracking than the all-caps Latin labels it replaces. */
    private fun languageIsChinese() = resources.configuration.locales[0].language == AppLanguage.CHINESE

    private fun tracking(latin: Float) = if (languageIsChinese()) .04f else latin

    private fun about(content: LinearLayout) {
        content.addView(label(getString(R.string.about_title), 40, TEXT, true))
        content.addView(label(getString(R.string.about_tagline), 20, MUTED).apply { setPadding(0, dp(8), 0, dp(24)) })
        section(content, getString(R.string.about_preview, version())) { card ->
            card.addView(label(getString(R.string.about_body), 17, TEXT))
        }
        section(content, getString(R.string.about_oss_section)) { card ->
            card.addView(label(getString(R.string.about_oss_body), 16, MUTED))
        }
    }

    // The car hotspot link needs the hotspot on; DiPlay only checks it (turning it on needs ADB-only permission).
    private fun carHotspotOff(): Boolean =
        AirPlayPersistence.loadWirelessHotspotMode(this) == WirelessHotspotMode.MANUAL &&
            com.shilapi.xcertplay.network.CarHotspotStatus.isEnabled(this) == false

    private fun carHotspotOffDialog() {
        AlertDialog.Builder(this).setTitle(R.string.dialog_hotspot_off_title)
            .setMessage(getString(R.string.dialog_hotspot_off_body, AirPlayPersistence.loadManualHotspotSsid(this)))
            .setPositiveButton(R.string.action_open_car_settings) { _, _ -> openCarWifiSettings() }
            .setNeutralButton(R.string.action_connect) { _, _ -> connect(true) }
            .setNegativeButton(R.string.action_cancel, null).show()
    }

    // BYD maps the AOSP tether action to its own hotspot screen; other firmware falls back to Wi-Fi settings.
    // BYD shows that screen as a dialog and closes it unless its own settings or the car home screen is on top,
    // so the home screen goes first.
    private fun openCarWifiSettings() {
        val hotspot = Intent("com.android.settings.WIFI_TETHER_SETTINGS")
        val target = packageManager.resolveActivity(hotspot, 0)?.activityInfo?.packageName
        if (target == null) {
            openSystem(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            return
        }
        if (target == "com.byd.carsettings") {
            runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        }
        if (runCatching { startActivity(hotspot) }.isSuccess) return
        openSystem(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    private fun openCarClientWifiSettings() {
        val wifi = Intent(Settings.ACTION_WIFI_SETTINGS)
        if (packageManager.resolveActivity(wifi, 0)?.activityInfo?.packageName == "com.byd.carsettings") {
            runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        }
        openSystem(wifi)
    }

    private fun connectionSetup(content: LinearLayout) {
        content.addView(label(getString(R.string.section_connection_setup), 34, TEXT, true))
        content.addView(label(getString(R.string.connection_setup_subhead), 17, MUTED).apply { setPadding(0, dp(8), 0, dp(24)) })
        section(content, getString(R.string.connection_step_choose)) { card -> wirelessLinkControls(card) }
        section(content, getString(R.string.connection_step_pair)) { card ->
            card.addView(label(getString(R.string.connection_pair_body), 16, MUTED))
            card.addView(button(getString(R.string.action_choose_iphone_named, DiPlayPreferences.phoneName(this)), false) { choosePhone() }, matchButton(12, 60))
            card.addView(button(getString(R.string.action_review_app_permissions), false) {
                openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }, matchButton(12, 60))
        }
        section(content, getString(R.string.connection_step_connect)) { card ->
            card.addView(label(getString(R.string.connection_connect_body), 16, MUTED))
            card.addView(button(getString(R.string.action_connect_phone), true) { connect(true) }, matchButton(12, 60))
        }
        section(content, getString(R.string.connection_step_cable)) { card ->
            card.addView(label(getString(R.string.connection_cable_body), 16, MUTED))
            card.addView(button(getString(R.string.action_connect_usb), false) { connect(false) }, matchButton(12, 60))
        }
    }

    private fun wirelessLinkControls(parent: LinearLayout) {
        val mode = if (pendingCarHotspotSetup) WirelessHotspotMode.MANUAL else AirPlayPersistence.loadWirelessHotspotMode(this)
        val modes = listOf(WirelessHotspotMode.MANUAL, WirelessHotspotMode.WIFI_P2P)
        val titles = listOf(
            getString(R.string.wireless_mode_builtin_hotspot),
            getString(R.string.wireless_mode_wifi_direct),
        )
        val descriptions = listOf(
            getString(R.string.wireless_mode_builtin_hotspot_desc),
            getString(R.string.wireless_mode_wifi_direct_desc),
        )
        val wide = resources.configuration.screenWidthDp >= 850
        val choices = if (wide) row().apply { gravity = Gravity.TOP } else column()
        parent.addView(choices)
        modes.forEachIndexed { index, candidate ->
            val option = column()
            choices.addView(option, if (wide) LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(16)
            } else LinearLayout.LayoutParams(-1, -2))
            option.addView(button("${if (mode == candidate) "✓  " else ""}${titles[index]}", mode == candidate) {
                if (candidate == WirelessHotspotMode.MANUAL) {
                    pendingCarHotspotSetup = true
                    render()
                } else {
                    pendingCarHotspotSetup = false
                    applyWirelessLink(candidate)
                }
            }, matchButton(12, 60))
            option.addView(label(descriptions[index], 15, MUTED).apply { setPadding(0, dp(6), 0, dp(12)) })
        }
        if (mode == WirelessHotspotMode.MANUAL) {
            parent.addView(label(getString(R.string.hotspot_setup_title), 22, TEXT, true))
            parent.addView(label(getString(R.string.hotspot_setup_steps), 16, MUTED).apply { setPadding(0, dp(8), 0, dp(12)) })
            parent.addView(button(getString(R.string.action_open_car_hotspot_settings), false) { openCarWifiSettings() }, matchButton(0, 60))
            parent.addView(button(if (pendingCarHotspotSetup) getString(R.string.action_save_hotspot_details_use_mode) else getString(R.string.action_edit_saved_hotspot, storedSsid()), false) {
                askHotspotCredentials { ssid, password ->
                    saveHotspotCredentials(ssid, password)
                    pendingCarHotspotSetup = false
                    applyWirelessLink(WirelessHotspotMode.MANUAL)
                }
            }, matchButton(12, 60))
            parent.addView(label(
                if (pendingCarHotspotSetup) getString(R.string.hotspot_status_finish_setup)
                else if (carHotspotOff()) getString(R.string.hotspot_status_off)
                else getString(R.string.hotspot_status_saved),
                15,
                if (carHotspotOff()) WARNING else MUTED,
            ).apply { setPadding(0, dp(12), 0, 0) })
        } else {
            parent.addView(label(getString(R.string.wifi_direct_steps), 16, MUTED))
            parent.addView(button(getString(R.string.action_open_car_wifi_settings), false) { openCarClientWifiSettings() }, matchButton(12, 60))
        }
    }

    private fun storedSsid() = AirPlayPersistence.loadManualHotspotSsid(this)
    private fun storedPassword() = AirPlayPersistence.loadManualHotspotPassphrase(this)

    /** The localized reason the credentials cannot be used, or null when they can. */
    private fun hotspotError(ssid: String, password: String): String? =
        ManualHotspotValidation.validate(ssid, password)?.let { problem ->
            getString(
                when (problem) {
                    ManualHotspotValidation.Problem.EMPTY_SSID -> R.string.hotspot_problem_empty_ssid
                    ManualHotspotValidation.Problem.SSID_TOO_LONG -> R.string.hotspot_problem_ssid_too_long
                    ManualHotspotValidation.Problem.INVALID_CHARACTER -> R.string.hotspot_problem_invalid_character
                    ManualHotspotValidation.Problem.PASSWORD_LENGTH -> R.string.hotspot_problem_password_length
                }
            )
        }

    private fun saveHotspotCredentials(ssid: String, password: String) {
        AirPlayPersistence.saveManualHotspotSsid(this, ssid)
        AirPlayPersistence.saveManualHotspotPassphrase(this, password)
        AirPlayPersistence.saveManualHotspotSecurity(this,
            com.shilapi.xcertplay.orchestration.ManualHotspotValidation.securityFor(password))
        AirPlayPersistence.saveManualHotspotBand(this, com.shilapi.xcertplay.orchestration.ManualHotspotBand.AUTO)
        AirPlayPersistence.saveManualHotspotChannel(this, 0)
    }

    private fun askHotspotCredentials(done: (String, String) -> Unit) {
        val fields = column().apply { setPadding(dp(24), dp(12), dp(24), dp(12)) }
        fields.addView(label(getString(R.string.hotspot_dialog_hint), 16, MUTED))
        val ssid = EditText(this).apply { hint = getString(R.string.hotspot_field_name); setText(storedSsid()); setSingleLine() }
        val password = EditText(this).apply {
            hint = getString(R.string.hotspot_field_password); setText(storedPassword()); setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        ssid.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        password.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        fun hideKeyboard() {
            val token = password.windowToken ?: ssid.windowToken
            (this.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(token, 0)
            ssid.clearFocus(); password.clearFocus()
        }
        ssid.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) { password.requestFocus(); true } else false
        }
        password.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { hideKeyboard(); true } else false
        }
        fields.addView(ssid); fields.addView(password)
        fields.addView(CheckBox(this).apply {
            text = getString(R.string.hotspot_show_password)
            setOnCheckedChangeListener { _, checked ->
                password.transformationMethod = if (checked) null else android.text.method.PasswordTransformationMethod.getInstance()
                password.setSelection(password.text.length)
            }
        })
        val error = label("", 14, WARNING)
        error.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        fields.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.dialog_hotspot_details_title)
            .setView(ScrollView(this).apply { addView(fields) })
            .setPositiveButton(R.string.action_save_details, null).setNegativeButton(R.string.action_cancel) { _, _ -> hideKeyboard() }
            .setNeutralButton(R.string.action_hide_keyboard, null).create()
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { hideKeyboard() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = ssid.text.toString().trim()
                val secret = password.text.toString()
                val problem = hotspotError(name, secret)
                if (problem != null) error.text = problem
                else { hideKeyboard(); dialog.dismiss(); done(name, secret) }
            }
        }
        dialog.show()
    }

    // "Left 20 %", "Centre · default", "Down 10 %": a signed step reads as a direction and a distance.
    private fun markerStepLabel(step: Int, negative: String, positive: String): String = when {
        step == 0 -> getString(R.string.cluster_marker_centre)
        step < 0 -> getString(
            R.string.cluster_marker_step,
            negative,
            (-step * CarPlayClusterDisplay.MARKER_STEP_PERCENT).toString(),
        )
        else -> getString(
            R.string.cluster_marker_step,
            positive,
            (step * CarPlayClusterDisplay.MARKER_STEP_PERCENT).toString(),
        )
    }

    /** Localized cluster theme name. The enum's [DiLink51ClusterLayout.Theme.label] stays English. */
    private fun clusterThemeLabel(theme: DiLink51ClusterLayout.Theme): String = getString(
        when (theme) {
            DiLink51ClusterLayout.Theme.SCENARIO -> R.string.cluster_theme_scenario
            DiLink51ClusterLayout.Theme.MAP -> R.string.cluster_theme_map
            DiLink51ClusterLayout.Theme.SIMPLE -> R.string.cluster_theme_simple
        }
    )

    /** Localized cluster contrast name. The enum's label stays English for logs. */
    private fun clusterContrastLabel(contrast: DiLink51ClusterLayout.Contrast): String = getString(
        when (contrast) {
            DiLink51ClusterLayout.Contrast.DEFAULT -> R.string.cluster_contrast_default
            DiLink51ClusterLayout.Contrast.LIGHT -> R.string.cluster_contrast_light
            DiLink51ClusterLayout.Contrast.DARK -> R.string.cluster_contrast_dark
        }
    )

    private fun showClusterAccessSetup() {
        val command = "adb shell appops set $packageName GET_USAGE_STATS allow"
        val body = column().apply { setPadding(dp(24), dp(12), dp(24), dp(12)) }
        body.addView(label(getString(R.string.cluster_setup_title), 20, TEXT, true))
        body.addView(label(getString(R.string.cluster_setup_body), 15, MUTED))
        body.addView(label(getString(R.string.cluster_setup_step_adb), 16, TEXT))
        body.addView(label(command, 16, TEXT).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(16), 0, dp(16))
        })
        body.addView(button(getString(R.string.action_copy_command), false) {
            getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
                android.content.ClipData.newPlainText("DiPlay Usage Access", command))
            toast(getString(R.string.toast_command_copied))
        }, matchButton(0, 56))
        body.addView(label(getString(R.string.cluster_setup_adb_hint, packageName), 14, MUTED))
        body.addView(label(getString(R.string.cluster_setup_step_check), 16, TEXT))
        val status = label(
            if (DiLink51ClusterMonitor.hasAccess(this)) getString(R.string.cluster_setup_enabled)
            else getString(R.string.cluster_setup_not_enabled),
            16,
            TEXT,
        )
        body.addView(status)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.dialog_cluster_setup_title)
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton(R.string.action_close, null)
            .setPositiveButton(R.string.action_check_and_enable, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (DiLink51ClusterMonitor.hasAccess(this)) {
                    AirPlayPersistence.saveClusterMapEnabled(this, true)
                    DiLink51ClusterLayout.saveAutomatic(this, true)
                    dialog.dismiss()
                    render()
                    toast(getString(R.string.toast_cluster_map_enabled))
                    reconnectForClusterMap()
                } else {
                    status.text = getString(R.string.cluster_setup_still_waiting)
                }
            }
        }
        dialog.show()
    }

    // The cluster screen is described at connection time, so a running session reconnects over
    // its current link. The position choices need no call: "Apply and reconnect" already does it.
    private fun reconnectForClusterMap() {
        if (CarPlayBackgroundSession.hasSession()) connect(AirPlayPersistence.loadWirelessEnabled(this))
    }

    private fun applyWirelessLink(mode: WirelessHotspotMode) {
        AirPlayPersistence.saveWirelessHotspotMode(this, mode)
        render()
        toast(getString(R.string.toast_saved_next_connection))
    }

    private fun carPlaySizeControl(parent: LinearLayout) {
        val sizes = CarPlaySize.entries
        val current = CarPlaySize.fromWidthMillimeters(AirPlayPersistence.loadWidthPhysicalMm(this))
        choice(parent, getString(R.string.choice_carplay_size), sizes.map { carPlaySizeLabel(it) }, sizes.indexOf(current)) {
            AirPlayPersistence.saveWidthPhysicalMm(this, sizes[it].widthMillimeters)
        }
        parent.addView(label(getString(R.string.choice_carplay_size_note), 14, MUTED).apply {
            setPadding(0, 0, 0, dp(18))
        })
    }

    /**
     * The localized size name. The exported report and the session log keep the enum's stable
     * English [CarPlaySize.label] instead, so diagnostics stay readable in English.
     */
    private fun carPlaySizeLabel(size: CarPlaySize): String = getString(
        when (size) {
            CarPlaySize.LARGE -> R.string.carplay_size_large
            CarPlaySize.MEDIUM -> R.string.carplay_size_medium
            CarPlaySize.SMALL -> R.string.carplay_size_small
        }
    )

    private fun connect(wireless: Boolean) {
        if (wireless && pendingCarHotspotSetup) { toast(getString(R.string.toast_save_hotspot_first)); page = "connection"; render(); return }
        if (setupError != null) { toast(setupError!!); return }
        if (wireless && AirPlayPersistence.loadWirelessHotspotMode(this) == WirelessHotspotMode.MANUAL &&
            hotspotError(storedSsid(), storedPassword()) != null) {
            pendingCarHotspotSetup = true
            page = "connection"
            render()
            toast(getString(R.string.toast_save_hotspot_credentials_first))
            return
        }
        if (wireless && carHotspotOff()) { carHotspotOffDialog(); return }
        if (wireless && DiPlayPreferences.phoneAddress(this) == null) {
            pendingWireless = true; choosePhone(); return
        }
        val preferences = getSharedPreferences("diplay", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !preferences.getBoolean("notification_asked", false)) {
            preferences.edit().putBoolean("notification_asked", true).apply()
            notificationTransport = wireless
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val open = {
            AirPlayPersistence.saveWirelessEnabled(this, wireless)
            openProjection()
        }
        if (CarPlayBackgroundSession.hasSession()) CarPlayBackgroundSession.stop { runOnUiThread { open() } }
        else open()
    }
    private fun openProjection() {
        startActivity(Intent(this, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
    private fun choosePhone() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT); return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            AlertDialog.Builder(this).setTitle(R.string.dialog_bluetooth_off_title)
                .setMessage(R.string.dialog_bluetooth_off_body)
                .setPositiveButton(R.string.action_open_bluetooth) { _, _ -> openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .setNegativeButton(R.string.action_later, null).show(); return
        }
        val devices = runCatching { adapter.bondedDevices.sortedBy { it.name ?: "" } }.getOrDefault(emptyList())
        if (devices.isEmpty()) {
            AlertDialog.Builder(this).setTitle(R.string.dialog_pair_iphone_title)
                .setMessage(R.string.dialog_pair_iphone_body)
                .setPositiveButton(R.string.action_open_bluetooth) { _, _ -> openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .setNegativeButton(R.string.action_got_it, null).show(); return
        }
        AlertDialog.Builder(this).setTitle(R.string.dialog_choose_iphone_title)
            .setItems(devices.map { device ->
                val name = device.name ?: getString(R.string.paired_device_fallback)
                if (devices.count { it.name == device.name } > 1) "$name · ${device.address.takeLast(5)}" else name
            }.toTypedArray()) { _, index ->
                val device = devices[index]
                DiPlayPreferences.savePhone(this, device.address, device.name ?: "iPhone")
                val start = pendingWireless; pendingWireless = false
                render()
                if (start) connect(true)
            }.setNeutralButton(R.string.action_pair_another) { _, _ -> openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .setNegativeButton(R.string.action_cancel) { _, _ -> pendingWireless = false }.show()
    }

    private fun wirelessHelp() {
        AlertDialog.Builder(this).setTitle(R.string.dialog_wireless_help_title)
            .setMessage(R.string.dialog_wireless_help_body)
            .setPositiveButton(R.string.action_got_it, null)
            .setNeutralButton(R.string.action_reset_carplay_wifi) { _, _ ->
                confirmWirelessReset()
            }.show()
    }

    private fun handleWirelessRecovery() {
        if (page != "wireless-recovery") return
        page = "home"; render()
        confirmWirelessReset()
    }

    private fun confirmWirelessReset() {
        AlertDialog.Builder(this).setTitle(R.string.dialog_reset_title)
            .setMessage(R.string.dialog_reset_body)
            .setPositiveButton(R.string.action_reset_and_connect) { _, _ ->
                CarPlayBackgroundSession.stop { runOnUiThread { resetWirelessGroup() } }
            }.setNegativeButton(R.string.action_cancel, null).show()
    }

    private fun resetWirelessGroup() {
        val manager = getSystemService(android.net.wifi.p2p.WifiP2pManager::class.java)
        if (manager == null) { toast(getString(R.string.toast_no_wifi_direct)); return }
        val channel = manager.initialize(this, mainLooper, null)
        try {
            manager.requestGroupInfo(channel) { group ->
                if (group == null) { channel.close(); connect(true); return@requestGroupInfo }
                manager.removeGroup(channel, object : android.net.wifi.p2p.WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        val deadline = android.os.SystemClock.elapsedRealtime() + 4000
                        fun waitUntilRemoved() {
                            manager.requestGroupInfo(channel) { remaining ->
                                when {
                                    remaining == null -> { channel.close(); if (!isFinishing && !isDestroyed) connect(true) }
                                    android.os.SystemClock.elapsedRealtime() >= deadline -> {
                                        channel.close(); toast(getString(R.string.toast_wifi_direct_busy))
                                    }
                                    else -> handler.postDelayed({ waitUntilRemoved() }, 200)
                                }
                            }
                        }
                        waitUntilRemoved()
                    }
                    override fun onFailure(reason: Int) { channel.close(); toast(getString(R.string.toast_wifi_reset_failed)) }
                })
            }
        } catch (_: SecurityException) {
            channel.close(); permissionHelp(getString(R.string.permission_help_wireless_title), getString(R.string.permission_help_wireless_body))
        }
    }

    private fun refreshStatus() {
        val running = CarPlayBackgroundSession.hasSession()
        status?.text = when {
            setupError != null -> getString(R.string.status_setup_attention)
            CarPlayBackgroundSession.active -> getString(R.string.status_carplay_connected)
            running -> getString(R.string.status_connecting)
            DiPlayPreferences.phoneAddress(this) != null -> getString(R.string.status_ready_for, DiPlayPreferences.phoneName(this))
            else -> getString(R.string.status_ready)
        }
        if (lastRunning != running) {
            connectButton?.text = getString(if (running) R.string.status_open_carplay else R.string.action_connect_phone)
            disconnectButton?.visibility = if (running) View.VISIBLE else View.GONE
            disconnectButton?.isEnabled = true
            lastRunning = running
        }
        connectButton?.isEnabled = setupError == null
    }
    private fun reportFileName() = "DiPlay-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())}.txt"

    private fun chooseReportDestination() {
        // Some head units omit or disable DocumentsUI. Launch itself can throw, before
        // the result callback and the background writer's exception handler ever run.
        runCatching { export.launch(reportFileName()) }.onFailure {
            toast(getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                R.string.toast_save_location_unavailable
                else R.string.toast_no_file_picker))
        }
    }

    private fun exportDiagnostics(uri: Uri? = null) {
        if (exportInProgress) return
        exportInProgress = true
        exportButton?.apply { isEnabled = false; text = getString(R.string.action_saving_report) }
        val appContext = applicationContext
        val fileName = reportFileName()
        Thread({
            val result = runCatching {
                val report = buildString {
                    appendLine("DiPlay ${version()} · private beta diagnostic report")
                    appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                    appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Connection: ${if (AirPlayPersistence.loadWirelessEnabled(appContext)) "wireless" else "USB"}")
                    appendLine("Authentication: local experimental beta identity; no remote fallback")
                    appendLine("CarPlay setup: ${if (setupError == null) "ready" else "authentication unavailable"}")
                    appendLine("Saved video preference (may differ from active session): ${if (AirPlayPersistence.loadHevcEnabled(appContext)) "HEVC" else "H.264"}; ${AirPlayPersistence.loadFps(appContext)} fps")
                    appendLine("CarPlay size: ${com.shilapi.xcertplay.airplay.CarPlaySize.fromWidthMillimeters(AirPlayPersistence.loadWidthPhysicalMm(appContext)).label}")
                    appendLine("Saved resolution preference (may differ from active session): ${AirPlayPersistence.loadDisplayScaleTenths(appContext) * 10}%")
                    appendLine("Session: ${if (CarPlayBackgroundSession.active) "active" else if (CarPlayBackgroundSession.hasSession()) "connecting" else "stopped"}")
                    appendLine("Head-unit board: ${Build.BOARD}; hardware: ${Build.HARDWARE}; build: ${Build.DISPLAY}")
                    appendLine()
                    appendLine("--- Last display negotiation (timestamps distinguish it from current settings) ---")
                    appendLine(DisplayDiagnosticSnapshot.report(appContext))
                    appendLine()
                    for (name in SessionLogFile.REPORT_NAMES) {
                        val file = File(appContext.filesDir, "logs/$name")
                        if (file.isFile) {
                            appendLine("--- $name ---")
                            file.useLines { lines -> lines.forEach { line -> DiagnosticRedactor.redact(line)?.let { appendLine(it) } } }
                        }
                    }
                }
                if (uri != null) { DiagnosticExportStore.write(appContext.contentResolver, uri, report); uri }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    DiagnosticExportStore.saveToDownloads(appContext.contentResolver, fileName, report)
                } else error("A save location is required")
            }
            runOnUiThread {
                exportInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                exportButton?.apply { isEnabled = true; text = getString(R.string.action_save_report) }
                if (result.isSuccess) {
                    val savedUri = result.getOrThrow()
                    AlertDialog.Builder(this).setTitle(R.string.dialog_report_saved_title)
                        .setMessage(if (uri == null) getString(R.string.report_saved_path, fileName) else getString(R.string.report_saved_other))
                        .setPositiveButton(R.string.action_done, null)
                        .setNeutralButton(R.string.action_share) { _, _ ->
                            runCatching {
                                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, savedUri)
                                    clipData = android.content.ClipData.newRawUri("Diagnostic report", savedUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, getString(R.string.share_diagnostic_report)))
                            }.onFailure { toast(getString(R.string.toast_report_saved_share_manually)) }
                        }.show()
                } else {
                    AlertDialog.Builder(this).setTitle(R.string.dialog_report_failed_title)
                        .setMessage(R.string.dialog_report_failed_body)
                        .setPositiveButton(R.string.action_choose_location) { _, _ -> chooseReportDestination() }
                        .setNegativeButton(R.string.action_close, null).show()
                }
            }
        }, "diplay-export").start()
    }
    private fun permissionHelp(title: String, body: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton(R.string.action_app_settings) { _, _ ->
            openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }.setNegativeButton(R.string.action_later, null).show()
    }
    private fun openSystem(intent: Intent) { runCatching { startActivity(intent) }.onFailure { toast(getString(R.string.toast_open_from_car_settings)) } }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun version() = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0-beta.1"
    private fun section(parent: LinearLayout, title: String, icon: Int? = null, build: (LinearLayout) -> Unit) {
        val card = card()
        val heading = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(16)) }
        if (icon != null) heading.addView(ImageView(this).apply {
            setImageResource(icon); imageTintList = ColorStateList.valueOf(ACCENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
        heading.addView(label(title, 22, TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(heading)
        build(card)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
    }
    private fun toggle(parent: LinearLayout, title: String, description: String, value: Boolean, save: (Boolean) -> Unit) {
        val line = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        val text = column(); text.addView(label(title, 18, TEXT, true)); text.addView(label(description, 14, MUTED).apply { setPadding(0, dp(6), dp(16), 0) })
        line.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Switch(this).apply { contentDescription = title; isChecked = value; minHeight = dp(56); buttonTintList = ColorStateList.valueOf(ACCENT); setOnCheckedChangeListener { _, checked -> save(checked) } })
        parent.addView(line)
    }
    private fun choice(parent: LinearLayout, title: String, options: List<String>, current: Int, save: (Int) -> Unit) {
        var selection = current
        val button = button("$title · ${options[selection]}", false) {}
        button.setOnClickListener {
            var pendingSelection = selection
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), selection) { _, index -> pendingSelection = index }
                .setPositiveButton(if (CarPlayBackgroundSession.hasSession()) R.string.action_apply_reconnect else R.string.action_save) { _, _ ->
                    if (pendingSelection != selection) {
                        selection = pendingSelection
                        save(selection)
                        button.text = "$title · ${options[selection]}"
                        if (CarPlayBackgroundSession.hasSession()) {
                            connect(AirPlayPersistence.loadWirelessEnabled(this))
                        }
                    }
                }.setNegativeButton(R.string.action_cancel, null).show()
        }
        parent.addView(button, matchButton(0, 60)); parent.addView(space(12))
    }
    private fun card() = column().apply { background = rounded(SURFACE, BORDER); setPadding(dp(24), dp(24), dp(24), dp(24)) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(title: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 18f; setTextColor(if (primary) BG else TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x336F9FD9), rounded(if (primary) ACCENT else SURFACE, if (primary) ACCENT else BORDER), null)
        setPadding(dp(16), 0, dp(16), 0); minHeight = dp(56); stateListAnimator = null
        setOnClickListener { click() }
    }
    private fun rounded(color: Int, stroke: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat(); setStroke(dp(1), stroke) }
    private fun matchButton(top: Int = 0, height: Int = 68) = LinearLayout.LayoutParams(-1, dp(height)).apply { topMargin = dp(top) }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private const val KEY_SKIP_AUTO_CONNECT = "skip_auto_connect"
        private val BG = Color.rgb(12, 17, 27)
        private val SURFACE = Color.rgb(21, 30, 44)
        private val BORDER = Color.rgb(42, 56, 75)
        private val ACCENT = Color.rgb(166, 200, 255)
        private val TEXT = Color.rgb(241, 245, 252)
        private val MUTED = Color.rgb(168, 182, 202)
        private val WARNING = Color.rgb(255, 196, 128)
    }
}
