package com.manhwaread.core.designsystem

import androidx.compose.ui.unit.dp
import com.manhwaread.core.designsystem.component.statusTextRes
import com.manhwaread.core.model.ReadingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ShapeAndTokenTest {
    @Test
    fun `corner radii follow Mangalib specifications`() {
        assertEquals(6.dp, ManhwareadRadius.Badge)
        assertEquals(6.dp, ManhwareadRadius.Chip)
        assertEquals(8.dp, ManhwareadRadius.Button)
        assertEquals(8.dp, ManhwareadRadius.Input)
        assertEquals(12.dp, ManhwareadRadius.Card)
        assertEquals(12.dp, ManhwareadRadius.Dialog)
        assertEquals(16.dp, ManhwareadRadius.BottomSheet)
    }

    @Test
    fun `all reading statuses have unique string resources`() {
        val resourceIds = ReadingStatus.entries.map { status -> statusTextRes(status) }.toSet()
        assertEquals(ReadingStatus.entries.size, resourceIds.size)
    }

    @Test
    fun `manga type tokens are defined and distinct`() {
        assertNotEquals(ManhwareadPalette.TypeManga, ManhwareadPalette.TypeManhwa)
        assertNotEquals(ManhwareadPalette.TypeManhua, ManhwareadPalette.TypeManhwa)
        assertNotEquals(ManhwareadPalette.TypeOther, ManhwareadPalette.TypeManhwa)
    }

    @Test
    fun `status tokens are defined and distinct`() {
        val statusColors = setOf(
            ManhwareadPalette.StatusReading,
            ManhwareadPalette.StatusPlanned,
            ManhwareadPalette.StatusCompleted,
            ManhwareadPalette.StatusDropped,
            ManhwareadPalette.StatusOnHold,
        )
        assertEquals(5, statusColors.size)
    }
}
