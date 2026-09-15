package com.wafflehq.talktome.di

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.wafflehq.talktome.data.crypto.E2eIdentity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    private const val KEYSET_NAME = "talktome_e2e_identity_keyset"
    private const val PREFERENCE_FILE_NAME = "talktome_e2e_identity_prefs"
    private const val MASTER_KEY_URI = "android-keystore://talktome_e2e_identity_kek"

    @Provides
    @Singleton
    fun provideE2eIdentity(@ApplicationContext context: Context): E2eIdentity {
        HybridConfig.register()
        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
        return E2eIdentity(keysetManager.keysetHandle)
    }
}
