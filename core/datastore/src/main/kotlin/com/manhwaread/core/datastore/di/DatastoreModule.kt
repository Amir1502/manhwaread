package com.manhwaread.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.manhwaread.core.datastore.ApiKeyStore
import com.manhwaread.core.datastore.DataStoreSettingsStore
import com.manhwaread.core.datastore.SecureApiKeyStore
import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.datastore.createEncryptedPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatastoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(DATASTORE_FILE_NAME) },
        )

    @Provides
    @Singleton
    fun provideSettingsStore(
        dataStore: DataStore<Preferences>,
    ): SettingsStore = DataStoreSettingsStore(dataStore)

    @Provides
    @Singleton
    fun provideApiKeyStore(
        @ApplicationContext context: Context,
    ): ApiKeyStore =
        SecureApiKeyStore(createEncryptedPrefs(context))

    private const val DATASTORE_FILE_NAME = "manhwaread_settings"
}
