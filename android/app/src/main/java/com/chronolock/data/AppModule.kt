package com.chronolock.data

import android.content.Context
import androidx.room.Room
import com.chronolock.domain.RuleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): ChronoDatabase =
        Room.databaseBuilder(context, ChronoDatabase::class.java, "chronolock.db")
            .addMigrations(ChronoDatabase.MIGRATION_1_2, ChronoDatabase.MIGRATION_2_3, ChronoDatabase.MIGRATION_3_4,
                ChronoDatabase.MIGRATION_4_5, ChronoDatabase.MIGRATION_5_6, ChronoDatabase.MIGRATION_6_7, ChronoDatabase.MIGRATION_7_8, ChronoDatabase.MIGRATION_8_9).build()
    @Provides fun ruleRepository(repository: LocalRepository): RuleRepository = repository
}
