package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.connectivity.ConnectivityObserver
import com.example.accessoriesmanager.di.ApplicationScope
import com.example.accessoriesmanager.local.dao.HeadquarterDao
import com.example.accessoriesmanager.local.dao.InstallationDao
import com.example.accessoriesmanager.local.dao.PendingOperationDao
import com.example.accessoriesmanager.local.entity.PendingEntityType
import com.example.accessoriesmanager.local.entity.PendingOperationEntity
import com.example.accessoriesmanager.local.entity.PendingOperationType
import com.example.accessoriesmanager.local.entity.toEntity
import com.example.accessoriesmanager.local.entity.toModel
import com.example.accessoriesmanager.model.Headquarter
import com.example.accessoriesmanager.sync.SyncManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Room-first, Firestore-when-possible decorator over [FirebaseHeadquarterRepository] — see [OfflineAccessoryRepository]. */
@Singleton
class OfflineHeadquarterRepository @Inject constructor(
    private val firebase: FirebaseHeadquarterRepository,
    private val dao: HeadquarterDao,
    private val installationDao: InstallationDao,
    private val pendingOperationDao: PendingOperationDao,
    private val connectivityObserver: ConnectivityObserver,
    private val syncManager: SyncManager,
    @ApplicationScope private val appScope: CoroutineScope,
    private val gson: Gson
) : HeadquarterRepository {

    override fun listenHeadquarters(
        onChange: (List<Headquarter>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val cacheJob: Job = appScope.launch {
            dao.observeAll().collect { entities -> onChange(entities.map { it.toModel() }) }
        }

        val firestoreRegistration: ListenerRegistration? = if (connectivityObserver.isOnline.value) {
            firebase.listenHeadquarters(
                onChange = { list ->
                    onChange(list)
                    appScope.launch { dao.replaceAll(list.map { it.toEntity() }) }
                },
                onError = onError
            )
        } else null

        return ListenerRegistration {
            cacheJob.cancel()
            firestoreRegistration?.remove()
        }
    }

    override suspend fun add(headquarter: Headquarter): String {
        val localId = "local_" + UUID.randomUUID()
        val toSave = headquarter.copy(id = localId)
        dao.upsert(toSave.toEntity())

        if (connectivityObserver.isOnline.value) {
            val realId = runCatching { firebase.add(headquarter) }.getOrNull()
            if (realId != null) {
                dao.delete(localId)
                dao.upsert(headquarter.copy(id = realId).toEntity())
                return realId
            }
        }

        enqueue(PendingOperationType.CREATE, targetId = null, payload = toSave)
        return localId
    }

    override suspend fun update(id: String, headquarter: Headquarter) {
        val toSave = headquarter.copy(id = id)
        dao.upsert(toSave.toEntity())

        if (connectivityObserver.isOnline.value && runCatching { firebase.update(id, headquarter) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.UPDATE, targetId = id, payload = toSave)
    }

    override suspend fun deleteHeadquarter(id: String) {
        dao.delete(id)

        if (connectivityObserver.isOnline.value && runCatching { firebase.deleteHeadquarter(id) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.DELETE, targetId = id, payload = null)
    }

    override suspend fun getById(id: String): Headquarter? =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.getById(id) }.getOrNull() ?: dao.getById(id)?.toModel()
        } else {
            dao.getById(id)?.toModel()
        }

    override suspend fun existsByName(name: String): Boolean =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.existsByName(name) }.getOrElse { existsByNameLocally(name) }
        } else {
            existsByNameLocally(name)
        }

    override suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.existsByNameExcludingId(name, excludeId) }
                .getOrElse { existsByNameLocally(name, excludeId) }
        } else {
            existsByNameLocally(name, excludeId)
        }

    override suspend fun getAll(): List<Headquarter> =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.getAll() }.getOrElse { dao.getAllOnce().map { it.toModel() } }
        } else {
            dao.getAllOnce().map { it.toModel() }
        }

    /** Best-effort offline: scans the (≤10) cached installations for this hq's most recent increment. */
    override suspend fun getIncrement(headquarterId: String): Int =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.getIncrement(headquarterId) }.getOrElse { getIncrementLocally(headquarterId) }
        } else {
            getIncrementLocally(headquarterId)
        }

    private suspend fun getIncrementLocally(headquarterId: String): Int =
        installationDao.getRecent10Once()
            .filter { it.headquarterId == headquarterId }
            .maxByOrNull { it.createdAt ?: 0L }
            ?.increment?.toInt() ?: 0

    private suspend fun existsByNameLocally(name: String, excludeId: String? = null): Boolean =
        dao.getAllOnce().any { it.name.equals(name, ignoreCase = true) && it.id != excludeId }

    private suspend fun enqueue(operation: PendingOperationType, targetId: String?, payload: Headquarter?) {
        pendingOperationDao.insert(
            PendingOperationEntity(
                entityType = PendingEntityType.HEADQUARTER.name,
                operationType = operation.name,
                targetId = targetId,
                payloadJson = payload?.let { gson.toJson(it) } ?: "",
                createdAt = System.currentTimeMillis(),
                status = "PENDING",
                lastError = null
            )
        )
        syncManager.syncNow()
    }
}
