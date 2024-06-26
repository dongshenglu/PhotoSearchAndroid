package com.demo.photosearchactivity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.photosearchactivity.di.IoDispatcher
import com.demo.photosearchactivity.model.PhotoData
import com.demo.photosearchactivity.model.PhotoResponse
import com.demo.photosearchactivity.model.SearchParameters
import com.demo.photosearchactivity.usecase.FetchPhotoUseCase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private const val TAG = "PhotoSearch-MainActivityViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val fetchPhotoUseCase: FetchPhotoUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // A shared flow for fetching photo progress.
    private val _progressFlow = MutableSharedFlow<Float>()
    val progressFlow: SharedFlow<Float> = _progressFlow

    // A state flow for starting photo search.
    private val _searchParameters = MutableStateFlow(SearchParameters("", 0))
    private val searchParameters: StateFlow<SearchParameters> = _searchParameters

    // Property to store the total number of photos in a page.
    private var totalPhotosCount = 0

    // Property to store amount of pages.
    private var totalPages = 0

    lateinit var selectedPhoto: PhotoData

    var searchKeyword by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow<MainActivityUiState>(MainActivityUiState.Loading)
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    // List of photos.
    private val _fetchedPhotos = mutableStateListOf<PhotoData>()
    val fetchedPhotos: SnapshotStateList<PhotoData> = _fetchedPhotos

    // Property to store the search response that is a list of PhotoResponse objects.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResponse = searchParameters.flatMapLatest { (query, page) ->
        flow {
            if (query.isNotEmpty() && page >= 1) {
                val (_, pages, _, _, photo) = fetchPhotoUseCase.fetchPhotos(query, page)
                totalPages = pages
                emit(photo)
            }
        }.flowOn(ioDispatcher)
    }

    // A value for concurrency limit to avoid high resource usage.
    private val concurrencyLimit = 20

    // A flow of type MainActivityUiState, representing the UI state of the MainActivity.
    // The state is maintained in the ViewModel scope and emits a Loading state initially,
    // followed by Success with the list of mapped photos.
    // It transforms the searchResponse by mapping each PhotoResponse to a MapPhoto object,
    // which contains photo ID, latitude, longitude, and a bitmap image.
    // The transformation includes fetching the location and bitmap for each photo in parallel.
    init {
        searchResponse.flatMapConcat { photos: List<PhotoResponse> ->

            // Use channelFlow to have concurrent coroutines.
            channelFlow {
                totalPhotosCount = photos.size
                val photoCounter = AtomicInteger(0)

                // Define a Semaphore with a specific number of permits.
                val semaphore = Semaphore(permits = concurrencyLimit)

                _progressFlow.emit(0f)

                // Launch parallel coroutines for each photo
                photos.forEach { photo ->
                    launch {
                        // Acquire a permit before fetching the photo.
                        semaphore.withPermit {
                            val mapPhoto = fetchPhotoUseCase.fetchPhoto(photo)
                            mapPhoto?.let {

                                fetchedPhotos.add(it)

                                // Increase fetched phone counter.
                                photoCounter.incrementAndGet()
                                if (photoCounter.get() == totalPhotosCount) {
                                    Log.d(
                                        TAG,
                                        "====photo id: ${photo.id}, completed: ${photoCounter.get()} / $totalPhotosCount ======"
                                    )

                                    // Notify photo fetching completely.
                                    send(MainActivityUiState.Loaded)
                                }
                            }

                            val progressValue = photoCounter.get().toFloat() / totalPhotosCount
                            _progressFlow.emit(progressValue)
                        }
                    }
                }
            }
        }
            .onEach { state ->
                _uiState.value = state
            }.launchIn(viewModelScope)
    }

    /**
     * Start a new search or fetch the next page search result.
     */
    fun loadNextPage() {
        val currentParams = searchParameters.value
        _searchParameters.value = currentParams.copy(
            query = currentParams.query,
            page = currentParams.page + 1
        )
    }

    fun clearFetchedPhotos() {
        _fetchedPhotos.clear()
    }

    fun resetUiState() {
        _uiState.value = MainActivityUiState.Loading
    }

    /**
     * Updates the search parameters for fetching photos.
     *
     * This function sets the new search parameters that will be used
     * for searching photos. The updated parameters will be reflected
     * in the `_searchParameters` StateFlow.
     *
     * @param newParameters The new search parameters to set.
     */
    fun setSearchParameters(newParameters: SearchParameters) {
        _searchParameters.value = newParameters
    }

    /**
     * Updates the search keyword for fetching photos.
     *
     * @param query The new search keyword to set.
     */
    fun updateSearchQuery(query: String) {
        searchKeyword = query
    }
}

/**
 * Convert to Monochrome bitmap.
 * This function is executed in a coroutine with Dispatchers.Default for better performance with large bitmaps.
 * It should be called from a coroutine or another suspend function.
 */
suspend fun Bitmap.convertToMonochrome(): Bitmap? = withContext(Dispatchers.Default) {
    try {
        // Create a paint object with a ColorMatrix that desaturates the color (converts to monochrome)
        val paint = Paint().apply {
            val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        // Create a new bitmap of the same size and convert it to monochrome
        val monochromeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(monochromeBitmap).drawBitmap(this@convertToMonochrome, 0f, 0f, paint)

        return@withContext monochromeBitmap
    } catch (e: Exception) {
        Log.e(TAG, "Error converting to monochrome: ${e.message}")
        null
    }
}

/**
 * Get photo label with trained AI model.
 */
suspend fun Bitmap.getImageLabel(): String = withContext(Dispatchers.Default) {
    try {
        val image = InputImage.fromBitmap(this@getImageLabel, 0)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        // Create a CompletableFuture that will be completed when the labeling is done.
        val future = CompletableFuture<String>()

        labeler.process(image)
            .addOnSuccessListener { labels ->
                // Task completed successfully.
                var confidenceMax = 0.0f
                var bestLabel = ""
                for (label in labels) {
                    if (label.confidence > confidenceMax) {
                        confidenceMax = label.confidence
                        bestLabel = label.text
                    }
                }
                future.complete(bestLabel)
            }
            .addOnFailureListener { _ -> future.complete("") }

        // Await the future's completion, which effectively waits for the labeling task to complete.
        return@withContext future.await()

    } catch (e: Exception) {
        Log.e(TAG, "Error in image labeling: ${e.message}")
        ""
    }
}

/**
 * An sealed interface defines possible states for the UI of MainActivity.
 */
sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState
    data object Loaded : MainActivityUiState
}