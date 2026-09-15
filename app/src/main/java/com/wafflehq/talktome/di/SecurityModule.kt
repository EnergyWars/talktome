package com.wafflehq.talktome.di

import com.wafflehq.uikit.security.AesGcmBox
import com.wafflehq.uikit.security.AndroidKeystoreAesGcmBox
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    private const val GEMINI_API_KEY_ALIAS = "talktome_gemini_api_key"
    private const val DEVICE_TOKEN_ALIAS = "talktome_device_token"

    @Provides
    @Singleton
    fun provideAesGcmBox(): AesGcmBox = AndroidKeystoreAesGcmBox(alias = GEMINI_API_KEY_ALIAS)

    @Provides
    @Singleton
    @DeviceTokenCipher
    fun provideDeviceTokenCipher(): AesGcmBox = AndroidKeystoreAesGcmBox(alias = DEVICE_TOKEN_ALIAS)
}
