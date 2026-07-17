package com.example.smartroadsystem;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.smartroadsystem.databinding.FragmentMapBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MapFragment extends Fragment
        implements OnMapReadyCallback {

    private static final float NEARBY_DISTANCE_METERS = 10_000f;

    private FragmentMapBinding binding;

    private GoogleMap mMap;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private FusedLocationProviderClient fusedLocationClient;

    /*
     * Location selected for a new report.
     *
     * This can be the user's GPS location or a point
     * selected manually on the map.
     */
    private LatLng selectedLatLng;

    /*
     * Actual GPS location of the device.
     *
     * Nearby hazard distance is calculated from this
     * location only.
     */
    private LatLng currentDeviceLatLng;

    private Marker userSelectedMarker;

    private boolean isLocationManuallySelected = false;

    /*
     * All hazard reports loaded from Firestore.
     */
    private final List<HazardReport> allHazards =
            new ArrayList<>();

    /*
     * Hazard reports located within 10 kilometres.
     */
    private final List<HazardReport> nearbyHazards =
            new ArrayList<>();

    private NearbyHazardAdapter nearbyHazardAdapter;

    private final ActivityResultLauncher<String>
            locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {

                        if (isGranted) {
                            enableLocationAndMoveCamera();
                        } else {
                            updateGpsStatus(false);

                            showLocationPermissionDeniedMessage();

                            moveToDefaultLocation();

                            /*
                             * Hazards can still be displayed,
                             * but distance cannot be calculated.
                             */
                            loadHazardsFromFirestore();
                        }
                    }
            );

    public MapFragment() {
        // Required empty public constructor.
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding =
                FragmentMapBinding.inflate(
                        inflater,
                        container,
                        false
                );

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(
                view,
                savedInstanceState
        );

        initialiseFirebase();
        initialiseLocationClient();
        setupNearbyRecyclerView();
        initialiseMap();
        setupClickListeners();
        loadUserProfile();
    }

    private void initialiseFirebase() {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    private void initialiseLocationClient() {
        fusedLocationClient =
                LocationServices
                        .getFusedLocationProviderClient(
                                requireActivity()
                        );
    }

    private void initialiseMap() {
        SupportMapFragment mapFragment =
                (SupportMapFragment)
                        getChildFragmentManager()
                                .findFragmentById(
                                        R.id.google_map
                                );

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(
                    requireContext(),
                    "Unable to initialise Google Maps.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void setupNearbyRecyclerView() {
        nearbyHazardAdapter =
                new NearbyHazardAdapter(
                        nearbyHazards,
                        this::showHazardInformation
                );

        binding.recyclerNearbyHazards.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );

        binding.recyclerNearbyHazards.setHasFixedSize(
                false
        );

        binding.recyclerNearbyHazards.setNestedScrollingEnabled(
                false
        );

        binding.recyclerNearbyHazards.setAdapter(
                nearbyHazardAdapter
        );
    }

    private void setupClickListeners() {
        binding.btnRefreshLocation.setOnClickListener(
                view -> refreshCurrentLocation()
        );

        binding.btnGoToReport.setOnClickListener(
                view -> openReportPage()
        );

        /*
         * This button must exist in fragment_map.xml:
         *
         * android:id="@+id/btnMapType"
         */
        binding.btnMapType.setOnClickListener(
                this::showMapTypeMenu
        );
    }

    @Override
    public void onMapReady(
            @NonNull GoogleMap googleMap
    ) {
        mMap = googleMap;

        configureMap();
        setupMapClickListener();
        setupMarkerClickListener();
        checkLocationPermission();
    }

    private void configureMap() {
        if (mMap == null) {
            return;
        }

        /*
         * Default map style.
         */
        mMap.setMapType(
                GoogleMap.MAP_TYPE_NORMAL
        );

        /*
         * Enable Google Maps controls and gestures.
         */
        mMap.getUiSettings()
                .setZoomControlsEnabled(true);

        mMap.getUiSettings()
                .setCompassEnabled(true);

        mMap.getUiSettings()
                .setMapToolbarEnabled(true);

        mMap.getUiSettings()
                .setMyLocationButtonEnabled(true);

        mMap.getUiSettings()
                .setZoomGesturesEnabled(true);

        mMap.getUiSettings()
                .setScrollGesturesEnabled(true);

        mMap.getUiSettings()
                .setRotateGesturesEnabled(true);

        mMap.getUiSettings()
                .setTiltGesturesEnabled(true);

        /*
         * Prevent the map from moving to its maximum
         * or minimum zoom level.
         */
        mMap.setMinZoomPreference(3f);
        mMap.setMaxZoomPreference(21f);
    }

    private void showMapTypeMenu(
            @NonNull View anchorView
    ) {
        if (mMap == null) {
            Toast.makeText(
                    requireContext(),
                    "Map is not ready yet.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        PopupMenu popupMenu =
                new PopupMenu(
                        requireContext(),
                        anchorView
                );

        popupMenu.getMenu().add(
                0,
                GoogleMap.MAP_TYPE_NORMAL,
                0,
                "Normal"
        );

        popupMenu.getMenu().add(
                0,
                GoogleMap.MAP_TYPE_SATELLITE,
                1,
                "Satellite"
        );

        popupMenu.getMenu().add(
                0,
                GoogleMap.MAP_TYPE_HYBRID,
                2,
                "Hybrid"
        );

        popupMenu.getMenu().add(
                0,
                GoogleMap.MAP_TYPE_TERRAIN,
                3,
                "Terrain"
        );

        popupMenu.setOnMenuItemClickListener(item -> {

            int selectedMapType =
                    item.getItemId();

            if (
                    selectedMapType ==
                            GoogleMap.MAP_TYPE_NORMAL
            ) {
                mMap.setMapType(
                        GoogleMap.MAP_TYPE_NORMAL
                );

                binding.btnMapType.setText(
                        "Normal"
                );

                return true;
            }

            if (
                    selectedMapType ==
                            GoogleMap.MAP_TYPE_SATELLITE
            ) {
                mMap.setMapType(
                        GoogleMap.MAP_TYPE_SATELLITE
                );

                binding.btnMapType.setText(
                        "Satellite"
                );

                return true;
            }

            if (
                    selectedMapType ==
                            GoogleMap.MAP_TYPE_HYBRID
            ) {
                mMap.setMapType(
                        GoogleMap.MAP_TYPE_HYBRID
                );

                binding.btnMapType.setText(
                        "Hybrid"
                );

                return true;
            }

            if (
                    selectedMapType ==
                            GoogleMap.MAP_TYPE_TERRAIN
            ) {
                mMap.setMapType(
                        GoogleMap.MAP_TYPE_TERRAIN
                );

                binding.btnMapType.setText(
                        "Terrain"
                );

                return true;
            }

            return false;
        });

        popupMenu.show();
    }

    private void setupMapClickListener() {
        if (mMap == null) {
            return;
        }

        mMap.setOnMapClickListener(latLng -> {

            selectedLatLng = latLng;

            isLocationManuallySelected = true;

            updateCoordinateText(
                    latLng
            );

            displaySelectedLocationMarker(
                    latLng
            );

            mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                            latLng,
                            17f
                    )
            );
        });
    }

    private void setupMarkerClickListener() {
        if (mMap == null) {
            return;
        }

        mMap.setOnMarkerClickListener(marker -> {

            Object markerTag =
                    marker.getTag();

            if (markerTag instanceof HazardReport) {
                HazardReport report =
                        (HazardReport) markerTag;

                showHazardInformation(
                        report
                );

                return true;
            }

            /*
             * Show the normal marker information window
             * for the manually selected location.
             */
            marker.showInfoWindow();

            return true;
        });
    }

    private void refreshCurrentLocation() {
        if (!isAdded()) {
            return;
        }

        int permissionStatus =
                ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                );

        if (
                permissionStatus ==
                        PackageManager.PERMISSION_GRANTED
        ) {
            updateGpsStatusLoading();
            getDeviceLocation();
        } else {
            locationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
            );
        }
    }

    private void checkLocationPermission() {
        int permissionStatus =
                ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                );

        if (
                permissionStatus ==
                        PackageManager.PERMISSION_GRANTED
        ) {
            enableLocationAndMoveCamera();
        } else {
            updateGpsStatus(false);

            locationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
            );
        }
    }

    private void enableLocationAndMoveCamera() {
        if (
                mMap == null ||
                        !isAdded()
        ) {
            return;
        }

        int permissionStatus =
                ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                );

        if (
                permissionStatus !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        try {
            mMap.setMyLocationEnabled(true);

            updateGpsStatusLoading();

            getDeviceLocation();

        } catch (SecurityException exception) {
            updateGpsStatus(false);

            Toast.makeText(
                    requireContext(),
                    "Unable to access your location.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void getDeviceLocation() {
        if (!isAdded()) {
            return;
        }

        int permissionStatus =
                ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                );

        if (
                permissionStatus !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        fusedLocationClient
                .getLastLocation()
                .addOnSuccessListener(location -> {

                    if (
                            binding == null ||
                                    mMap == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    if (location == null) {
                        updateGpsStatus(false);

                        showLocationUnavailableMessage();

                        moveToDefaultLocation();

                        loadHazardsFromFirestore();

                        return;
                    }

                    currentDeviceLatLng =
                            new LatLng(
                                    location.getLatitude(),
                                    location.getLongitude()
                            );

                    /*
                     * Use GPS as the report location until
                     * the user selects another point manually.
                     */
                    if (!isLocationManuallySelected) {
                        selectedLatLng =
                                currentDeviceLatLng;

                        updateCoordinateText(
                                selectedLatLng
                        );
                    }

                    updateGpsStatus(true);

                    mMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                    currentDeviceLatLng,
                                    18f
                            )
                    );

                    loadHazardsFromFirestore();
                })
                .addOnFailureListener(exception -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    updateGpsStatus(false);

                    showLocationUnavailableMessage();

                    moveToDefaultLocation();

                    loadHazardsFromFirestore();
                });
    }

    private void openReportPage() {
        if (selectedLatLng == null) {
            Toast.makeText(
                    requireContext(),
                    "Please wait for GPS or tap the map to select a location.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Bundle arguments = new Bundle();

        arguments.putString(
                "selected_coordinates",
                createCoordinateString(
                        selectedLatLng
                )
        );

        arguments.putDouble(
                "selected_latitude",
                selectedLatLng.latitude
        );

        arguments.putDouble(
                "selected_longitude",
                selectedLatLng.longitude
        );

        ReportFragment reportFragment =
                new ReportFragment();

        reportFragment.setArguments(
                arguments
        );

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity())
                    .openReportFragment(
                            reportFragment
                    );
        }
    }

    private void loadUserProfile() {
        FirebaseUser currentUser =
                mAuth.getCurrentUser();

        if (currentUser == null) {
            if (binding != null) {
                binding.tvWelcome.setText(
                        "Welcome, Guest"
                );
            }

            return;
        }

        String userId =
                currentUser.getUid();

        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (binding == null) {
                        return;
                    }

                    String firestoreName =
                            documentSnapshot.getString(
                                    "name"
                            );

                    if (
                            firestoreName != null &&
                                    !firestoreName
                                            .trim()
                                            .isEmpty()
                    ) {
                        binding.tvWelcome.setText(
                                "Welcome, " +
                                        firestoreName
                        );
                    } else {
                        displayGoogleAccountName(
                                currentUser
                        );
                    }
                })
                .addOnFailureListener(exception -> {

                    if (binding == null) {
                        return;
                    }

                    displayGoogleAccountName(
                            currentUser
                    );
                });
    }

    private void displayGoogleAccountName(
            @NonNull FirebaseUser currentUser
    ) {
        if (binding == null) {
            return;
        }

        String googleName =
                currentUser.getDisplayName();

        if (
                googleName != null &&
                        !googleName.trim().isEmpty()
        ) {
            binding.tvWelcome.setText(
                    "Welcome, " +
                            googleName
            );
        } else {
            binding.tvWelcome.setText(
                    "Welcome, User"
            );
        }
    }

    private void loadHazardsFromFirestore() {
        if (
                db == null ||
                        binding == null
        ) {
            return;
        }

        showHazardLoading(true);

        db.collection("reports")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    if (
                            binding == null ||
                                    mMap == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    allHazards.clear();
                    nearbyHazards.clear();

                    mMap.clear();

                    userSelectedMarker = null;

                    /*
                     * Restore the manually selected marker
                     * after clearing the map.
                     */
                    if (
                            selectedLatLng != null &&
                                    isLocationManuallySelected
                    ) {
                        displaySelectedLocationMarker(
                                selectedLatLng
                        );
                    }

                    for (
                            QueryDocumentSnapshot document :
                            queryDocumentSnapshots
                    ) {
                        try {
                            Double latitude =
                                    readCoordinate(
                                            document,
                                            "latitude"
                                    );

                            Double longitude =
                                    readCoordinate(
                                            document,
                                            "longitude"
                                    );

                            if (
                                    latitude == null ||
                                            longitude == null
                            ) {
                                continue;
                            }

                            if (
                                    !isCoordinateValid(
                                            latitude,
                                            longitude
                                    )
                            ) {
                                continue;
                            }

                            HazardReport report =
                                    document.toObject(
                                            HazardReport.class
                                    );

                            report.setId(
                                    document.getId()
                            );

                            report.setLatitude(
                                    latitude
                            );

                            report.setLongitude(
                                    longitude
                            );

                            allHazards.add(
                                    report
                            );

                            addHazardMarker(
                                    report
                            );

                            if (currentDeviceLatLng != null) {
                                float distanceMeters =
                                        calculateDistanceMeters(
                                                currentDeviceLatLng.latitude,
                                                currentDeviceLatLng.longitude,
                                                latitude,
                                                longitude
                                        );

                                report.setDistanceMeters(
                                        distanceMeters
                                );

                                if (
                                        distanceMeters <=
                                                NEARBY_DISTANCE_METERS
                                ) {
                                    nearbyHazards.add(
                                            report
                                    );
                                }
                            }

                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    }

                    sortNearbyHazards();

                    nearbyHazardAdapter
                            .notifyDataSetChanged();

                    showHazardLoading(false);

                    updateNearbyHazardUi();
                })
                .addOnFailureListener(exception -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    showHazardLoading(false);

                    updateNearbyHazardUi();

                    Toast.makeText(
                            requireContext(),
                            "Unable to load road hazards: " +
                                    exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void addHazardMarker(
            @NonNull HazardReport report
    ) {
        if (mMap == null) {
            return;
        }

        String hazardType =
                report.getHazardType();

        String description =
                report.getDescription();

        if (
                hazardType == null ||
                        hazardType.trim().isEmpty()
        ) {
            hazardType = "Road Hazard";
        }

        if (description == null) {
            description = "";
        }

        LatLng position =
                new LatLng(
                        report.getLatitude(),
                        report.getLongitude()
                );

        Marker marker =
                mMap.addMarker(
                        new MarkerOptions()
                                .position(position)
                                .title(hazardType)
                                .snippet(description)
                );

        if (marker != null) {
            marker.setTag(
                    report
            );
        }
    }

    private float calculateDistanceMeters(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        float[] result =
                new float[1];

        Location.distanceBetween(
                startLatitude,
                startLongitude,
                endLatitude,
                endLongitude,
                result
        );

        return result[0];
    }

    private void sortNearbyHazards() {
        Collections.sort(
                nearbyHazards,
                (firstReport, secondReport) ->
                        Float.compare(
                                firstReport.getDistanceMeters(),
                                secondReport.getDistanceMeters()
                        )
        );
    }

    private void updateNearbyHazardUi() {
        if (binding == null) {
            return;
        }

        int nearbyCount =
                nearbyHazards.size();

        binding.tvHazardCount.setText(
                nearbyCount + " nearby"
        );

        if (currentDeviceLatLng == null) {
            binding.recyclerNearbyHazards.setVisibility(
                    View.GONE
            );

            binding.cardNoHazards.setVisibility(
                    View.VISIBLE
            );

            binding.tvNoHazards.setText(
                    "GPS location is required"
            );

            return;
        }

        if (nearbyHazards.isEmpty()) {
            binding.recyclerNearbyHazards.setVisibility(
                    View.GONE
            );

            binding.cardNoHazards.setVisibility(
                    View.VISIBLE
            );

            binding.tvNoHazards.setText(
                    "No nearby hazards found"
            );
        } else {
            binding.cardNoHazards.setVisibility(
                    View.GONE
            );

            binding.recyclerNearbyHazards.setVisibility(
                    View.VISIBLE
            );
        }
    }

    private void showHazardLoading(
            boolean loading
    ) {
        if (binding == null) {
            return;
        }

        binding.progressHazards.setVisibility(
                loading
                        ? View.VISIBLE
                        : View.GONE
        );

        if (loading) {
            binding.recyclerNearbyHazards.setVisibility(
                    View.GONE
            );

            binding.cardNoHazards.setVisibility(
                    View.GONE
            );
        }
    }

    private void showHazardInformation(
            @NonNull HazardReport report
    ) {
        String hazardType =
                report.getHazardType();

        String description =
                report.getDescription();

        String status =
                report.getStatus();

        if (
                hazardType == null ||
                        hazardType.trim().isEmpty()
        ) {
            hazardType = "Road Hazard";
        }

        if (
                description == null ||
                        description.trim().isEmpty()
        ) {
            description =
                    "No description provided.";
        }

        if (
                status == null ||
                        status.trim().isEmpty()
        ) {
            status = "New";
        }

        String distanceText;

        if (currentDeviceLatLng == null) {
            distanceText =
                    "GPS unavailable";
        } else {
            float distanceKm =
                    report.getDistanceMeters() /
                            1000f;

            distanceText =
                    String.format(
                            Locale.US,
                            "%.2f km",
                            distanceKm
                    );
        }

        String message =
                "Status: " +
                        status +
                        "\n\n" +

                        "Description:\n" +
                        description +
                        "\n\n" +

                        "Distance: " +
                        distanceText +
                        "\n\n" +

                        "Coordinates:\n" +
                        String.format(
                                Locale.US,
                                "%.6f, %.6f",
                                report.getLatitude(),
                                report.getLongitude()
                        );

        new com.google.android.material.dialog
                .MaterialAlertDialogBuilder(
                requireContext()
        )
                .setTitle(hazardType)
                .setMessage(message)
                .setNegativeButton(
                        "Close",
                        null
                )
                .setPositiveButton(
                        "View on Map",
                        (dialog, which) -> {

                            if (mMap == null) {
                                return;
                            }

                            LatLng hazardLocation =
                                    new LatLng(
                                            report.getLatitude(),
                                            report.getLongitude()
                                    );

                            mMap.animateCamera(
                                    CameraUpdateFactory
                                            .newLatLngZoom(
                                                    hazardLocation,
                                                    17f
                                            )
                            );
                        }
                )
                .show();
    }

    @Nullable
    private Double readCoordinate(
            @NonNull QueryDocumentSnapshot document,
            @NonNull String fieldName
    ) {
        Object value =
                document.get(fieldName);

        if (value instanceof Number) {
            return ((Number) value)
                    .doubleValue();
        }

        if (value instanceof String) {
            try {
                return Double.parseDouble(
                        ((String) value).trim()
                );
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private boolean isCoordinateValid(
            double latitude,
            double longitude
    ) {
        return latitude >= -90 &&
                latitude <= 90 &&
                longitude >= -180 &&
                longitude <= 180 &&
                !(latitude == 0.0 &&
                        longitude == 0.0);
    }

    private void updateCoordinateText(
            @NonNull LatLng latLng
    ) {
        if (binding == null) {
            return;
        }

        binding.tvLatitude.setText(
                String.format(
                        Locale.US,
                        "%.6f",
                        latLng.latitude
                )
        );

        binding.tvLongitude.setText(
                String.format(
                        Locale.US,
                        "%.6f",
                        latLng.longitude
                )
        );
    }

    private String createCoordinateString(
            @NonNull LatLng latLng
    ) {
        return String.format(
                Locale.US,
                "Lat: %.6f, Long: %.6f",
                latLng.latitude,
                latLng.longitude
        );
    }

    private void displaySelectedLocationMarker(
            @NonNull LatLng latLng
    ) {
        if (mMap == null) {
            return;
        }

        if (userSelectedMarker != null) {
            userSelectedMarker.setPosition(
                    latLng
            );
        } else {
            userSelectedMarker =
                    mMap.addMarker(
                            new MarkerOptions()
                                    .position(latLng)
                                    .title(
                                            "Selected Report Location"
                                    )
                                    .snippet(
                                            "The hazard report will use this location."
                                    )
                    );
        }

        if (userSelectedMarker != null) {
            userSelectedMarker.showInfoWindow();
        }
    }

    private void updateGpsStatusLoading() {
        if (binding == null) {
            return;
        }

        binding.tvGpsStatus.setText(
                "Locating..."
        );
    }

    private void updateGpsStatus(
            boolean active
    ) {
        if (binding == null) {
            return;
        }

        binding.tvGpsStatus.setText(
                active
                        ? "GPS Active"
                        : "GPS Unavailable"
        );
    }

    private void moveToDefaultLocation() {
        if (
                mMap == null ||
                        binding == null
        ) {
            return;
        }

        LatLng defaultLocation =
                new LatLng(
                        3.1390,
                        101.6869
                );

        currentDeviceLatLng = null;

        if (!isLocationManuallySelected) {
            selectedLatLng = null;
        }

        binding.tvLatitude.setText(
                "Unavailable"
        );

        binding.tvLongitude.setText(
                "Unavailable"
        );

        mMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                        defaultLocation,
                        11f
                )
        );
    }

    private void showLocationUnavailableMessage() {
        if (
                binding == null ||
                        !isAdded()
        ) {
            return;
        }

        Toast.makeText(
                requireContext(),
                "Current GPS location is unavailable. Tap Refresh or select a report location manually.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void showLocationPermissionDeniedMessage() {
        if (!isAdded()) {
            return;
        }

        Toast.makeText(
                requireContext(),
                "Location permission was denied. Nearby hazard distance cannot be calculated.",
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (
                mMap != null &&
                        db != null
        ) {
            loadHazardsFromFirestore();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }
}