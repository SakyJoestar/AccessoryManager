package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.connectivity.ConnectivityObserver
import com.example.accessoriesmanager.di.ApplicationScope
import com.example.accessoriesmanager.local.dao.PendingOperationDao
import com.example.accessoriesmanager.local.dao.VehicleDao
import com.example.accessoriesmanager.local.entity.PendingEntityType
import com.example.accessoriesmanager.local.entity.PendingOperationEntity
import com.example.accessoriesmanager.local.entity.PendingOperationType
import com.example.accessoriesmanager.local.entity.toEntity
import com.example.accessoriesmanager.local.entity.toModel
import com.example.accessoriesmanager.model.Vehicle
import com.example.accessoriesmanager.sync.SyncManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Room-first, Firestore-when-possible decorator over [FirebaseVehicleRepository] — see [OfflineAccessoryRepository]. */
@Singleton
class OfflineVehicleRepository @Inject constructor(
    private val firebase: FirebaseVehicleRepository,
    private val dao: VehicleDao,
    private val pendingOperationDao: PendingOperationDao,
    private val connectivityObserver: ConnectivityObserver,
    private val syncManager: SyncManager,
    @ApplicationScope private val appScope: CoroutineScope,
    private val gson: Gson
) : VehicleRepository {

    override fun listenVehicles(
        onChange: (List<Vehicle>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val cacheJob: Job = appScope.launch {
            dao.observeAll().collect { entities -> onChange(entities.map { it.toModel() }) }
        }

        val firestoreRegistration: ListenerRegistration? = if (connectivityObserver.isOnline.value) {
            firebase.listenVehicles(
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

    override suspend fun add(vehicle: Vehicle): String {
        val localId = "local_" + UUID.randomUUID()
        val toSave = vehicle.copy(id = localId)
        dao.upsert(toSave.toEntity())

        if (connectivityObserver.isOnline.value) {
            val realId = runCatching { firebase.add(vehicle) }.getOrNull()
            if (realId != null) {
                dao.delete(localId)
                dao.upsert(vehicle.copy(id = realId).toEntity())
                return realId
            }
        }

        enqueue(PendingOperationType.CREATE, targetId = null, payload = toSave)
        return localId
    }

    override suspend fun update(id: String, vehicle: Vehicle) {
        val toSave = vehicle.copy(id = id)
        dao.upsert(toSave.toEntity())

        if (connectivityObserver.isOnline.value && runCatching { firebase.update(id, vehicle) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.UPDATE, targetId = id, payload = toSave)
    }

    override suspend fun deleteVehicle(id: String) {
        dao.delete(id)

        if (connectivityObserver.isOnline.value && runCatching { firebase.deleteVehicle(id) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.DELETE, targetId = id, payload = null)
    }

    override suspend fun getById(id: String): Vehicle? =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.getById(id) }.getOrNull() ?: dao.getById(id)?.toModel()
        } else {
            dao.getById(id)?.toModel()
        }

    override suspend fun existsByMakeAndModel(make: String, model: String): Boolean =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.existsByMakeAndModel(make, model) }.getOrElse { existsLocally(make, model) }
        } else {
            existsLocally(make, model)
        }

    override suspend fun existsByMakeAndModelExcludingId(make: String, model: String, excludeId: String): Boolean =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.existsByMakeAndModelExcludingId(make, model, excludeId) }
                .getOrElse { existsLocally(make, model, excludeId) }
        } else {
            existsLocally(make, model, excludeId)
        }

    override suspend fun getAll(): List<Vehicle> =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.getAll() }.getOrElse { dao.getAllOnce().map { it.toModel() } }
        } else {
            dao.getAllOnce().map { it.toModel() }
        }

    private suspend fun existsLocally(make: String, model: String, excludeId: String? = null): Boolean =
        dao.getAllOnce().any {
            it.make.equals(make, ignoreCase = true) && it.model.equals(model, ignoreCase = true) && it.id != excludeId
        }

    private suspend fun enqueue(operation: PendingOperationType, targetId: String?, payload: Vehicle?) {
        pendingOperationDao.insert(
            PendingOperationEntity(
                entityType = PendingEntityType.VEHICLE.name,
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
