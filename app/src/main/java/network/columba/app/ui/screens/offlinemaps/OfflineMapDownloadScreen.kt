package network.columba.app.ui.screens.offlinemaps

import android.Manifest
import android.location.Location
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import network.columba.app.R
import network.columba.app.map.TileDownloadManager
import network.columba.app.util.LocationCompat
import network.columba.app.viewmodel.AddressSearchResult
import network.columba.app.viewmodel.DownloadProgress
import network.columba.app.viewmodel.DownloadWizardStep
import network.columba.app.viewmodel.OfflineMapDownloadViewModel
import network.columba.app.viewmodel.RadiusOption
import java.util.Locale

private const val TAG = "OfflineMapDownload"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapDownloadScreen(
    onNavigateBack: () -> Unit = {},
    onDownloadComplete: () -> Unit = {},
    updateRegionId: Long? = null,
    viewModel: OfflineMapDownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCancelDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Pre-fill wizard when updating an existing region
    LaunchedEffect(updateRegionId) {
        if (updateRegionId != null) {
            viewModel.initForUpdate(updateRegionId)
        }
    }

    // Handle completion — consolidate all post-download notifications
    // into a single Toast so they don't conflict or get lost on navigation.
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            val message =
                when {
                    state.styleCacheWarning != null && state.httpAutoDisabled ->
                        state.styleCacheWarning +
                            " Note: HTTP was auto-disabled and must be re-enabled before retrying."
                    state.styleCacheWarning != null -> state.styleCacheWarning
                    state.httpAutoDisabled -> "HTTP disabled. Your offline maps are ready."
                    else -> null
                }
            message?.let {
                android.widget.Toast
                    .makeText(context, it, android.widget.Toast.LENGTH_LONG)
                    .show()
            }
            onDownloadComplete()
        }
    }

    // Show error in snackbar
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.step) {
                            DownloadWizardStep.LOCATION -> "Select Location"
                            DownloadWizardStep.RADIUS -> "Choose Area"
                            DownloadWizardStep.CONFIRM -> "Confirm Download"
                            DownloadWizardStep.DOWNLOADING -> "Downloading"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.step == DownloadWizardStep.DOWNLOADING) {
                                showCancelDialog = true
                            } else if (state.step == DownloadWizardStep.LOCATION) {
                                onNavigateBack()
                            } else {
                                viewModel.previousStep()
                            }
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (state.step == DownloadWizardStep.DOWNLOADING) {
                                    Icons.Default.Close
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                            contentDescription =
                                if (state.step == DownloadWizardStep.DOWNLOADING) {
                                    "Cancel"
                                } else {
                                    "Back"
                                },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        // The parent Scaffold in MainActivity consumes navigation bar insets
        // but discards its paddingValues, so child Scaffolds see them as consumed.
        // Use the raw (unconsumed) WindowInsets to get the actual nav bar height.
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(bottom = navBarBottom),
        ) {
            when (state.step) {
                DownloadWizardStep.LOCATION ->
                    LocationSelectionStep(
                        hasLocation = state.hasLocation,
                        latitude = state.centerLatitude,
                        longitude = state.centerLongitude,
                        isGeocoderAvailable = state.isGeocoderAvailable,
                        addressQuery = state.addressQuery,
                        addressSearchResults = state.addressSearchResults,
                        isSearchingAddress = state.isSearchingAddress,
                        addressSearchError = state.addressSearchError,
                        httpEnabled = state.httpEnabled,
                        onLocationSet = { lat, lon -> viewModel.setLocation(lat, lon) },
                        onCurrentLocationRequest = { location ->
                            viewModel.setLocationFromCurrent(location)
                        },
                        onAddressQueryChange = { viewModel.setAddressQuery(it) },
                        onSearchAddress = { viewModel.searchAddress() },
                        onSelectAddressResult = { viewModel.selectAddressResult(it) },
                        onEnableHttp = { viewModel.enableHttp() },
                        onNext = { viewModel.nextStep() },
                    )

                DownloadWizardStep.RADIUS ->
                    RadiusSelectionStep(
                        radiusOption = state.radiusOption,
                        minZoom = state.minZoom,
                        maxZoom = state.maxZoom,
                        estimatedTileCount = state.estimatedTileCount,
                        estimatedSize = state.getEstimatedSizeString(),
                        onRadiusChange = { viewModel.setRadiusOption(it) },
                        onZoomRangeChange = { min, max -> viewModel.setZoomRange(min, max) },
                        onNext = { viewModel.nextStep() },
                        onBack = { viewModel.previousStep() },
                    )

                DownloadWizardStep.CONFIRM ->
                    ConfirmDownloadStep(
                        latitude = state.centerLatitude ?: 0.0,
                        longitude = state.centerLongitude ?: 0.0,
                        radiusKm = state.radiusOption.km,
                        minZoom = state.minZoom,
                        maxZoom = state.maxZoom,
                        estimatedTileCount = state.estimatedTileCount,
                        estimatedSize = state.getEstimatedSizeString(),
                        name = state.name,
                        httpEnabled = state.httpEnabled,
                        onNameChange = { viewModel.setName(it) },
                        onEnableHttp = { viewModel.enableHttp() },
                        onStartDownload = { viewModel.nextStep() },
                        onBack = { viewModel.previousStep() },
                    )

                DownloadWizardStep.DOWNLOADING ->
                    DownloadingStep(
                        progress = state.downloadProgress,
                        onCancel = { showCancelDialog = true },
                    )
            }
        }
    }

    // Cancel confirmation dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.offline_map_download_cancel_download)) },
            text = {
                Text(stringResource(R.string.offline_map_download_are_you_sure_you_want_to))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelDownload()
                        onNavigateBack()
                    },
                ) {
                    Text(stringResource(R.string.offline_map_download_cancel_download_f647), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
        )
    }
}

