package io.github.eurya.awt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.eurya.awt.manager.RuntimeLibraryManager
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRuntimeLibraryManager(
        @ApplicationContext context: Context
    ): RuntimeLibraryManager {
        return RuntimeLibraryManager(context)
    }
}