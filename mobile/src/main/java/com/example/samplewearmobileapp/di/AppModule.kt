package com.example.samplewearmobileapp.di

import android.content.Context
import android.content.SharedPreferences
import com.example.samplewearmobileapp.permissions.PermissionManager
import com.example.samplewearmobileapp.sensor.PolarEcgManager
import com.example.samplewearmobileapp.sensor.PolarEcgManagerImpl
import com.example.samplewearmobileapp.sensor.WearPpgManager
import com.example.samplewearmobileapp.sensor.WearPpgManagerImpl
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Hilt module providing application-wide singletons.
 *
 * Provides sensor managers, data utilities, and coroutine dispatchers
 * to the ViewModel and Service layers.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("heart_monitor_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun providePermissionManager(): PermissionManager = PermissionManager()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    // === Sensor Managers ===

    @Provides
    @Singleton
    fun providePolarEcgManager(
        @ApplicationContext context: Context
    ): PolarEcgManager {
        return PolarEcgManagerImpl(context)
    }

    @Provides
    @Singleton
    fun provideWearPpgManager(
        @ApplicationContext context: Context,
        gson: Gson
    ): WearPpgManager {
        return WearPpgManagerImpl(context, gson)
    }
}
