package com.example.accessoriesmanager.sync

sealed class SyncStatus {
    data object Offline : SyncStatus()
    data object Synced : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val failedCount: Int) : SyncStatus()
}
