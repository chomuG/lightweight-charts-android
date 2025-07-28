package com.tradingview.lightweightcharts.example.app.di

import android.content.Context
import com.tradingview.lightweightcharts.example.app.api.KisApiService
// import com.tradingview.lightweightcharts.example.app.database.ChartDatabase
// import com.tradingview.lightweightcharts.example.app.database.ChartDataDao
import com.google.gson.GsonBuilder
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
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl("https://openapi.koreainvestment.com:9443/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideKisApiService(retrofit: Retrofit): KisApiService {
        return retrofit.create(KisApiService::class.java)
    }

    // Room 데이터베이스는 나중에 구현 예정
    // @Provides
    // @Singleton
    // fun provideChartDatabase(@ApplicationContext context: Context): ChartDatabase {
    //     return ChartDatabase.getDatabase(context)
    // }

    // @Provides
    // fun provideChartDataDao(database: ChartDatabase): ChartDataDao {
    //     return database.chartDataDao()
    // }
}