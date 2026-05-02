package com.medisyncplus.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.medisyncplus.BuildConfig
import com.medisyncplus.ai.LlmApiService
import com.medisyncplus.data.database.MediSyncDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setPrettyPrinting().create()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MediSyncDatabase =
        Room.databaseBuilder(ctx, MediSyncDatabase::class.java, "medisync.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePatientDao(db: MediSyncDatabase) = db.patientDao()
    @Provides fun provideMedicationDao(db: MediSyncDatabase) = db.medicationDao()
    @Provides fun provideMedLogDao(db: MediSyncDatabase) = db.medicationLogDao()
    @Provides fun provideVitalDao(db: MediSyncDatabase) = db.vitalDao()
    @Provides fun provideSymptomDao(db: MediSyncDatabase) = db.symptomReportDao()
    @Provides fun provideAppointmentDao(db: MediSyncDatabase) = db.appointmentDao()
    @Provides fun provideChecklistDao(db: MediSyncDatabase) = db.checklistDao()
    @Provides fun provideHospitalStayDao(db: MediSyncDatabase) = db.hospitalStayDao()
    @Provides fun provideCareTeamReminderDao(db: MediSyncDatabase) = db.careTeamReminderDao()
    @Provides fun provideEmrProposalDao(db: MediSyncDatabase) = db.emrProposalDao()
    @Provides fun provideChatMessageDao(db: MediSyncDatabase) = db.chatMessageDao()
    @Provides fun provideCareTeamDao(db: MediSyncDatabase) = db.careTeamDao()
    @Provides fun provideAgentAuditTrailDao(db: MediSyncDatabase) = db.agentAuditTrailDao()
    @Provides fun provideUserSettingsDao(db: MediSyncDatabase) = db.userSettingsDao()
    @Provides fun provideHospitalInfoDao(db: MediSyncDatabase) = db.hospitalInfoDao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    @Provides
    @Singleton
    fun provideLlmApiService(client: OkHttpClient, gson: Gson): LlmApiService {
        val baseUrl = if (BuildConfig.LLM_BASE_URL.endsWith("/")) {
            BuildConfig.LLM_BASE_URL
        } else {
            "${BuildConfig.LLM_BASE_URL}/"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(LlmApiService::class.java)
    }
}
