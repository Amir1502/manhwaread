package com.manhwaread.source.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Реестр источников: хранит подключённые [Source] и реактивно публикует их список.
 */
interface SourceRegistry {
    /** Актуальный список источников в порядке регистрации. */
    val sources: StateFlow<List<Source>>

    /**
     * Регистрирует источник.
     * @return false, если источник с таким [Source.id] уже зарегистрирован (дубль отвергается).
     */
    fun register(source: Source): Boolean

    /**
     * Удаляет источник по id.
     * @return false, если источника с таким id не было.
     */
    fun unregister(sourceId: Long): Boolean

    /** Возвращает источник по id или null, если он не зарегистрирован. */
    fun get(sourceId: Long): Source?
}

/**
 * Реестр в памяти: LinkedHashMap под монитором, снимки публикует [MutableStateFlow].
 * Порядок регистрации сохраняется; все операции потокобезопасны.
 */
class InMemorySourceRegistry : SourceRegistry {
    private val lock = Any()
    private val byId = LinkedHashMap<Long, Source>()
    private val _sources = MutableStateFlow<List<Source>>(emptyList())

    override val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    override fun register(source: Source): Boolean = synchronized(lock) {
        if (byId.containsKey(source.id)) {
            false
        } else {
            byId[source.id] = source
            publishSnapshot()
            true
        }
    }

    override fun unregister(sourceId: Long): Boolean = synchronized(lock) {
        val removed = byId.remove(sourceId)
        if (removed != null) {
            publishSnapshot()
            true
        } else {
            false
        }
    }

    override fun get(sourceId: Long): Source? = synchronized(lock) { byId[sourceId] }

    // Вызывается только под монитором lock.
    private fun publishSnapshot() {
        _sources.value = byId.values.toList()
    }
}
