package com.example.accessoriesmanager.repository

import android.net.Uri
import com.example.accessoriesmanager.model.Installation
import com.google.firebase.firestore.ListenerRegistration

interface InstallationRepository {
    /** A fresh Firestore-generated id, reserved locally (no network call) so it can be used
     * as the Storage upload path before the installation document itself is written. */
    fun newInstallationId(): String

    /** Uploads each local photo [uris] under the installation's Cloudinary folder and returns their secure URLs. */
    suspend fun uploadPhotos(installationId: String, uris: List<Uri>): List<String>

    suspend fun getById(id: String): Installation?
    suspend fun create(installation: Installation): String
    suspend fun update(id: String, installation: Installation)
    suspend fun delete(id: String)

    fun listenAll(
        onChange: (List<Installation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration

    /** Marks ALL accessories of an installation as paid / not paid. */
    suspend fun markAllAccessoriesPaidAndUpdateInstallation(installationId: String, paid: Boolean)
}
