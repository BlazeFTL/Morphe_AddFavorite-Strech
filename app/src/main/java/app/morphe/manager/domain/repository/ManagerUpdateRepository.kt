/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for the available Morphe Manager update.
 *
 * The home banner, the update dialog and the background worker resolve the same asset here,
 * so every surface announces and downloads exactly the release the others resolved.
 */
class ManagerUpdateRepository(private val morpheAPI: MorpheAPI) {
    private val _availableUpdate = MutableStateFlow<MorpheAsset?>(null)

    /** The most recently resolved update, or null while none is known to be available. */
    val availableUpdate: StateFlow<MorpheAsset?> = _availableUpdate.asStateFlow()

    // Serializes concurrent checks so a pull-to-refresh and an opening dialog share one
    // network round trip instead of racing each other
    private val refreshMutex = Mutex()

    /**
     * Re-checks for an update and publishes the result.
     *
     * Returns null both when the app is up to date and when the check failed, mirroring
     * [MorpheAPI.getAppUpdate], and clears the cached asset so the next reader resolves the
     * state from scratch instead of acting on a stale release.
     */
    suspend fun refresh(): MorpheAsset? = refreshMutex.withLock { fetch() }

    /** Returns the cached update if a check already resolved one, otherwise checks now. */
    suspend fun getOrRefresh(): MorpheAsset? =
        _availableUpdate.value ?: refreshMutex.withLock { _availableUpdate.value ?: fetch() }

    private suspend fun fetch(): MorpheAsset? =
        morpheAPI.getAppUpdate().also { _availableUpdate.value = it }
}
