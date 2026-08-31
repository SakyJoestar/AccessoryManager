package com.example.accessoriesmanager.sync

import com.example.accessoriesmanager.connectivity.ConnectivityObserver
import com.example.accessoriesmanager.di.ApplicationScope
import com.example.accessoriesmanager.local.dao.AccessoryDao
import com.example.accessoriesmanager.local.dao.HeadquarterDao
import com.example.accessoriesmanager.local.dao.InstallationDao
import com.example.accessoriesmanager.local.dao.PendingOperationDao
import com.example.accessoriesmanager.local.dao.VehicleDao
import com.example.accessoriesmanager.local.entity.PendingEntityType
import com.example.accessoriesmanager.local.entity.PendingOperationEntity
import com.example.accessoriesmanager.local.entity.PendingOperationType
import com.example.accessoriesmanager.local.entity.toEntity
import com.example.accessoriesmanager.model.Accessory
import com.example.accessoriesmanager.model.Headquarter
import com.example.accessoriesmanager.model.Installation
import com.example.accessoriesmanager.model.Vehicle
import com.example.accessoriesmanager.repository.FirebaseAccessoryRepository
import com.example.accessoriesmanager.repository.FirebaseHeadquarterRepository
import com.example.accessoriesmanager.repository.FirebaseInstallationRepository
import com.example.accessoriesmanager.repository.FirebaseVehicleRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Payload for the [PendingOperationType.MARK_PAID] queue entry. */
data class MarkPaidPayload(val installationId: String, val paid: Boolean)

/**
 * Drains the offline write queue ([PendingOperationDao]) against real Firestore repositories
 * as soon as [ConnectivityObserver] reports the device is back online.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    private val connectivityObserver: ConnectivityObserver,
    private val pendingOperationDao: PendingOperationDao,
    private val accessoryDao: AccessoryDao,
    private val vehicleDao: VehicleDao,
    private val headquarterDao: HeadquarterDao,
    private val installationDao: InstallationDao,
    private val firebaseAccessoryRepository: FirebaseAccessoryRepository,
    private val firebaseVehicleRepository: FirebaseVehicleRepository,
    private val firebaseHeadquarterRepository: FirebaseHeadquarterRepository,
    private val firebaseInstallationRepository: FirebaseInstallationRepository,
    private val gson: Gson
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Offline)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val mutex = Mutex()

    init {
        appScope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (online) runSync() else _status.value = SyncStatus.Offline
            }
        }
    }

    fun syncNow() {
        appScope.launch { runSync() }
    }

    suspend fun failedOperations(): List<PendingOperationEntity> =
        pendingOperationDao.getAllPending().filter { it.status == "FAILED" }

    private suspend fun runSync() = mutex.withLock {
        if (!connectivityObserver.isOnline.value) {
            _status.value = SyncStatus.Offline
            return
        }

        val pending = pendingOperationDao.getAllPending()
        if (pending.isEmpty()) {
            _status.value = SyncStatus.Synced
            return
        }

        _status.value = SyncStatus.Syncing
        var failures = 0
        for (op in pending) {
            val result = runCatching { applyOperation(op) }
            if (result.isSuccess) {
                pendingOperationDao.delete(op.localId)
            } else {
                failures++
                pendingOperationDao.update(
                    op.copy(
                        status = "FAILED",
                        lastError = result.exceptionOrNull()?.message ?: "Error desconocido",
                        attemptCount = op.attemptCount + 1
                    )
                )
            }
        }
        _status.value = if (failures == 0) SyncStatus.Synced else SyncStatus.Error(failures)
    }

    private suspend fun applyOperation(op: PendingOperationEntity) {
        when (PendingEntityType.valueOf(op.entityType)) {
            PendingEntityType.ACCESSORY -> applyAccessoryOperation(op)
            PendingEntityType.VEHICLE -> applyVehicleOperation(op)
            PendingEntityType.HEADQUARTER -> applyHeadquarterOperation(op)
            PendingEntityType.INSTALLATION -> applyInstallationOperation(op)
        }
    }

    private suspend fun applyAccessoryOperation(op: PendingOperationEntity) {
        when (PendingOperationType.valueOf(op.operationType)) {
            PendingOperationType.CREATE -> {
                val local = gson.fromJson(op.payloadJson, Accessory::class.java)
                val localId = local.id
                val realId = firebaseAccessoryRepository.add(local)
                accessoryDao.delete(localId)
                accessoryDao.upsert(local.copy(id = realId).toEntity())
            }
            PendingOperationType.UPDATE -> {
                val accessory = gson.fromJson(op.payloadJson, Accessory::class.java)
                firebaseAccessoryRepository.update(op.targetId!!, accessory)
            }
            PendingOperationType.DELETE -> firebaseAccessoryRepository.deleteAccessory(op.targetId!!)
            PendingOperationType.MARK_PAID -> Unit
        }
    }

    private suspend fun applyVehicleOperation(op: PendingOperationEntity) {
        when (PendingOperationType.valueOf(op.operationType)) {
            PendingOperationType.CREATE -> {
                val local = gson.fromJson(op.payloadJson, Vehicle::class.java)
                val localId = local.id.orEmpty()
                val realId = firebaseVehicleRepository.add(local)
                vehicleDao.delete(localId)
                vehicleDao.upsert(local.copy(id = realId).toEntity())
            }
            PendingOperationType.UPDATE -> {
                val vehicle = gson.fromJson(op.payloadJson, Vehicle::class.java)
                firebaseVehicleRepository.update(op.targetId!!, vehicle)
            }
            PendingOperationType.DELETE -> firebaseVehicleRepository.deleteVehicle(op.targetId!!)
            PendingOperationType.MARK_PAID -> Unit
        }
    }

    private suspend fun applyHeadquarterOperation(op: PendingOperationEntity) {
        when (PendingOperationType.valueOf(op.operationType)) {
            PendingOperationType.CREATE -> {
                val local = gson.fromJson(op.payloadJson, Headquarter::class.java)
                val localId = local.id.orEmpty()
                val realId = firebaseHeadquarterRepository.add(local)
                headquarterDao.delete(localId)
                headquarterDao.upsert(local.copy(id = realId).toEntity())
            }
            PendingOperationType.UPDATE -> {
                val headquarter = gson.fromJson(op.payloadJson, Headquarter::class.java)
                firebaseHeadquarterRepository.update(op.targetId!!, headquarter)
            }
            PendingOperationType.DELETE -> firebaseHeadquarterRepository.deleteHeadquarter(op.targetId!!)
            PendingOperationType.MARK_PAID -> Unit
        }
    }

    private suspend fun applyInstallationOperation(op: PendingOperationEntity) {
        when (PendingOperationType.valueOf(op.operationType)) {
            PendingOperationType.CREATE -> {
                val installation = gson.fromJson(op.payloadJson, Installation::class.java)
                firebaseInstallationRepository.create(installation)
            }
            PendingOperationType.UPDATE -> {
                val installation = gson.fromJson(op.payloadJson, Installation::class.java)
                firebaseInstallationRepository.update(op.targetId!!, installation)
            }
            PendingOperationType.DELETE -> {
                firebaseInstallationRepository.delete(op.targetId!!)
                installationDao.delete(op.targetId)
            }
            PendingOperationType.MARK_PAID -> {
                val payload = gson.fromJson(op.payloadJson, MarkPaidPayload::class.java)
                firebaseInstallationRepository.markAllAccessoriesPaidAndUpdateInstallation(
                    payload.installationId,
                    payload.paid
                )
            }
        }
    }
}
