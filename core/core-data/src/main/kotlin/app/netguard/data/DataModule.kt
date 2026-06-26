package app.netguard.data

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DataModule — Hilt module for data layer dependency injection.
 * Repository bindings added as implementations are completed.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule
