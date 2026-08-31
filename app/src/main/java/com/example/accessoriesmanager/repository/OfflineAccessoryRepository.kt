package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.connectivity.ConnectivityObserver
import com.example.accessoriesmanager.di.ApplicationScope
import com.example.accessoriesmanager.local.dao.AccessoryDao
import com.example.accessoriesmanager.local.dao.PendingOperationDao
import com.example.accessoriesmanager.local.entity.PendingEntityType
import com.example.accessoriesmanager.local.entity.PendingOperationEntity
import com.example.accessoriesmanager.local.entity.PendingOperationType
import com.example.accessoriesmanager.local.entity.toEntity
import com.example.accessoriesmanager.local.entity.toModel
import com.example.accessoriesmanager.model.Accessory
import com.example.accessoriesmanager.sync.SyncManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first, Firestore-when-possible decorator over [FirebaseAccessoryRepository]: reads come
 * from Room instantly (offline-safe), writes apply to Room immediately and either go straight to
 * Firestore (when online) or get queued as a [PendingOperationEntity] for [SyncManager] to replay.
 */
@Singleton
class OfflineAccessoryRepository @Inject constructor(
    private val firebase: FirebaseAccessoryRepository,
    private val dao: AccessoryDao,
    private val pendingOperationDao: PendingOperationDao,
    private val connectivityObserver: ConnectivityObserver,
    private val syncManager: SyncManager,
    @ApplicationScope private val appScope: CoroutineScope,
    private val gson: Gson
) : AccessoryRepository {

    override fun listenAccessories(
        onChange: (List<Accessory>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val cacheJob: Job = appScope.launch {
            dao.observeAll().collect { entities -> onChange(entities.map { it.toModel() }) }
        }

        val firestoreRegistration: ListenerRegistration? = if (connectivityObserver.isOnline.value) {
            firebase.listenAccessories(
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

    override suspend fun add(accessory: Accessory): String {
        val localId = "local_" + UUID.randomUUID()
        val toSave = accessory.copy(id = localId)
        dao.upsert(toSave.toEntity())

        if (connectivityObserver.isOnline.value) {
            val realId = runCatching { firebase.add(accessory) }.getOrNull()
            if (realId != null) {
                dao.delete(localId)
                dao.upsert(accessory.copy(id = realId).toEntity())
                return realId
            }
        }

        enqueue(PendingOperationType.CREATE, targetId = null, payload = toSave)
        return localId
    }

    override suspend fun update(id: String, accessory: Accessory) {
        val toSave = accessory.copy(id = id)
        dao.upsert(toSave.toEntity())

        if (connectivityObserver.isOnline.value && runCatching { firebase.update(id, accessory) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.UPDATE, targetId = id, payload = toSave)
    }

    override suspend fun deleteAccessory(id: String) {
        dao.delete(id)

        if (connectivityObserver.isOnline.value && runCatching { firebase.deleteAccessory(id) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.DELETE, targetId = id, payload = null)
    }

    override suspend fun getById(id: String): Accessory? =
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

    override suspend fun getAll(): List<Accessory> =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.getAll() }.getOrElse { dao.getAllOnce().map { it.toModel() } }
        } else {
            dao.getAllOnce().map { it.toModel() }
        }

    private suspend fun existsByNameLocally(name: String, excludeId: String? = null): Boolean =
        dao.getAllOnce().any { it.name.equals(name, ignoreCase = true) && it.id != excludeId }

    private suspend fun enqueue(operation: PendingOperationType, targetId: String?, payload: Accessory?) {
        pendingOperationDao.insert(
            PendingOperationEntity(
                entityType = PendingEntityType.ACCESSORY.name,
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
