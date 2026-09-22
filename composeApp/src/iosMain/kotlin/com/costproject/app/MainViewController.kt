package com.costproject.app

import androidx.compose.ui.window.ComposeUIViewController
import com.costproject.app.data.container.AppContainer

/** The one AppContainer for the process, created on first use. */
private val container: AppContainer by lazy { AppContainer() }

fun MainViewController() = ComposeUIViewController { App(container) }
