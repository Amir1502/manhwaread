package com.manhwaread.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manhwaread.core.database.ManhwareadDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import javax.inject.Inject

// Запуск приложения с Hilt-графом на JVM: проверяются DI, манифест, тема и Compose-контент.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
@HiltAndroidTest
class MainActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: ManhwareadDatabase

    @Test
    fun `activity launches with composed content`() {
        hiltRule.inject()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun `hilt graph provides working database`() {
        hiltRule.inject()
        assertNotNull(database.openHelper.writableDatabase)
    }
}
