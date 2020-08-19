/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.runtime

// TODO(aelias): Mark the typealiases internal when https://youtrack.jetbrains.com/issue/KT-36695 is fixed.
// Currently, they behave as internal because the actual is internal, even though the expect is public.

expect class BitSet() {
    fun set(bitIndex: Int)
    fun or(set: BitSet)
    fun clear(bitIndex: Int)
    operator fun get(bitIndex: Int): Boolean
}

internal expect open class ThreadLocal<T>(initialValue: () -> T) {
    fun get(): T
    fun set(value: T)
}

internal fun <T> ThreadLocal() = ThreadLocal<T?> { null }

expect class WeakHashMap<K, V>() : MutableMap<K, V>

internal expect fun identityHashCode(instance: Any?): Int

internal expect inline fun <R> synchronized(lock: Any, block: () -> R): R

expect open class WeakReference<T> : Reference<T> {
    constructor(referent: T)
    constructor(referent: T, q: ReferenceQueue<in T>?)
}

expect abstract class Reference<T> {
    open fun get(): T?
}

expect open class ReferenceQueue<T>() {
    open fun poll(): Reference<out T>?
}

expect class AtomicReference<V>(value: V) {
    fun get(): V
    fun set(value: V)
    fun getAndSet(value: V): V
    fun compareAndSet(expect: V, newValue: V): Boolean
}

internal expect class BuildableMapBuilder<K, V> : MutableMap<K, V> {
    fun build(): BuildableMap<K, V>
}

expect class BuildableMap<K, V> : Map<K, V> {
    internal fun builder(): BuildableMapBuilder<K, V>
}

@Suppress("UNCHECKED_CAST")
internal inline fun <K, V> BuildableMap<K, V>.mutate(
    mutator: (MutableMap<K, V>) -> Unit
): BuildableMap<K, V> = builder().apply(mutator).build()

internal expect fun <K, V> buildableMapOf(): BuildableMap<K, V>

internal expect class BuildableListBuilder<T> : MutableList<T> {
    fun build(): BuildableList<T>
}

internal expect class BuildableList<T> : List<T> {
    internal fun builder(): BuildableListBuilder<T>
    internal fun add(element: T): BuildableList<T>
    internal fun add(index: Int, element: T): BuildableList<T>
    internal fun addAll(elements: Collection<T>): BuildableList<T>
    internal fun remove(element: T): BuildableList<T>
    internal fun removeAll(elements: Collection<T>): BuildableList<T>
    internal fun removeAt(index: Int): BuildableList<T>
    internal fun set(index: Int, element: T): BuildableList<T>
}

internal expect fun <T> buildableListOf(): BuildableList<T>

internal inline fun <T> BuildableList<T>.mutate(
    mutator: (MutableList<T>) -> Unit
): BuildableList<T> = builder().apply(mutator).build()

expect class UnsupportedOperationException

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR
)
expect annotation class MainThread()

@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
expect annotation class TestOnly()

@Target(AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
expect annotation class CheckResult(
    val suggest: String
)

// Array<T>.sortWith(comparator, fromIndex, toIndex) is only available on jvm and native
internal expect fun <T> sortArrayWith(
    array: Array<T>,
    comparator: Comparator<T>,
    fromIndex: Int,
    toIndex: Int
)