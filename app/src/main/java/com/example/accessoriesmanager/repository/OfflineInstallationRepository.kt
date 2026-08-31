package com.example.accessoriesmanager.repository

import android.net.Uri
import com.example.accessoriesmanager.connectivity.ConnectivityObserver
import com.example.accessoriesmanager.di.ApplicationScope
import com.example.accessoriesmanager.local.dao.InstallationDao
import com.example.accessoriesmanager.local.dao.PendingOperationDao
import com.example.accessoriesmanager.local.entity.PendingEntityType
import com.example.accessoriesmanager.local.entity.PendingOperationEntity
import com.example.accessoriesmanager.local.entity.PendingOperationType
import com.example.accessoriesmanager.local.entity.toEntity
import com.example.accessoriesmanager.local.entity.toModel
import com.example.accessoriesmanager.model.Installation
import com.example.accessoriesmanager.sync.MarkPaidPayload
import com.example.accessoriesmanager.sync.SyncManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first, Firestore-when-possible decorator over [FirebaseInstallationRepository] — see
 * [OfflineAccessoryRepository]. Room only ever caches the 10 most recent installations
 * ([InstallationDao.observeRecent10]); Cloudinary photo upload has no offline path and is left
 * to the caller ([com.example.accessoriesmanager.viewmodel.InstallationFormViewModel]) to guard.
 */
@Singleton
class OfflineInstallationRepository @Inject constructor(
    private val firebase: FirebaseInstallationRepository,
    private val dao: InstallationDao,
    private val pendingOperationDao: PendingOperationDao,
    private val connectivityObserver: ConnectivityObserver,
    private val syncManager: SyncManager,
    @ApplicationScope private val appScope: CoroutineScope,
    private val gson: Gson
) : InstallationRepository {

    override fun newInstallationId(): String = firebase.newInstallationId()

    override suspend fun uploadPhotos(installationId: String, uris: List<Uri>): List<String> =
        firebase.uploadPhotos(installationId, uris)

    override fun listenAll(
        onChange: (List<Installation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        // Room is only the source of truth while offline — online, the Firestore listener below
        // drives onChange with the full (unbounded) history, same as before this feature existed.
        val cacheJob: Job = appScope.launch {
            dao.observeRecent10().collect { entities ->
                if (!connectivityObserver.isOnline.value) {
                    onChange(entities.map { it.toModel() })
                }
            }
        }

        val firestoreRegistration: ListenerRegistration? = if (connectivityObserver.isOnline.value) {
            firebase.listenAll(
                onChange = { list ->
                    onChange(list)
                    appScope.launch { dao.replaceAllAndTrim(list.map { it.toEntity() }) }
                },
                onError = onError
            )
        } else null

        return ListenerRegistration {
            cacheJob.cancel()
            firestoreRegistration?.remove()
        }
    }

    override suspend fun getById(id: String): Installation? =
        if (connectivityObserver.isOnline.value) {
            runCatching { firebase.getById(id) }.getOrNull() ?: dao.getById(id)?.toModel()
        } else {
            dao.getById(id)?.toModel()
        }

    override suspend fun create(installation: Installation): String {
        val id = installation.id?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Installation.id debe resolverse con newInstallationId() antes de crear")
        dao.upsertAndTrim(installation.toEntity())

        if (connectivityObserver.isOnline.value && runCatching { firebase.create(installation) }.isSuccess) {
            return id
        }
        enqueue(PendingOperationType.CREATE, targetId = id, payload = installation)
        return id
    }

    override suspend fun update(id: String, installation: Installation) {
        val toSave = installation.copy(id = id)
        dao.upsertAndTrim(toSave.toEntity())

        if (connectivityObserver.isOnline.value && runCatching { firebase.update(id, installation) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.UPDATE, targetId = id, payload = toSave)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)

        if (connectivityObserver.isOnline.value && runCatching { firebase.delete(id) }.isSuccess) {
            return
        }
        enqueue(PendingOperationType.DELETE, targetId = id, payload = null)
    }

    override suspend fun markAllAccessoriesPaidAndUpdateInstallation(installationId: String, paid: Boolean) {
        applyMarkPaidLocally(installationId, paid)

        if (connectivityObserver.isOnline.value) {
            val result = runCatching {
                firebase.markAllAccessoriesPaidAndUpdateInstallation(installationId, paid)
            }
            if (result.isSuccess) return
        }

        pendingOperationDao.insert(
            PendingOperationEntity(
                entityType = PendingEntityType.INSTALLATION.name,
                operationType = PendingOperationType.MARK_PAID.name,
                targetId = installationId,
                payloadJson = gson.toJson(MarkPaidPayload(installationId, paid)),
                createdAt = System.currentTimeMillis(),
                status = "PENDING",
                lastError = null
            )
        )
        syncManager.syncNow()
    }

    /** Mirrors the total/state recomputation in [FirebaseInstallationRepository] so the offline UI is correct immediately. */
    private suspend fun applyMarkPaidLocally(installationId: String, paid: Boolean) {
        val cached = dao.getById(installationId)?.toModel() ?: return
        val increment = cached.increment ?: 0L
        val accessories = cached.accessories.orEmpty()
        val updatedAccessories = accessories.map { it.copy(isPaid = paid) }

        val totalWorked = accessories.sumOf { it.price + increment }
        val totalPaid = if (paid) totalWorked else 0L
        val totalUnpaid = totalWorked - totalPaid
        val newState = when {
            totalWorked <= 0L -> "NO_PAGADO"
            totalPaid <= 0L -> "NO_PAGADO"
            totalUnpaid <= 0L -> "PAGADO"
            else -> "PARCIAL"
        }

        dao.upsertAndTrim(
            cached.copy(
                accessories = updatedAccessories,
                totalWorked = totalWorked,
                totalPaid = totalPaid,
                totalUnpaid = totalUnpaid,
                state = newState
            ).toEntity()
        )
    }

    private suspend fun enqueue(operation: PendingOperationType, targetId: String?, payload: Installation?) {
        pendingOperationDao.insert(
            PendingOperationEntity(
                entityType = PendingEntityType.INSTALLATION.name,
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
