package com.manhwaread.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manhwaread.core.designsystem.ManhwareadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Панели читалки на Robolectric (JVM, без эмулятора): доступность кнопок глав,
// тумблер «Оригинал | Перевод», слайдер, закладка, оглавление и инфо-полоса.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReaderChromeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun barState(
        currentPage: Int = 0,
        pageCount: Int = 86,
        showOverlay: Boolean = true,
        hasOverlays: Boolean = true,
        hasPreviousChapter: Boolean = true,
        hasNextChapter: Boolean = true,
    ) = ReaderBottomBarState(
        currentPage = currentPage,
        pageCount = pageCount,
        mode = ReaderMode.WEBTOON,
        showOverlay = showOverlay,
        hasOverlays = hasOverlays,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
        hasToc = true,
    )

    private fun actions(
        onShowOverlay: (Boolean) -> Unit = {},
        onNextChapter: () -> Unit = {},
        onOpenToc: () -> Unit = {},
    ) = ReaderBottomBarActions(
        onPageSelected = {},
        onSelectMode = {},
        onShowOverlay = onShowOverlay,
        onNextChapter = onNextChapter,
        onOpenToc = onOpenToc,
    )

    private fun setBottomBar(state: ReaderBottomBarState, actions: ReaderBottomBarActions = actions()) {
        composeRule.setContent {
            ManhwareadTheme { ReaderBottomBar(state = state, actions = actions) }
        }
    }

    @Test
    fun `translation toggle is disabled without overlay specs`() {
        setBottomBar(barState(hasOverlays = false))

        composeRule.onNodeWithText("Original").assertIsNotEnabled().assertIsSelected()
        composeRule.onNodeWithText("Translation").assertIsNotEnabled().assertIsNotSelected()
    }

    @Test
    fun `translation toggle reports explicit choice`() {
        var shown: Boolean? = null
        setBottomBar(barState(showOverlay = true), actions(onShowOverlay = { show -> shown = show }))

        composeRule.onNodeWithText("Translation").assertIsSelected()
        composeRule.onNodeWithText("Original").assertIsEnabled().performClick()
        assertEquals(false, shown)
    }

    @Test
    fun `slider row shows current page and total`() {
        setBottomBar(barState(currentPage = 11, pageCount = 86))

        composeRule.onNodeWithContentDescription("Page navigation slider").assertExists()
        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithText("86").assertExists()
    }

    @Test
    fun `single page shows counter instead of slider`() {
        setBottomBar(barState(pageCount = 1))

        composeRule.onNodeWithText("1 / 1").assertExists()
        composeRule.onNodeWithContentDescription("Page navigation slider").assertDoesNotExist()
    }

    @Test
    fun `chapter buttons follow neighbours and toc opens`() {
        var nextClicks = 0
        var tocClicks = 0
        setBottomBar(
            barState(hasPreviousChapter = false, hasNextChapter = true),
            actions(onNextChapter = { nextClicks++ }, onOpenToc = { tocClicks++ }),
        )

        composeRule.onNodeWithContentDescription("Previous chapter").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next chapter").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Chapters").performClick()
        assertEquals(1, nextClicks)
        assertEquals(1, tocClicks)
    }

    @Test
    fun `top bar shows subtitle and follows bookmark state`() {
        var bookmarked by mutableStateOf(false)
        composeRule.setContent {
            ManhwareadTheme {
                ReaderTopBar(
                    title = "Король меча",
                    subtitle = "Том 7 · Глава 302",
                    isBookmarked = bookmarked,
                    onBack = {},
                    onToggleBookmark = { bookmarked = !bookmarked },
                )
            }
        }

        composeRule.onNodeWithText("Том 7 · Глава 302").assertExists()
        composeRule.onNodeWithContentDescription("Bookmark this page").performClick()
        composeRule.onNodeWithContentDescription("Remove bookmark").assertExists()
    }

    @Test
    fun `top bar without host has no bookmark button`() {
        composeRule.setContent {
            ManhwareadTheme {
                ReaderTopBar(title = "Глава 1", subtitle = null, isBookmarked = false, onBack = {}, onToggleBookmark = null)
            }
        }

        composeRule.onNodeWithText("Глава 1").assertExists()
        composeRule.onNodeWithContentDescription("Bookmark this page").assertDoesNotExist()
    }

    @Test
    fun `toc lists chapters newest first and reports selection`() {
        var selected: Long? = null
        composeRule.setContent {
            ManhwareadTheme {
                TocList(
                    chapters = (1L..5L).map { id -> ReaderTocItem(chapterId = id, title = "Глава $id", isRead = id < 3L) },
                    currentChapterId = 3L,
                    onSelectChapter = { chapterId -> selected = chapterId },
                )
            }
        }

        composeRule.onNodeWithText("Глава 3").assertIsSelected()
        composeRule.onNodeWithText("Глава 2").assertIsNotSelected()
        val newestTop = composeRule.onNodeWithText("Глава 5").fetchSemanticsNode().boundsInRoot.top
        val oldestTop = composeRule.onNodeWithText("Глава 1").fetchSemanticsNode().boundsInRoot.top
        assertTrue(newestTop < oldestTop)
        composeRule.onNodeWithText("Глава 4").performClick()
        assertEquals(4L, selected)
    }

    @Test
    fun `info strip renders status text`() {
        composeRule.setContent {
            ManhwareadTheme { ReaderInfoStrip(text = "12 / 86 · 74% · 20:41") }
        }

        composeRule.onNodeWithText("12 / 86 · 74% · 20:41").assertExists()
    }
}
