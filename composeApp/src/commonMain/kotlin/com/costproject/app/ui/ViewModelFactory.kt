package com.costproject.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.costproject.app.data.container.AppContainer
import com.costproject.app.ui.viewmodel.AuthViewModel
import com.costproject.app.ui.viewmodel.ProjectDetailViewModel
import com.costproject.app.ui.viewmodel.ProjectListViewModel

/**
 * One factory per ViewModel, as in the reference project. The reference builds
 * them from ViewModelProvider.Factory with a JVM Class<T> check; this uses the
 * multiplatform viewModelFactory DSL, which works in commonMain.
 */
object ViewModelFactory {

    fun auth(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { AuthViewModel(container.authRepository) }
    }

    fun projectList(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ProjectListViewModel(container.projectRepository, container.authRepository, container.exportRepository)
        }
    }

    fun projectDetail(container: AppContainer, projectId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { ProjectDetailViewModel(projectId, container.projectRepository, container.applicationScope) }
    }
}
