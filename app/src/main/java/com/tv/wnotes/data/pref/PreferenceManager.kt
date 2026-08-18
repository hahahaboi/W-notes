package com.tv.wnotes.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {
    companion object {
        val FLOATING_BUTTON_X = floatPreferencesKey("floating_button_x")
        val FLOATING_BUTTON_Y = floatPreferencesKey("floating_button_y")
        val IS_COLLAPSED = booleanPreferencesKey("is_collapsed")
        val DOCKED_EDGE = stringPreferencesKey("docked_edge")
        val LOCKED_PIN = stringPreferencesKey("locked_pin")
    }

    val floatingButtonX: Flow<Float?> = context.dataStore.data.map { it[FLOATING_BUTTON_X] }
    val floatingButtonY: Flow<Float?> = context.dataStore.data.map { it[FLOATING_BUTTON_Y] }
    val isCollapsed: Flow<Boolean> = context.dataStore.data.map { it[IS_COLLAPSED] ?: false }
    val dockedEdge: Flow<String?> = context.dataStore.data.map { it[DOCKED_EDGE] }
    val lockedPin: Flow<String?> = context.dataStore.data.map { it[LOCKED_PIN] }

    fun sectionSort(sectionKey: String): Flow<String> {
        val key = stringPreferencesKey("section_sort_$sectionKey")
        return context.dataStore.data.map { it[key] ?: "RECENTLY_EDITED" }
    }

    suspend fun savePosition(x: Float, y: Float) {
        context.dataStore.edit {
            it[FLOATING_BUTTON_X] = x
            it[FLOATING_BUTTON_Y] = y
        }
    }

    suspend fun saveCollapsedState(collapsed: Boolean, edge: String?) {
        context.dataStore.edit {
            it[IS_COLLAPSED] = collapsed
            if (edge != null) it[DOCKED_EDGE] = edge
        }
    }

    suspend fun saveSectionSort(sectionKey: String, sortName: String) {
        val key = stringPreferencesKey("section_sort_$sectionKey")
        context.dataStore.edit {
            it[key] = sortName
        }
    }

    suspend fun saveLockedPin(pin: String) {
        context.dataStore.edit {
            it[LOCKED_PIN] = pin
        }
    }
}
