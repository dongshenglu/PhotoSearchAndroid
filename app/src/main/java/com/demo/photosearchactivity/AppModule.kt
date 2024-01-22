package com.demo.photosearchactivity

import android.content.Context
import com.demo.photosearchactivity.usecase.FetchPhotoUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun providePhotosRepository(@ApplicationContext context: Context): PhotosRepository {
        return PhotosRepository(context)
    }

    @Singleton
    @Provides
    fun provideFetchPhotoUseCase(photosRepository: PhotosRepository): FetchPhotoUseCase {
        return FetchPhotoUseCase(photosRepository)
    }
}