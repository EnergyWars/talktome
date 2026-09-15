package com.wafflehq.talktome.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiApiKeyDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceIdentityDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceTokenCipher
