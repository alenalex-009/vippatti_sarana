package com.example.config

import android.util.Log
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppRemoteConfig(
    val homePadding: Int = 0,
    val primaryColorHex: String = "",
    val secondaryColorHex: String = "",
    val emergencyBannerText: String = "",
    val emergencyBannerEnabled: Boolean = false,
    val featureRadarEnabled: Boolean = true,
    val featureDispatchesEnabled: Boolean = true,
    val appLogoUrl: String = ""
)

class RemoteConfigManager {
    private val _configState = MutableStateFlow(AppRemoteConfig())
    val configState: StateFlow<AppRemoteConfig> = _configState.asStateFlow()

    private val remoteConfig: FirebaseRemoteConfig? = try {
        FirebaseRemoteConfig.getInstance()
    } catch (_: IllegalStateException) {
        Log.w("RemoteConfigManager", "FirebaseApp not initialised; using local defaults")
        null
    }

    init {
        val rc = remoteConfig
        if (rc != null) {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
            rc.setConfigSettingsAsync(configSettings)

            // Defaults are now registered; advance to fetch + realtime listener.
            // Do NOT call updateState() here: on a fresh install Firebase may still
            // report unset booleans as false through getBoolean() even after
            // setDefaultsAsync completes, which would publish featureRadarEnabled=
            // false / featureDispatchesEnabled=false and hide the Map + News tabs on
            // the first frame. The _configState is already seeded with
            // AppRemoteConfig() (both killable modules ON) at construction, so every
            // frame before the first fetchAndActivate lands is correct. We only
            // publish a new config when fetchAndActivate actually returns values.
            fetchAndActivate(rc)
            setupRealtimeUpdates(rc)
        }
    }

    private fun fetchAndActivate(rc: FirebaseRemoteConfig) {
        rc.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                updateState(rc)
            }
        }
    }

    private fun setupRealtimeUpdates(rc: FirebaseRemoteConfig) {
        rc.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                rc.activate().addOnCompleteListener {
                    updateState(rc)
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w("RemoteConfigManager", "Real-time update error", error)
            }
        })
    }

    private fun updateState(rc: FirebaseRemoteConfig) {
        _configState.update {
            AppRemoteConfig(
                homePadding = rc.getLong("home_padding").toInt(),
                primaryColorHex = rc.getString("primary_color"),
                secondaryColorHex = rc.getString("secondary_color"),
                emergencyBannerText = rc.getString("emergency_banner_text"),
                emergencyBannerEnabled = rc.getBoolean("emergency_banner_enabled"),
                featureRadarEnabled = rc.getBoolean("feature_radar_enabled"),
                featureDispatchesEnabled = rc.getBoolean("feature_dispatches_enabled"),
                appLogoUrl = rc.getString("app_logo_url")
            )
        }
    }
}

object ConfigRegistry {
    val manager: RemoteConfigManager by lazy { RemoteConfigManager() }
}

/**
 * The defaults registered with Firebase, mirrored by [AppRemoteConfig]'s
 * constructor defaults. Both killable modules default ON: a fresh install must
 * never see a module disappear just because Firebase has no value for it yet
 * (the SDK reports unset booleans as false, which would hide the tabs on the
 * first launch - see the race fix in [RemoteConfigManager]'s init).
 */
object RemoteConfigDefaults {
    val firebaseDefaults: Map<String, Any> = mapOf(
        "home_padding" to 0,
        "primary_color" to "",
        "secondary_color" to "",
        "emergency_banner_text" to "",
        "emergency_banner_enabled" to false,
        "feature_radar_enabled" to true,
        "feature_dispatches_enabled" to true,
        "app_logo_url" to ""
    )
}