@Composable
fun LocationSelectionStep(
    hasLocation: Boolean,
    latitude: Double?,
    longitude: Double?,
    isGeocoderAvailable: Boolean,
    addressQuery: String,
    addressSearchResults: List<AddressSearchResult>,
    isSearchingAddress: Boolean,
    addressSearchError: String?,
    httpEnabled: Boolean,
    onLocationSet: (Double, Double) -> Unit,
    onCurrentLocationRequest: (Location) -> Unit,
    onAddressQueryChange: (String) -> Unit,
    onSearchAddress: () -> Unit,
    onSelectAddressResult: (AddressSearchResult) -> Unit,
    onEnableHttp: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isGettingLocation by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (hasPermission) {
                isGettingLocation = true
                if (LocationCompat.isPlayServicesAvailable(context)) {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    try {
                        fusedClient
                            .getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token,
                            ).addOnSuccessListener { location ->
                                isGettingLocation = false
                                if (location != null) {
                                    onCurrentLocationRequest(location)
                                }
                            }.addOnFailureListener {
                                isGettingLocation = false
                            }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Location permission denied", e)
                        isGettingLocation = false
                    }
                } else {
                    // Fallback to platform LocationManager (issue #456)
                    LocationCompat.getCurrentLocation(context) { location ->
                        isGettingLocation = false
                        if (location != null) {
                            onCurrentLocationRequest(location)
                        }
                    }
                }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Warning banner when HTTP is disabled
            if (!httpEnabled) {
                HttpDisabledWarningBanner(
                    onEnableHttp = onEnableHttp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = stringResource(R.string.offline_map_download_choose_the_center_point_for_your),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Use current location button
            FilledTonalButton(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                enabled = !isGettingLocation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isGettingLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.offline_map_download_use_current_location))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.offline_map_download_or),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Manual coordinate entry
            var latText by remember(latitude) { mutableStateOf(latitude?.toString() ?: "") }
            var lonText by remember(longitude) { mutableStateOf(longitude?.toString() ?: "") }
            var latError by remember(latitude) { mutableStateOf<String?>(null) }
            var lonError by remember(longitude) { mutableStateOf<String?>(null) }

            fun validateAndSetLocation(
                lat: Double?,
                lon: Double?,
            ) {
                if (lat == null || lon == null) return
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
                if (latError != null || lonError != null) return
                onLocationSet(lat, lon)
            }

            OutlinedTextField(
                value = latText,
                onValueChange = {
                    latText = it
                    val lat = it.toDoubleOrNull()
                    latError =
                        when {
                            it.isEmpty() || it == "-" -> null
                            lat == null -> "Invalid number"
                            lat !in -90.0..90.0 -> "Must be between -90 and 90"
                            else -> null
                        }
                    validateAndSetLocation(lat, lonText.toDoubleOrNull())
                },
                label = { Text(stringResource(R.string.offline_map_download_latitude)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = latError != null,
                supportingText =
                    latError?.let {
                        { Text(it) }
                    },
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = lonText,
                onValueChange = {
                    lonText = it
                    val lon = it.toDoubleOrNull()
                    lonError =
                        when {
                            it.isEmpty() || it == "-" -> null
                            lon == null -> "Invalid number"
                            lon !in -180.0..180.0 -> "Must be between -180 and 180"
                            else -> null
                        }
                    validateAndSetLocation(latText.toDoubleOrNull(), lon)
                },
                label = { Text(stringResource(R.string.offline_map_download_longitude)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = lonError != null,
                supportingText =
                    lonError?.let {
                        { Text(it) }
                    },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Address/City search - only show if geocoder is available (requires Google Play Services)
            if (isGeocoderAvailable) {
                Text(
                    text = stringResource(R.string.offline_map_download_or_05b4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = onAddressQueryChange,
                    label = { Text(stringResource(R.string.offline_map_download_search_city_or_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchAddress() }),
                    trailingIcon = {
                        if (isSearchingAddress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (addressQuery.isNotEmpty()) {
                            IconButton(onClick = onSearchAddress) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.contacts_search),
                                )
                            }
                        }
                    },
                    supportingText = {
                        addressSearchError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    isError = addressSearchError != null,
                )

                // Search results
                if (addressSearchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            addressSearchResults.forEach { result ->
                                TextButton(
                                    onClick = { onSelectAddressResult(result) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = result.displayName,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = stringResource(R.string.offline_map_download_or_05b4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Geohash entry
            var geohashText by remember { mutableStateOf("") }
            var geohashError by remember { mutableStateOf<String?>(null) }

            OutlinedTextField(
                value = geohashText,
                onValueChange = { input ->
                    geohashText = input
                    if (input.isNotEmpty()) {
                        val coords = TileDownloadManager.decodeGeohashCenter(input)
                        if (coords != null) {
                            geohashError = null
                            latText = String.format(Locale.US, "%.6f", coords.first)
                            lonText = String.format(Locale.US, "%.6f", coords.second)
                            onLocationSet(coords.first, coords.second)
                        } else {
                            geohashError = "Invalid geohash"
                        }
                    } else {
                        geohashError = null
                    }
                },
                label = { Text(stringResource(R.string.offline_map_download_geohash)) },
                placeholder = { Text(stringResource(R.string.offline_map_download_e_g_dqcjq)) },
                supportingText = {
                    if (geohashError != null) {
                        Text(geohashError!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(stringResource(R.string.offline_map_download_enter_a_geohash_to_set_the))
                    }
                },
                isError = geohashError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (hasLocation) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text =
                                "Location set: ${String.format(Locale.US, "%.4f", latitude)}, " +
                                    String.format(Locale.US, "%.4f", longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            enabled = hasLocation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.offline_map_download_next))
        }
    }
}

@Composable
fun RadiusSelectionStep(
    radiusOption: RadiusOption,
    minZoom: Int,
    maxZoom: Int,
    estimatedTileCount: Long,
    estimatedSize: String,
    onRadiusChange: (RadiusOption) -> Unit,
    onZoomRangeChange: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.offline_map_download_select_area_size),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Radius options
        Column(modifier = Modifier.selectableGroup()) {
            RadiusOption.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = radiusOption == option,
                                onClick = { onRadiusChange(option) },
                                role = Role.RadioButton,
                            ).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = radiusOption == option,
                        onClick = null,
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Zoom range
        Text(
            text = stringResource(R.string.offline_map_download_zoom_range),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Min zoom: $minZoom (less detail, smaller size)",
            style = MaterialTheme.typography.bodyMedium,
        )

        Slider(
            value = minZoom.toFloat(),
            onValueChange = { onZoomRangeChange(it.toInt(), maxZoom) },
            valueRange = 0f..14f,
            steps = 13,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Max zoom: $maxZoom (more detail, larger size)",
            style = MaterialTheme.typography.bodyMedium,
        )

        Slider(
            value = maxZoom.toFloat(),
            onValueChange = { onZoomRangeChange(minZoom, it.toInt()) },
            valueRange = 0f..14f,
            steps = 13,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Estimate card
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.offline_map_download_estimated_download),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$estimatedTileCount tiles",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "~$estimatedSize",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_back))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.offline_map_download_next_10ac))
            }
        }
    }
}

@Composable
fun ConfirmDownloadStep(
    latitude: Double,
    longitude: Double,
    radiusKm: Int,
    minZoom: Int,
    maxZoom: Int,
    estimatedTileCount: Long,
    estimatedSize: String,
    name: String,
    httpEnabled: Boolean,
    onNameChange: (String) -> Unit,
    onEnableHttp: () -> Unit,
    onStartDownload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Warning banner when HTTP is disabled
        if (!httpEnabled) {
            HttpDisabledWarningBanner(
                onEnableHttp = onEnableHttp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.offline_map_download_name_your_map),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.offline_map_download_region_name)) },
            placeholder = { Text(stringResource(R.string.offline_map_download_e_g_home_downtown_trail)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.offline_map_download_summary),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryRow(
                    "Location",
                    "${String.format(Locale.US, "%.4f", latitude)}, " +
                        String.format(Locale.US, "%.4f", longitude),
                )
                SummaryRow("Radius", "$radiusKm km")
                SummaryRow("Zoom Range", "$minZoom - $maxZoom")
                SummaryRow("Tiles", "$estimatedTileCount")
                SummaryRow("Estimated Size", "~$estimatedSize")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text =
                "This will download map tiles from OpenFreeMap for offline use. " +
                    "Make sure you're connected to Wi-Fi for large downloads.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_back))
            }
            Button(
                onClick = onStartDownload,
                enabled = name.isNotBlank() && httpEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.offline_map_download_download))
            }
        }
    }
}

/**
 * Warning banner shown when HTTP map source is disabled.
 * Downloads require HTTP to fetch tiles from the internet.
 */
@Composable
fun HttpDisabledWarningBanner(
    onEnableHttp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.offline_map_download_internet_access_required),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.offline_map_download_http_map_source_is_disabled_enable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            FilledTonalButton(
                onClick = onEnableHttp,
            ) {
                Text(stringResource(R.string.migration_enable))
            }
        }
    }
}

@Composable
fun SummaryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun DownloadingStep(
    progress: DownloadProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (progress == null) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.offline_map_download_preparing_download))
        } else {
            val statusText =
                when {
                    progress.isComplete -> "Complete!"
                    progress.errorMessage != null -> "Error"
                    progress.statusMessage != null -> progress.statusMessage
                    progress.progress > 0 -> "Downloading..."
                    else -> "Preparing..."
                }

            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${(progress.progress * 100).toInt()}%",
                style = MaterialTheme.typography.displaySmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${progress.completedResources} / ${progress.requiredResources} resources",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (progress.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = progress.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Show cancel button while downloading
            if (!progress.isComplete && progress.errorMessage == null) {
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}
