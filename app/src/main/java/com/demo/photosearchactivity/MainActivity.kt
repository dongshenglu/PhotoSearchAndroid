package com.demo.photosearchactivity

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.demo.photosearchactivity.model.PhotoData
import com.demo.photosearchactivity.model.SearchParameters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Main activity view model for fetching photos.
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "photoGrid") {
                composable("photoGrid") {
                    PhotoGridScreen(navController)
                }
                composable(
                    "photoDetails/{photoId}",
                    arguments = listOf(navArgument("photoId") { type = NavType.StringType })
                ) { backStackEntry ->
                    PhotoDetailsScreen(
                        navController,
                        photoId = backStackEntry.arguments?.getString("photoId") ?: ""
                    )
                }
            }
        }
    }

    @Composable
    fun PhotoGridScreen(navController: NavController) {

        // State list to hold Bitmaps
        val fetchedPhotos = remember { viewModel.fetchedPhotos }

        LaunchedEffect(viewModel.uiState) {
            Log.d(TAG, "=====LaunchedEffect started with value: $viewModel.uiState.")
            if (viewModel.searchKeyword.isNotEmpty()) {
                Log.d(TAG, "=====initialSearch started with value: $viewModel.uiState.")
                initialSearch()
                startNewSearch(viewModel.searchKeyword)
            }
        }

        DisposableEffect(navController) {
            val callback =
                NavController.OnDestinationChangedListener { _, destination, _ ->
                    if (destination.route == "photoGrid") {
                        Log.d(TAG, "Navigated back to Photo Grid Screen.")
                    }
                }
            navController.addOnDestinationChangedListener(callback)
            onDispose {
                navController.removeOnDestinationChangedListener(callback)
            }
        }

        Column(modifier = Modifier.padding(top = 8.dp)) {
            SearchToolbar()

            // Collect photo fetching progress value and update indicator.
            val progress by viewModel.progressFlow.collectAsState(initial = 0f)

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .height(10.dp)
                    .fillMaxWidth()
            )

            PhotoDataGrid(
                photoList = fetchedPhotos,
                onItemClick = { photoData ->
                    // Handle the click event here
                    Log.d("PhotoDataGrid", "Clicked on item: ${photoData.id}")
                    viewModel.selectedPhoto = photoData
                    navController.navigate("photoDetails/${photoData.id}")
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchToolbar() {
        val context = LocalContext.current
        val repository = PhotosRepository(context)
        var text by remember { mutableStateOf(viewModel.searchKeyword) }
        var isFocused by remember { mutableStateOf(false) }

        // State to control menu visibility.
        var showMenu by remember { mutableStateOf(false) }

        //  The FocusManager can be used to clear focus, which typically hides the keyboard.
        val focusManager = LocalFocusManager.current

        // Common function for search operation
        fun performSearch() {
            focusManager.clearFocus()
            viewModel.searchKeyword = text
            viewModel.resetUiState()
            startNewSearch(text) // Assuming startNewSearch is your search function
        }

        TopAppBar(
            title = { Text("App Title") }, // Replace with your app title
            actions = {
                Row(
                    modifier = Modifier
                        .height(70.dp)
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                        .border(2.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontSize = 18.sp),
                        label = { if (text.isEmpty() && !isFocused) Text("Enter text") },
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                            },
                        trailingIcon = {
                            IconButton(onClick = { performSearch() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_search),
                                    contentDescription = "Search"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { performSearch() }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp)) // Adding a spacer for better visual spacing

                    IconButton(
                        onClick = {
                            viewModel.loadNextPage()
                        },
                        modifier = Modifier.size(40.dp) // Adjust size as needed
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_next), // Replace with your image resource
                            contentDescription = "Next Page"
                        )
                    }

                    // "Box" ensures that the DropdownMenu aligns with the 3-dot IconButton.
                    Box(
                        contentAlignment = Alignment.TopEnd // Aligns the dropdown to the end of the box
                    ) {
                        // 3-dot menu button
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More Options")
                        }

                        // Dropdown menu
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "Clear cache") },
                                onClick = { repository.clearPhotoCache() }
                            )
                        }
                    }
                }
            }
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        SearchToolbar()
    }

    @Composable
    fun PhotoDataGrid(
        photoList: List<PhotoData>,
        modifier: Modifier = Modifier,
        onItemClick: (PhotoData) -> Unit
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            modifier = modifier
        ) {
            items(photoList) { photoData ->
                PhotoDataCard(
                    photoData = photoData,
                    modifier = Modifier.padding(8.dp),
                    onClick = { onItemClick(photoData) }
                )
            }
        }
    }

    @Composable
    fun PhotoDataCard(photoData: PhotoData, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Card(modifier = modifier.clickable(onClick = onClick)) {
            Column {
                Image(
                    bitmap = photoData.bitmap.asImageBitmap(),
                    contentDescription = photoData.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
//                        .height(100.dp),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }

    @Preview
    @Composable
    private fun PhotoDataCardPreview() {
        // Create a dummy bitmap for the preview
        val dummyBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            // Fill the Bitmap with a color, e.g., red
            eraseColor(android.graphics.Color.RED)
        }

        // Create a dummy PhotoData object
        val dummyPhotoData = PhotoData(
            id = "photoId",
            title = "Photo Title",
            latitude = -37.814223,
            longitude = 144.967936,
            bitmap = dummyBitmap
        )

        // Use the dummy data for the preview
        PhotoDataCard(photoData = dummyPhotoData, onClick = {})
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PhotoDetailsScreen(navController: NavController, photoId: String) {
        val (id, title, latitude, longitude, originalBitmap) = viewModel.selectedPhoto
        var showMenu by remember { mutableStateOf(false) } // State to control menu visibility
        // State to control the visibility of the menu item.
        var showMenuItem by remember { mutableStateOf(true) }
        var bitmapState by remember { mutableStateOf(originalBitmap) } // State to hold the current bitmap
        var showDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        if (id == photoId) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(context.getString(R.string.image_details)) },
                        navigationIcon = {
                            IconButton(onClick = {
                                navController.navigateUp()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = context.getString(R.string.navigation_back)
                                )
                            }
                        },
                        actions = {

                            // "Box" ensures that the DropdownMenu aligns with the 3-dot IconButton.
                            Box(
                                contentAlignment = Alignment.TopEnd // Aligns the dropdown to the end of the box
                            ) {
                                // 3-dot menu button
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert, contentDescription =
                                        context.getString(R.string.more_options)
                                    )
                                }

                                // Dropdown menu
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    if (showMenuItem) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = context.getString(
                                                        R.string.menu_action_convert_monochrome
                                                    )
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                lifecycleScope.launch {
                                                    val convertedBitmap =
                                                        originalBitmap.convertToMonochrome()
                                                    if (convertedBitmap != null) {
                                                        bitmapState = convertedBitmap
                                                        showMenuItem = false
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text(text = context.getString(R.string.menu_action_save)) },
                                        onClick = {
                                            showMenu = false
                                            showDialog = true
                                        }
                                    )
                                }
                            }
                        })
                },
            )
            { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Display the image here.
                    Image(
                        bitmap = bitmapState.asImageBitmap(), contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                    Text(
                        text =
                        """
                            |Latitude: $latitude,
                            |Longitude: $longitude
                            |
                            |Photo description:
                            |
                        """.trimMargin(),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }

        if (showDialog) {
            SaveImageDialog(bitmap = bitmapState) {
                showDialog = false // This will be called to close the dialog
            }
        }
    }

    /**
     * Initial collecting search results.
     */
    private fun initialSearch() {

        // Collect photo search results.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    viewModel.uiState
                        .collect {
                            Log.d(TAG, "state: $it")
                            when (it) {
                                is MainActivityUiState.Loading -> {
                                    Log.i(TAG, "onCreate: Loading...")
                                }

                                is MainActivityUiState.Loaded -> {
                                    Log.i(TAG, "onCreate: Loaded.")
                                }
                            }
                        }
                } catch (e: Exception) {
                    // Catch exception from the collection phase.
                    Log.d(TAG, "Flow collection exception: ${e.message}")
                }
            }
        }
    }

    /**
     * Start a new search from the 1st page.
     *
     * @param keyWord: The search keyword input.
     */
    private fun startNewSearch(keyWord: String) {
        viewModel.searchParameters.value = SearchParameters(keyWord, 0)
        viewModel.clearFetchedPhotos()
        viewModel.loadNextPage()
    }

    @Composable
    fun SaveImageDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
        var fileName by remember { mutableStateOf("") }
        var showDialog by remember { mutableStateOf(true) }
        val repository = PhotosRepository(LocalContext.current)
        val context = LocalContext.current

        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.menu_action_save),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text(stringResource(id = R.string.name_image_file_name_hint)) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(id = android.R.string.cancel))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (fileName.isNotEmpty()) {
                                        // Handle the save operation
                                        showDialog = false
                                        repository.saveImage(bitmap, fileName)
                                    } else {
                                        // Retrieve the string resource
                                        val errorMessage =
                                            context.getString(R.string.name_image_file_name_error)
                                        // Show error toast
                                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                            ) {
                                Text(stringResource(id = R.string.menu_action_save))
                            }
                        }
                    }
                }
            }
        }
    }

}

