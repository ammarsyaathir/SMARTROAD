package com.example.smartroadsystem;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.smartroadsystem.databinding.FragmentReportBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReportFragment extends Fragment {

    private FragmentReportBinding binding;

    private FusedLocationProviderClient fusedLocationClient;

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;
    private FirebaseStorage firebaseStorage;

    private Uri capturedImageUri;
    private File capturedImageFile;

    private String currentCoordinates = "";

    private double selectedLatitude = 0.0;
    private double selectedLongitude = 0.0;

    private boolean hasValidCoordinates = false;
    private boolean isSubmitting = false;

    /*
     * TakePicture saves a full-resolution image into the Uri supplied
     * through cameraLauncher.launch(capturedImageUri).
     */
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicture(),
                    success -> {

                        if (!success || capturedImageUri == null) {
                            deleteUnusedTemporaryImage();

                            showToast(
                                    "The picture was not captured."
                            );

                            return;
                        }

                        if (binding == null) {
                            return;
                        }

                        displayCapturedImage();
                    }
            );

    public ReportFragment() {
        // Required empty public constructor.
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding =
                FragmentReportBinding.inflate(
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

        initialiseServices();
        setupHazardDropdown();
        setupClickListeners();
        readCoordinates();
        resetPhotoUi();
    }

    private void initialiseServices() {
        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();

        firebaseStorage =
                FirebaseStorage.getInstance();

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(
                        requireActivity()
                );
    }

    private void setupHazardDropdown() {
        String[] issueTypes = {
                "Pothole",
                "Landslide",
                "Flooding",
                "Road Obstruction",
                "Traffic Light Malfunction",
                "Damaged Road Sign",
                "Road Surface Damage",
                "Other"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        issueTypes
                );

        binding.spinnerIssueType.setAdapter(
                adapter
        );

        /*
         * Prevent manual typing.
         * Users select an item from the dropdown.
         */
        binding.spinnerIssueType.setKeyListener(
                null
        );

        binding.spinnerIssueType.setOnClickListener(
                view ->
                        binding.spinnerIssueType
                                .showDropDown()
        );

        binding.spinnerIssueType.setOnItemClickListener(
                (parent, selectedView, position, id) ->
                        binding.layoutIssueType
                                .setError(null)
        );
    }

    private void setupClickListeners() {
        binding.btnCaptureImage.setOnClickListener(
                view -> openCamera()
        );

        binding.btnReplacePhoto.setOnClickListener(
                view -> openCamera()
        );

        binding.btnSubmitReport.setOnClickListener(
                view -> validateAndSubmitReport()
        );
    }

    private void readCoordinates() {
        Bundle arguments =
                getArguments();

        if (arguments != null) {

            /*
             * Preferred values sent separately from MapFragment.
             */
            if (
                    arguments.containsKey(
                            "selected_latitude"
                    ) &&
                            arguments.containsKey(
                                    "selected_longitude"
                            )
            ) {
                selectedLatitude =
                        arguments.getDouble(
                                "selected_latitude"
                        );

                selectedLongitude =
                        arguments.getDouble(
                                "selected_longitude"
                        );

                hasValidCoordinates =
                        isCoordinateValid(
                                selectedLatitude,
                                selectedLongitude
                        );

                if (hasValidCoordinates) {
                    updateCoordinateField();
                    return;
                }
            }

            /*
             * Compatibility with older MapFragment code that sends
             * one coordinate string.
             */
            if (
                    arguments.containsKey(
                            "selected_coordinates"
                    )
            ) {
                String coordinateString =
                        arguments.getString(
                                "selected_coordinates",
                                ""
                        );

                if (
                        extractCoordinatesFromString(
                                coordinateString
                        )
                ) {
                    updateCoordinateField();
                    return;
                }
            }
        }

        /*
         * Report page opened directly from bottom navigation.
         */
        getDeviceLocation();
    }

    private void getDeviceLocation() {
        if (!isAdded() || binding == null) {
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
            hasValidCoordinates = false;

            binding.etCoordinates.setText(
                    "GPS permission is required."
            );

            binding.layoutCoordinates.setError(
                    "Allow location permission or select a location from the map."
            );

            return;
        }

        binding.etCoordinates.setText(
                "Getting GPS location..."
        );

        fusedLocationClient
                .getLastLocation()
                .addOnSuccessListener(location -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    if (location == null) {
                        hasValidCoordinates = false;

                        binding.etCoordinates.setText(
                                "GPS location unavailable."
                        );

                        binding.layoutCoordinates.setError(
                                "Return to the map and select a report location."
                        );

                        return;
                    }

                    selectedLatitude =
                            location.getLatitude();

                    selectedLongitude =
                            location.getLongitude();

                    hasValidCoordinates =
                            isCoordinateValid(
                                    selectedLatitude,
                                    selectedLongitude
                            );

                    updateCoordinateField();
                })
                .addOnFailureListener(exception -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    hasValidCoordinates = false;

                    binding.etCoordinates.setText(
                            "Failed to obtain GPS location."
                    );

                    binding.layoutCoordinates.setError(
                            "Return to the map and select a report location."
                    );
                });
    }

    private void updateCoordinateField() {
        if (binding == null) {
            return;
        }

        currentCoordinates =
                String.format(
                        Locale.US,
                        "Lat: %.6f, Long: %.6f",
                        selectedLatitude,
                        selectedLongitude
                );

        binding.etCoordinates.setText(
                currentCoordinates
        );

        binding.layoutCoordinates.setError(
                null
        );
    }

    private boolean extractCoordinatesFromString(
            @Nullable String coordinateString
    ) {
        if (
                coordinateString == null ||
                        coordinateString.trim().isEmpty()
        ) {
            return false;
        }

        try {
            Pattern pattern =
                    Pattern.compile(
                            "[-+]?[0-9]*\\.?[0-9]+"
                    );

            Matcher matcher =
                    pattern.matcher(
                            coordinateString
                    );

            int coordinateCount = 0;

            while (matcher.find()) {
                double value =
                        Double.parseDouble(
                                matcher.group()
                        );

                if (coordinateCount == 0) {
                    selectedLatitude = value;

                } else if (coordinateCount == 1) {
                    selectedLongitude = value;
                    coordinateCount++;
                    break;
                }

                coordinateCount++;
            }

            hasValidCoordinates =
                    coordinateCount >= 2 &&
                            isCoordinateValid(
                                    selectedLatitude,
                                    selectedLongitude
                            );

            return hasValidCoordinates;

        } catch (NumberFormatException exception) {
            hasValidCoordinates = false;
            return false;
        }
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

    private void openCamera() {
        if (
                !isAdded() ||
                        isSubmitting
        ) {
            return;
        }

        try {
            File picturesDirectory =
                    requireContext()
                            .getExternalFilesDir(
                                    Environment.DIRECTORY_PICTURES
                            );

            if (picturesDirectory == null) {
                showToast(
                        "Unable to access the picture directory."
                );

                return;
            }

            capturedImageFile =
                    File.createTempFile(
                            "smartroad_hazard_" +
                                    System.currentTimeMillis() +
                                    "_",
                            ".jpg",
                            picturesDirectory
                    );

            capturedImageUri =
                    FileProvider.getUriForFile(
                            requireContext(),
                            requireContext()
                                    .getPackageName() +
                                    ".fileprovider",
                            capturedImageFile
                    );

            cameraLauncher.launch(
                    capturedImageUri
            );

        } catch (IOException exception) {
            capturedImageUri = null;
            capturedImageFile = null;

            showToast(
                    "Unable to prepare the camera file: " +
                            exception.getMessage()
            );

        } catch (IllegalArgumentException exception) {
            capturedImageUri = null;
            capturedImageFile = null;

            showToast(
                    "FileProvider is not configured correctly."
            );
        }
    }

    private void displayCapturedImage() {
        if (
                binding == null ||
                        capturedImageUri == null
        ) {
            return;
        }

        binding.photoPlaceholder.setVisibility(
                View.GONE
        );

        binding.ivReportImage.setVisibility(
                View.VISIBLE
        );

        binding.btnReplacePhoto.setVisibility(
                View.VISIBLE
        );

        binding.btnCaptureImage.setText(
                "Capture Another Photo"
        );

        binding.tvPhotoStatus.setText(
                "Photo Ready"
        );

        binding.tvPhotoStatus.setTextColor(
                Color.parseColor(
                        "#15803D"
                )
        );

        Glide.with(this)
                .load(capturedImageUri)
                .dontAnimate()
                .centerCrop()
                .into(
                        binding.ivReportImage
                );
    }

    private void validateAndSubmitReport() {
        if (
                binding == null ||
                        isSubmitting
        ) {
            return;
        }

        clearInputErrors();

        String hazardType =
                binding.spinnerIssueType
                        .getText()
                        .toString()
                        .trim();

        String description =
                binding.etDescription
                        .getText() != null
                        ? binding.etDescription
                        .getText()
                        .toString()
                        .trim()
                        : "";

        boolean isValid = true;

        if (hazardType.isEmpty()) {
            binding.layoutIssueType.setError(
                    "Please select a hazard type."
            );

            isValid = false;
        }

        if (description.isEmpty()) {
            binding.layoutDescription.setError(
                    "Please describe the road hazard."
            );

            isValid = false;

        } else if (description.length() < 10) {
            binding.layoutDescription.setError(
                    "Please provide at least 10 characters."
            );

            isValid = false;
        }

        if (
                !hasValidCoordinates ||
                        !isCoordinateValid(
                                selectedLatitude,
                                selectedLongitude
                        )
        ) {
            binding.layoutCoordinates.setError(
                    "A valid GPS location is required."
            );

            isValid = false;
        }

        if (
                capturedImageUri == null ||
                        capturedImageFile == null ||
                        !capturedImageFile.exists() ||
                        capturedImageFile.length() == 0
        ) {
            showToast(
                    "Please capture a clear picture of the hazard."
            );

            isValid = false;
        }

        if (!isValid) {
            return;
        }

        prepareReportSubmission(
                hazardType,
                description
        );
    }

    private void clearInputErrors() {
        binding.layoutIssueType.setError(
                null
        );

        binding.layoutDescription.setError(
                null
        );

        binding.layoutCoordinates.setError(
                null
        );
    }

    private void prepareReportSubmission(
            @NonNull String hazardType,
            @NonNull String description
    ) {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            showToast(
                    "Please sign in again before submitting a report."
            );

            return;
        }

        setSubmittingState(
                true
        );

        String userId =
                currentUser.getUid();

        firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    String reportedBy =
                            currentUser.getDisplayName();

                    String firestoreName =
                            documentSnapshot.getString(
                                    "name"
                            );

                    if (
                            firestoreName != null &&
                                    !firestoreName.trim().isEmpty()
                    ) {
                        reportedBy = firestoreName;
                    }

                    if (
                            reportedBy == null ||
                                    reportedBy.trim().isEmpty()
                    ) {
                        reportedBy = "SmartRoad User";
                    }

                    uploadReportImage(
                            userId,
                            reportedBy,
                            hazardType,
                            description
                    );
                })
                .addOnFailureListener(exception -> {

                    String reportedBy =
                            currentUser.getDisplayName();

                    if (
                            reportedBy == null ||
                                    reportedBy.trim().isEmpty()
                    ) {
                        reportedBy = "SmartRoad User";
                    }

                    uploadReportImage(
                            userId,
                            reportedBy,
                            hazardType,
                            description
                    );
                });
    }

    private void uploadReportImage(
            @NonNull String userId,
            @NonNull String reportedBy,
            @NonNull String hazardType,
            @NonNull String description
    ) {
        if (
                capturedImageUri == null ||
                        capturedImageFile == null ||
                        !capturedImageFile.exists()
        ) {
            setSubmittingState(
                    false
            );

            showToast(
                    "The captured photo is unavailable."
            );

            return;
        }

        String fileName =
                "hazard_" +
                        System.currentTimeMillis() +
                        ".jpg";

        StorageReference imageReference =
                firebaseStorage
                        .getReference()
                        .child("report_images")
                        .child(userId)
                        .child(fileName);

        StorageMetadata metadata =
                new StorageMetadata.Builder()
                        .setContentType(
                                "image/jpeg"
                        )
                        .setCustomMetadata(
                                "uploadedBy",
                                userId
                        )
                        .build();

        imageReference
                .putFile(
                        capturedImageUri,
                        metadata
                )
                .continueWithTask(task -> {

                    if (!task.isSuccessful()) {
                        Exception exception =
                                task.getException();

                        if (exception != null) {
                            throw exception;
                        }
                    }

                    return imageReference
                            .getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri ->

                        saveReportToFirestore(
                                userId,
                                reportedBy,
                                hazardType,
                                description,
                                downloadUri.toString()
                        )
                )
                .addOnFailureListener(exception -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    setSubmittingState(
                            false
                    );

                    Toast.makeText(
                            requireContext(),
                            "Unable to upload hazard image: " +
                                    exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void saveReportToFirestore(
            @NonNull String userId,
            @NonNull String reportedBy,
            @NonNull String hazardType,
            @NonNull String description,
            @NonNull String imageUrl
    ) {
        Timestamp currentTimestamp =
                Timestamp.now();

        Map<String, Object> report =
                new HashMap<>();

        report.put(
                "description",
                description
        );

        report.put(
                "hazardType",
                hazardType
        );

        /*
         * Firebase Storage download URL.
         */
        report.put(
                "imageUrl",
                imageUrl
        );

        report.put(
                "latitude",
                selectedLatitude
        );

        report.put(
                "longitude",
                selectedLongitude
        );

        report.put(
                "coordinates",
                currentCoordinates
        );

        report.put(
                "reportedBy",
                reportedBy
        );

        report.put(
                "status",
                "New"
        );

        report.put(
                "adminNote",
                ""
        );

        report.put(
                "submit",
                currentTimestamp
        );

        report.put(
                "updatedAt",
                currentTimestamp
        );

        report.put(
                "userId",
                userId
        );

        firestore.collection("reports")
                .add(report)
                .addOnSuccessListener(documentReference -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    setSubmittingState(
                            false
                    );

                    Toast.makeText(
                            requireContext(),
                            "Road hazard report submitted successfully.",
                            Toast.LENGTH_LONG
                    ).show();

                    resetReportForm();
                    navigateBackToMap();
                })
                .addOnFailureListener(exception -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    setSubmittingState(
                            false
                    );

                    Toast.makeText(
                            requireContext(),
                            "The photo was uploaded, but the report could not be saved: " +
                                    exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void setSubmittingState(
            boolean submitting
    ) {
        isSubmitting = submitting;

        if (binding == null) {
            return;
        }

        binding.submissionProgress.setVisibility(
                submitting
                        ? View.VISIBLE
                        : View.GONE
        );

        binding.btnSubmitReport.setEnabled(
                !submitting
        );

        binding.btnCaptureImage.setEnabled(
                !submitting
        );

        binding.btnReplacePhoto.setEnabled(
                !submitting
        );

        binding.spinnerIssueType.setEnabled(
                !submitting
        );

        binding.etDescription.setEnabled(
                !submitting
        );

        binding.btnSubmitReport.setText(
                submitting
                        ? "Uploading and Submitting..."
                        : "Submit Road Hazard Report"
        );
    }

    private void resetReportForm() {
        if (binding == null) {
            return;
        }

        binding.spinnerIssueType.setText(
                "",
                false
        );

        binding.etDescription.setText(
                ""
        );

        resetPhotoUi();

        capturedImageUri = null;
        capturedImageFile = null;
    }

    private void resetPhotoUi() {
        if (binding == null) {
            return;
        }

        binding.ivReportImage.setImageDrawable(
                null
        );

        binding.ivReportImage.setVisibility(
                View.GONE
        );

        binding.photoPlaceholder.setVisibility(
                View.VISIBLE
        );

        binding.btnReplacePhoto.setVisibility(
                View.GONE
        );

        binding.btnCaptureImage.setText(
                "Open Camera"
        );

        binding.tvPhotoStatus.setText(
                "Required"
        );

        binding.tvPhotoStatus.setTextColor(
                Color.parseColor(
                        "#B45309"
                )
        );
    }

    private void deleteUnusedTemporaryImage() {
        if (
                capturedImageFile != null &&
                        capturedImageFile.exists()
        ) {
            //noinspection ResultOfMethodCallIgnored
            capturedImageFile.delete();
        }

        capturedImageUri = null;
        capturedImageFile = null;
    }

    private void navigateBackToMap() {
        if (!isAdded()) {
            return;
        }

        BottomNavigationView bottomNavigation =
                requireActivity().findViewById(
                        R.id.bottom_navigation
                );

        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(
                    R.id.nav_map
            );

        } else {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.fragment_container,
                            new MapFragment()
                    )
                    .commit();
        }
    }

    private void showToast(
            @NonNull String message
    ) {
        if (!isAdded()) {
            return;
        }

        Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}