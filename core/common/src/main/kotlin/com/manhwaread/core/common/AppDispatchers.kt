package com.manhwaread.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * Абстракция диспетчеров корутин: продакшен-реализация и тестовая
 * (единый [TestDispatcher] для управления виртуальным временем).
 */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

/** Продакшен-реализация поверх стандартных [Dispatchers]. */
class DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}

/**
 * Тестовая реализация: все диспетчеры — один [TestDispatcher]
 * (по умолчанию [StandardTestDispatcher]).
 */
class TestAppDispatchers(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : AppDispatchers {
    override val main: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}
