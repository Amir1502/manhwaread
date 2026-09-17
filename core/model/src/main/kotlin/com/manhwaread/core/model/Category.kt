package com.manhwaread.core.model

/**
 * Категория библиотеки (пользовательская или системная).
 * Системные категории нельзя удалить или переименовать.
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val order: Int = 0,
    val isSystem: Boolean = false,
)
