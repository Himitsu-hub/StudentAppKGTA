package ru.alemak.studentapp.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.alemak.studentapp.BuildConfig
import ru.alemak.studentapp.data.local.AppDatabase
import ru.alemak.studentapp.data.local.NewsDao
import ru.alemak.studentapp.data.local.ReminderDao
import ru.alemak.studentapp.data.local.ScheduleDao
import ru.alemak.studentapp.data.local.TeacherDao
import ru.alemak.studentapp.data.remote.ScheduleApi

/**
 * Retries transient network failures (VPN flaps, timeouts, 5xx).
 * Does not retry 4xx client errors.
 */
private class RetryInterceptor(
    private val maxAttempts: Int = 3,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var lastError: IOException? = null
        var lastResponse: Response? = null
        repeat(maxAttempts) { attempt ->
            try {
                lastResponse?.close()
                val response = chain.proceed(chain.request())
                if (response.isSuccessful || response.code in 400..499) {
                    return response
                }
                // 5xx / unexpected — retry
                lastResponse = response
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < maxAttempts - 1) {
                try {
                    Thread.sleep(700L * (attempt + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        lastResponse?.let { return it }
        throw lastError ?: IOException("Network request failed after $maxAttempts attempts")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        // VPN-friendly: slightly longer connect (handshake), still fail over to cache
        // without multi-retry sleeps that feel like the app is frozen.
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RetryInterceptor(maxAttempts = 1))
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideScheduleApi(retrofit: Retrofit): ScheduleApi =
        retrofit.create(ScheduleApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "studentapp.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideTeacherDao(db: AppDatabase): TeacherDao = db.teacherDao()

    @Provides
    fun provideNewsDao(db: AppDatabase): NewsDao = db.newsDao()

    @Provides
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
}
