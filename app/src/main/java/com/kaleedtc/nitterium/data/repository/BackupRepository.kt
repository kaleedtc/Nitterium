package com.kaleedtc.nitterium.data.repository

import android.content.Context
import android.net.Uri
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BackupRepository(
    context: Context,
    private val subscriptions: SubscriptionRepository,
    private val groups: FeedGroupRepository,
    private val preferences: UserPreferencesRepository,
    private val instanceOptions: InstanceOptionsStore = CookieInstanceOptionsStore()
) {
    private val resolver = context.applicationContext.contentResolver
    private val mutex = Mutex()

    suspend fun export(uri: Uri, scope: BackupScope, knownInstances: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val includeSettings = scope == BackupScope.SETTINGS || scope == BackupScope.EVERYTHING
            val includeSubscriptions = scope != BackupScope.SETTINGS
            val includeGroups = scope == BackupScope.SUBSCRIPTIONS_AND_GROUPS || scope == BackupScope.EVERYTHING
            val settings = if (includeSettings) preferences.backupSettings().let { snapshot ->
                val urls = (knownInstances + snapshot.customInstances + snapshot.instanceUrl +
                    snapshot.frontendModes.keys + snapshot.detectedFrontends.keys).distinct()
                snapshot.copy(instanceOptions = urls.associateWith { url ->
                    val normalized = url.trimEnd('/')
                    val frontend = snapshot.frontendModes[normalized]?.takeUnless { it == FrontendType.AUTO }
                        ?: snapshot.detectedFrontends[normalized] ?: FrontendType.NITTER
                    InstanceOptionsBackup(frontend, instanceOptions.read(url, frontend))
                })
            } else null
            val backup = NitteriumBackup(
                subscriptions = if (includeSubscriptions) subscriptions.subscriptions.first() else null,
                groups = if (includeGroups) groups.groups.first() else null,
                settings = settings
            )
            val content = BackupCodec.encode(backup, scope)
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(content) }
                ?: error("Could not open backup destination")
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun read(uri: Uri): Result<NitteriumBackup> = withContext(Dispatchers.IO) {
        try {
            val content = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                ImportLimits.read(reader)
            } ?: error("Could not open backup")
            Result.success(BackupCodec.decode(content, preferences.instanceUrl.first()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun restore(backup: NitteriumBackup): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            var previousSettings: AppSettingsBackup? = null
            var previousOptions: Map<String, InstanceOptionsBackup> = emptyMap()
            var settingsApplied = false
            try {
                BackupCodec.validate(backup)
                backup.settings?.let { settings ->
                    previousSettings = preferences.backupSettings()
                    previousOptions = settings.instanceOptions.mapValues { (url, saved) ->
                        saved.copy(options = instanceOptions.read(url, saved.frontend))
                    }
                    settings.instanceOptions.forEach { (url, saved) -> instanceOptions.write(url, saved.frontend, saved.options) }
                    preferences.restoreSettings(settings)
                    settingsApplied = true
                }
                backup.subscriptions?.let { saved ->
                    subscriptions.mergeImportedSubscriptions(
                        SubscriptionImport(saved, backup.groups.orEmpty(), backup.skipped), groups
                    ).getOrThrow()
                }
                Result.success(Unit)
            } catch (error: Exception) {
                // Repositories already roll back imported groups if saving profiles
                // fails. Undo preference/cookie changes from this restore as well.
                withContext(NonCancellable) {
                    if (settingsApplied) runCatching { preferences.restoreSettings(previousSettings!!) }
                        .exceptionOrNull()?.let(error::addSuppressed)
                    previousOptions.forEach { (url, saved) ->
                        runCatching { instanceOptions.write(url, saved.frontend, saved.options) }
                            .exceptionOrNull()?.let(error::addSuppressed)
                    }
                }
                if (error is CancellationException) throw error
                Result.failure(error)
            }
        }
    }
}
