package de.davscha.erzaehlwerk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.Shelf) { vm.back() }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val s = vm.screen) {
            Screen.Shelf -> ShelfScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            is Screen.BookPage -> BookScreen(vm, s.bookId)
        }
    }
}
