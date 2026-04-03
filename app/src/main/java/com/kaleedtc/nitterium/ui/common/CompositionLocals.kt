package com.kaleedtc.nitterium.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.MutableState

val LocalFullScreenMode = compositionLocalOf<MutableState<Boolean>> { error("No FullScreenMode provided") }
