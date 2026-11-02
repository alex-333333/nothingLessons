package io.github.alex333333.bridge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.alex333333.bridge.protocol.PcRecord
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bridgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "bridge")

/** What the phone remembers: its own device id (made once) and the one paired PC. */
class BridgeStore(context: Context) {
    private val store = context.applicationContext.bridgeDataStore

    val pc: Flow<PcRecord?> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { PcRecord.fromJson(it[PC_JSON]) }
        .distinctUntilChanged()

    /** The id of this phone for the PC; a random UUID created on first use and kept. */
    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        var result = ""
        store.edit { prefs ->
            result = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return result
    }

    suspend fun savePc(record: PcRecord) {
        store.edit { it[PC_JSON] = record.toJson() }
    }

    suspend fun clearPc() {
        store.edit { it.remove(PC_JSON) }
    }

    private companion object {
        val PC_JSON = stringPreferencesKey("pc_json")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
