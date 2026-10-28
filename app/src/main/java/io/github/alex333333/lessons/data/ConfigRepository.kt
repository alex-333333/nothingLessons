package io.github.alex333333.lessons.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.ConfigCodec
import io.github.alex333333.lessons.widget.WidgetRefresher
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lessons")

/** The whole user configuration, stored as one JSON string. Use [ConfigRepository.get]. */
class ConfigRepository private constructor(private val context: Context) {

    val config: Flow<Config> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { ConfigCodec.decode(it[CONFIG_JSON]) }
        .distinctUntilChanged()

    /** Atomically replaces the config with [transform] of the current one, then redraws the widget. */
    suspend fun update(transform: (Config) -> Config) {
        context.dataStore.edit { prefs ->
            prefs[CONFIG_JSON] = ConfigCodec.encode(transform(ConfigCodec.decode(prefs[CONFIG_JSON])))
        }
        WidgetRefresher.refresh(context)
    }

    companion object {
        private val CONFIG_JSON = stringPreferencesKey("config_json")

        @Volatile
        private var instance: ConfigRepository? = null

        fun get(context: Context): ConfigRepository =
            instance ?: synchronized(this) {
                instance ?: ConfigRepository(context.applicationContext).also { instance = it }
            }
    }
}
