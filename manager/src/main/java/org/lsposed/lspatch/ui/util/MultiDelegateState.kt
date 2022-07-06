package org.lsposed.lspatch.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

class DelegateState<T>(initial: T, private val sideEffectSetter: (T) -> Unit) {
    private var snapshot by mutableStateOf(initial)

    var value: T
        get() = snapshot
        set(value) {
            snapshot = value
            sideEffectSetter(snapshot)
        }

    operator fun component1(): T = value

    operator fun component2(): (T) -> Unit = { value = it }
}

fun <T> delegateStateOf(initial: T, sideEffectSetter: (T) -> Unit) = DelegateState(initial, sideEffectSetter)

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> DelegateState<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> DelegateState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
    this.value = value
}
