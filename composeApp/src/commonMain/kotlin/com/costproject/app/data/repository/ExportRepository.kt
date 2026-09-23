package com.costproject.app.data.repository

import com.costproject.app.data.DataError
import com.costproject.app.data.remote.ExportApiService
import com.costproject.app.data.remote.safeApiCall
import com.costproject.app.domain.model.ExportFile

/**
 * Excel backup downloads. Every failure is a [DataError]. An interface so the
 * list ViewModel can be tested against a fake.
 */
interface ExportRepository {
    /** Whether the signed-in user may export every user's data. */
    suspend fun isAdmin(): Result<Boolean>

    /** The signed-in user's own projects. */
    suspend fun exportMine(): Result<ExportFile>

    /** Every user's projects. Fails with [DataError.Forbidden] for non-admins. */
    suspend fun exportAll(): Result<ExportFile>
}

class DefaultExportRepository(private val api: ExportApiService) : ExportRepository {
    override suspend fun isAdmin(): Result<Boolean> = safeApiCall { api.me().isAdmin }
    override suspend fun exportMine(): Result<ExportFile> = safeApiCall { api.downloadMine() }
    override suspend fun exportAll(): Result<ExportFile> = safeApiCall { api.downloadAll() }
}
