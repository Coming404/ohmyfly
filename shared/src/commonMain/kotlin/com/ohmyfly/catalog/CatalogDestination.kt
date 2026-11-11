package com.ohmyfly.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ohmyfly.ui.AppDestination

@Composable
internal fun CatalogDestination(destination: AppDestination, page: Int, revision: Long, clearSelection: () -> Unit) {
    LaunchedEffect(destination) {
        clearSelection()
        appCatalog.show(when (destination) {
            AppDestination.Subscriptions -> "towers"
            AppDestination.NodeLibrary -> "flights"
            else -> null
        })
    }
    LaunchedEffect(page, revision) { clearSelection() }
}
