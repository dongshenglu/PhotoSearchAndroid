package com.demo.photosearchactivity.di

import android.content.Context
import com.demo.photosearchactivity.PhotosRepository
import com.demo.photosearchactivity.usecase.FetchPhotoUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun providePhotosRepository(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): PhotosRepository {
        return PhotosRepository(context, ioDispatcher, defaultDispatcher)
    }

    @Singleton
    @Provides
    fun provideFetchPhotoUseCase(photosRepository: PhotosRepository): FetchPhotoUseCase {
        return FetchPhotoUseCase(photosRepository)
    }
}