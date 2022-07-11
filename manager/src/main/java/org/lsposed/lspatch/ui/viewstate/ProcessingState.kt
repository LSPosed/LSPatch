package org.lsposed.lspatch.ui.viewstate

sealed class ProcessingState<out T> {
    object Idle : ProcessingState<Nothing>()
    object Processing : ProcessingState<Nothing>()
    data class Done<T>(val result: T) : ProcessingState<T>()
}
