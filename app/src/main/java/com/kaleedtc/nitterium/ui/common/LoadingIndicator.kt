package com.kaleedtc.nitterium.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    isLoading: Boolean
) {
    if (isLoading) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.testTag("LoadingIndicator"),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
