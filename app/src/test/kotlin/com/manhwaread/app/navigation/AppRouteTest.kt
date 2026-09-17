package com.manhwaread.app.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppRouteTest {
    @Test
    fun `routes are unique`() {
        val routes = AppRoute.entries.map { it.route }
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun `start route is library and present in entries`() {
        assertEquals(AppRoute.LIBRARY, startRoute)
        assertTrue(AppRoute.entries.contains(startRoute))
    }

    @Test
    fun `routes are lowercase path segments`() {
        AppRoute.entries.forEach { destination ->
            assertTrue(destination.route.isNotEmpty(), "route must not be empty")
            assertTrue(
                destination.route.all { ch -> ch.isLowerCase() || ch.isDigit() },
                "route must be lowercase alphanumeric: ${destination.route}",
            )
        }
    }

    @Test
    fun `labels reference distinct resources`() {
        val labels = AppRoute.entries.map { it.labelRes }
        labels.forEach { label -> assertNotEquals(0, label) }
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun `icons are defined for every route`() {
        AppRoute.entries.forEach { destination ->
            assertTrue(destination.icon.name.isNotEmpty(), "icon must be set: ${destination.name}")
        }
    }
}
