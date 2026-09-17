package com.manhwaread.source.api

/**
 * Фильтры поиска источника. Immutable: UI изменяет состояние через copy().
 * Иерархия закреплена промтом: Select, Text, CheckBox, Sort, Header, Separator, Group.
 */
sealed interface Filter {
    /** Навигационный заголовок секции фильтров (не интерактивный). */
    data class Header(val title: String) : Filter

    /** Визуальный разделитель между фильтрами. */
    data object Separator : Filter

    /** Текстовое поле свободного ввода. */
    data class Text(val name: String, val value: String = "") : Filter

    /** Флажок (например, включение/исключение жанра). */
    data class CheckBox(val name: String, val checked: Boolean = false) : Filter

    /**
     * Одиночный выбор из списка опций.
     * [selectedIndex] = -1 означает «не задан» (значение по умолчанию у источника).
     */
    data class Select(val name: String, val options: List<String>, val selectedIndex: Int = -1) : Filter

    /**
     * Сортировка выдачи: выбранная опция и направление.
     * [selectedIndex] = -1 означает «сортировка источника по умолчанию».
     */
    data class Sort(
        val name: String,
        val options: List<String>,
        val selectedIndex: Int = -1,
        val ascending: Boolean = true,
    ) : Filter

    /** Группа вложенных фильтров (например, «Жанры»). */
    data class Group(val name: String, val filters: List<Filter>) : Filter
}
