package com.wafflehq.talktome.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.wafflehq.talktome.data.db.AppDatabase
import com.wafflehq.talktome.data.db.EncryptedAppDatabaseFactory
import com.wafflehq.talktome.data.db.InboxMessageDao
import com.wafflehq.talktome.data.db.MediatorNoteDao
import com.wafflehq.talktome.data.db.NegotiationTurnDao
import com.wafflehq.talktome.data.db.OutgoingMessageDao
import com.wafflehq.talktome.data.db.PartnerDao
import com.wafflehq.talktome.data.db.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        EncryptedAppDatabaseFactory(context).create()

    @Provides
    fun providePartnerDao(database: AppDatabase): PartnerDao = database.partnerDao()

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideMediatorNoteDao(database: AppDatabase): MediatorNoteDao = database.mediatorNoteDao()

    @Provides
    fun provideOutgoingMessageDao(database: AppDatabase): OutgoingMessageDao = database.outgoingMessageDao()

    @Provides
    fun provideInboxMessageDao(database: AppDatabase): InboxMessageDao = database.inboxMessageDao()

    @Provides
    fun provideNegotiationTurnDao(database: AppDatabase): NegotiationTurnDao = database.negotiationTurnDao()

    @Provides
    @Singleton
    @SettingsDataStore
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") },
        )

    @Provides
    @Singleton
    @GeminiApiKeyDataStore
    fun provideGeminiApiKeyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("gemini_api_key") },
        )

    @Provides
    @Singleton
    @DeviceIdentityDataStore
    fun provideDeviceIdentityDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("device_identity") },
        )
}
