package com.example.smartroadsystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.example.smartroadsystem.databinding.FragmentProfileBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ProfileFragment extends Fragment {

    /*
     * A profile picture is displayed inside a relatively small
     * circular ImageView, so 512px is clear enough while helping
     * keep the Firestore user document below its size limit.
     */
    private static final int MAX_PROFILE_IMAGE_DIMENSION = 512;

    /*
     * Base64 increases the byte size by approximately 33%.
     * Keeping the compressed JPEG at around 220 KB leaves enough
     * room for the user's other Firestore fields.
     */
    private static final int MAX_PROFILE_IMAGE_BYTES =
            220 * 1024;

    private FragmentProfileBinding binding;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private ActivityResultLauncher<String> imagePickerLauncher;

    private boolean isProcessingPhoto = false;

    public ProfileFragment() {
        // Required empty public constructor.
    }

    @Override
    public void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        imagePickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.GetContent(),
                        imageUri -> {

                            if (imageUri == null) {
                                return;
                            }

                            /*
                             * Show an immediate local preview.
                             */
                            if (binding != null) {
                                Glide.with(this)
                                        .load(imageUri)
                                        .centerCrop()
                                        .into(
                                                binding.ivProfileAvatar
                                        );
                            }

                            processAndSaveProfileImage(
                                    imageUri
                            );
                        }
                );
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding =
                FragmentProfileBinding.inflate(
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
        setupClickListeners();

        loadUserData();
        loadReportStatistics();
    }

    private void initialiseFirebase() {
        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();
    }

    private void setupClickListeners() {

        binding.ivProfileAvatar
                .setOnClickListener(view ->
                        openImagePicker()
                );

        binding.btnChangePhoto
                .setOnClickListener(view ->
                        openImagePicker()
                );

        binding.cardChangePhoto
                .setOnClickListener(view ->
                        openImagePicker()
                );

        binding.cardMyReports
                .setOnClickListener(view ->
                        openMyReportsPage()
                );

        binding.cardReportSummary
                .setOnClickListener(view ->
                        openMyReportsPage()
                );

        binding.btnLogout
                .setOnClickListener(view ->
                        logoutUser()
                );
    }

    private void openImagePicker() {
        if (isProcessingPhoto) {
            return;
        }

        if (imagePickerLauncher == null) {
            showToast(
                    "Image picker is unavailable."
            );

            return;
        }

        imagePickerLauncher.launch(
                "image/*"
        );
    }

    private void loadUserData() {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            openLoginPage();
            return;
        }

        displayAuthenticationEmail(
                currentUser
        );

        displayAuthenticationLastLogin(
                currentUser
        );

        if (binding != null) {
            binding.tvProfileName.setText(
                    "Loading..."
            );
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (binding == null) {
                        return;
                    }

                    if (!documentSnapshot.exists()) {
                        displayFirebaseFallbackProfile(
                                currentUser
                        );

                        createMissingUserDocument(
                                currentUser
                        );

                        return;
                    }

                    displayFirestoreProfile(
                            documentSnapshot,
                            currentUser
                    );
                })
                .addOnFailureListener(exception -> {

                    if (binding == null) {
                        return;
                    }

                    displayFirebaseFallbackProfile(
                            currentUser
                    );

                    showToast(
                            "Unable to load profile: " +
                                    exception.getMessage()
                    );
                });
    }

    private void createMissingUserDocument(
            @NonNull FirebaseUser currentUser
    ) {
        Map<String, Object> userData =
                new HashMap<>();

        userData.put(
                "uid",
                currentUser.getUid()
        );

        userData.put(
                "name",
                getFirebaseDisplayName(
                        currentUser
                )
        );

        userData.put(
                "email",
                currentUser.getEmail() != null
                        ? currentUser.getEmail()
                        : ""
        );

        userData.put(
                "role",
                "user"
        );

        /*
         * Keep the Google account photo initially.
         * A custom Base64 photo will replace this field later.
         */
        if (currentUser.getPhotoUrl() != null) {
            String authenticationPhoto =
                    currentUser
                            .getPhotoUrl()
                            .toString();

            userData.put(
                    "photoUrl",
                    authenticationPhoto
            );

            userData.put(
                    "profileImageUrl",
                    authenticationPhoto
            );
        }

        userData.put(
                "profileUpdatedAt",
                Timestamp.now()
        );

        firestore.collection("users")
                .document(currentUser.getUid())
                .set(
                        userData,
                        SetOptions.merge()
                )
                .addOnFailureListener(exception -> {

                    if (!isAdded()) {
                        return;
                    }

                    showToast(
                            "Unable to create user profile: " +
                                    exception.getMessage()
                    );
                });
    }

    private void displayFirestoreProfile(
            @NonNull DocumentSnapshot documentSnapshot,
            @NonNull FirebaseUser currentUser
    ) {
        if (binding == null) {
            return;
        }

        String name =
                documentSnapshot.getString(
                        "name"
                );

        String email =
                documentSnapshot.getString(
                        "email"
                );

        String photoUrl =
                documentSnapshot.getString(
                        "photoUrl"
                );

        /*
         * Compatibility with previous code.
         */
        if (
                photoUrl == null ||
                        photoUrl.trim().isEmpty()
        ) {
            photoUrl =
                    documentSnapshot.getString(
                            "profileImageUrl"
                    );
        }

        Timestamp lastLoginTimestamp =
                documentSnapshot.getTimestamp(
                        "lastLoginAt"
                );

        if (
                name == null ||
                        name.trim().isEmpty()
        ) {
            name = getFirebaseDisplayName(
                    currentUser
            );
        }

        binding.tvProfileName.setText(
                name
        );

        if (
                email != null &&
                        !email.trim().isEmpty()
        ) {
            binding.tvProfileEmail.setText(
                    email
            );
        } else {
            displayAuthenticationEmail(
                    currentUser
            );
        }

        if (lastLoginTimestamp != null) {
            displayLastLoginDate(
                    lastLoginTimestamp
            );
        } else {
            displayAuthenticationLastLogin(
                    currentUser
            );
        }

        if (
                photoUrl != null &&
                        !photoUrl.trim().isEmpty()
        ) {
            loadProfileImage(
                    photoUrl
            );

        } else if (
                currentUser.getPhotoUrl() != null
        ) {
            loadProfileImage(
                    currentUser
                            .getPhotoUrl()
                            .toString()
            );
        } else {
            displayDefaultProfileImage();
        }
    }

    private void displayFirebaseFallbackProfile(
            @NonNull FirebaseUser currentUser
    ) {
        if (binding == null) {
            return;
        }

        binding.tvProfileName.setText(
                getFirebaseDisplayName(
                        currentUser
                )
        );

        displayAuthenticationEmail(
                currentUser
        );

        displayAuthenticationLastLogin(
                currentUser
        );

        if (currentUser.getPhotoUrl() != null) {
            loadProfileImage(
                    currentUser
                            .getPhotoUrl()
                            .toString()
            );
        } else {
            displayDefaultProfileImage();
        }
    }

    private void displayAuthenticationEmail(
            @NonNull FirebaseUser currentUser
    ) {
        if (binding == null) {
            return;
        }

        String email =
                currentUser.getEmail();

        binding.tvProfileEmail.setText(
                email == null ||
                        email.trim().isEmpty()
                        ? "Email unavailable"
                        : email
        );
    }

    private String getFirebaseDisplayName(
            @NonNull FirebaseUser currentUser
    ) {
        String displayName =
                currentUser.getDisplayName();

        if (
                displayName == null ||
                        displayName.trim().isEmpty()
        ) {
            return "SMARTROAD User";
        }

        return displayName;
    }

    /**
     * Loads either:
     *
     * 1. A Base64 data URI stored in Firestore.
     * 2. A normal web image URL such as a Google profile picture.
     */
    private void loadProfileImage(
            @Nullable String imageValue
    ) {
        if (
                binding == null ||
                        imageValue == null ||
                        imageValue.trim().isEmpty()
        ) {
            displayDefaultProfileImage();
            return;
        }

        String trimmedValue =
                imageValue.trim();

        if (
                trimmedValue.startsWith(
                        "data:image"
                )
        ) {
            byte[] imageBytes =
                    decodeBase64Image(
                            trimmedValue
                    );

            if (
                    imageBytes == null ||
                            imageBytes.length == 0
            ) {
                displayDefaultProfileImage();
                return;
            }

            Glide.with(this)
                    .load(imageBytes)
                    .dontAnimate()
                    .centerCrop()
                    .placeholder(
                            android.R.drawable.sym_def_app_icon
                    )
                    .error(
                            android.R.drawable.sym_def_app_icon
                    )
                    .into(
                            binding.ivProfileAvatar
                    );

        } else {
            Glide.with(this)
                    .load(trimmedValue)
                    .dontAnimate()
                    .centerCrop()
                    .placeholder(
                            android.R.drawable.sym_def_app_icon
                    )
                    .error(
                            android.R.drawable.sym_def_app_icon
                    )
                    .into(
                            binding.ivProfileAvatar
                    );
        }
    }

    @Nullable
    private byte[] decodeBase64Image(
            @NonNull String dataUri
    ) {
        try {
            int commaIndex =
                    dataUri.indexOf(',');

            String base64Content =
                    commaIndex >= 0
                            ? dataUri.substring(
                            commaIndex + 1
                    )
                            : dataUri;

            return Base64.decode(
                    base64Content,
                    Base64.DEFAULT
            );

        } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private void displayDefaultProfileImage() {
        if (binding == null) {
            return;
        }

        binding.ivProfileAvatar.setImageResource(
                android.R.drawable.sym_def_app_icon
        );
    }

    private void processAndSaveProfileImage(
            @NonNull Uri imageUri
    ) {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            showToast(
                    "Please log in again."
            );

            openLoginPage();
            return;
        }

        if (isProcessingPhoto) {
            return;
        }

        setPhotoLoading(
                true
        );

        /*
         * Image decoding and compression should not run
         * on the main UI thread.
         */
        new Thread(() -> {

            String base64Image =
                    createOptimisedBase64Image(
                            imageUri
                    );

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {

                if (
                        binding == null ||
                                !isAdded()
                ) {
                    return;
                }

                if (
                        base64Image == null ||
                                base64Image.trim().isEmpty()
                ) {
                    setPhotoLoading(
                            false
                    );

                    /*
                     * Restore the currently saved image.
                     */
                    loadUserData();

                    showToast(
                            "Unable to process the selected profile picture."
                    );

                    return;
                }

                saveProfileImageToFirestore(
                        currentUser,
                        base64Image
                );
            });
        }).start();
    }

    /**
     * Uses Glide to:
     *
     * - Respect image rotation metadata.
     * - Resize large gallery images efficiently.
     * - Avoid loading a full multi-megapixel image unnecessarily.
     */
    @Nullable
    private String createOptimisedBase64Image(
            @NonNull Uri imageUri
    ) {
        FutureTarget<Bitmap> futureTarget =
                null;

        Bitmap selectedBitmap =
                null;

        try {
            futureTarget =
                    Glide.with(
                                    requireContext()
                            )
                            .asBitmap()
                            .load(imageUri)
                            .submit(
                                    MAX_PROFILE_IMAGE_DIMENSION,
                                    MAX_PROFILE_IMAGE_DIMENSION
                            );

            selectedBitmap =
                    futureTarget.get();

            if (selectedBitmap == null) {
                return null;
            }

            byte[] compressedBytes =
                    compressBitmapWithinLimit(
                            selectedBitmap
                    );

            if (
                    compressedBytes == null ||
                            compressedBytes.length == 0
            ) {
                return null;
            }

            return Base64.encodeToString(
                    compressedBytes,
                    Base64.NO_WRAP
            );

        } catch (
                ExecutionException |
                InterruptedException exception
        ) {
            exception.printStackTrace();

            if (
                    exception instanceof
                            InterruptedException
            ) {
                Thread.currentThread()
                        .interrupt();
            }

            return null;

        } finally {
            if (futureTarget != null) {
                try {
                    Glide.with(
                            requireContext()
                    ).clear(
                            futureTarget
                    );
                } catch (Exception ignored) {
                    // Fragment may already be detached.
                }
            }
        }
    }

    /**
     * Starts with good JPEG quality and lowers it only when
     * necessary to stay below the target size.
     */
    @Nullable
    private byte[] compressBitmapWithinLimit(
            @NonNull Bitmap bitmap
    ) {
        int jpegQuality = 88;

        byte[] compressedBytes =
                null;

        while (jpegQuality >= 50) {
            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            boolean successful =
                    bitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            jpegQuality,
                            outputStream
                    );

            if (!successful) {
                return null;
            }

            compressedBytes =
                    outputStream.toByteArray();

            if (
                    compressedBytes.length <=
                            MAX_PROFILE_IMAGE_BYTES
            ) {
                return compressedBytes;
            }

            jpegQuality -= 7;
        }

        /*
         * Keep a slightly larger image only when it remains
         * safely below the Firestore document limit.
         */
        if (
                compressedBytes != null &&
                        compressedBytes.length <=
                                300 * 1024
        ) {
            return compressedBytes;
        }

        return null;
    }

    private void saveProfileImageToFirestore(
            @NonNull FirebaseUser currentUser,
            @NonNull String base64Image
    ) {
        String dataUri =
                "data:image/jpeg;base64," +
                        base64Image;

        Map<String, Object> profileUpdates =
                new HashMap<>();

        /*
         * Keep both names for compatibility with earlier code.
         */
        profileUpdates.put(
                "photoUrl",
                dataUri
        );

        profileUpdates.put(
                "profileImageUrl",
                dataUri
        );

        profileUpdates.put(
                "profileUpdatedAt",
                Timestamp.now()
        );

        firestore.collection("users")
                .document(currentUser.getUid())
                .set(
                        profileUpdates,
                        SetOptions.merge()
                )
                .addOnSuccessListener(unused -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    setPhotoLoading(
                            false
                    );

                    loadProfileImage(
                            dataUri
                    );

                    showToast(
                            "Profile picture updated successfully."
                    );
                })
                .addOnFailureListener(exception -> {

                    if (
                            binding == null ||
                                    !isAdded()
                    ) {
                        return;
                    }

                    setPhotoLoading(
                            false
                    );

                    loadUserData();

                    Toast.makeText(
                            requireContext(),
                            "Unable to save profile picture: " +
                                    exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void displayLastLoginDate(
            @Nullable Timestamp timestamp
    ) {
        if (
                binding == null ||
                        timestamp == null
        ) {
            return;
        }

        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        Locale.getDefault()
                );

        binding.tvLastLogin.setText(
                formatter.format(
                        timestamp.toDate()
                )
        );
    }

    private void displayAuthenticationLastLogin(
            @NonNull FirebaseUser currentUser
    ) {
        if (binding == null) {
            return;
        }

        if (currentUser.getMetadata() == null) {
            binding.tvLastLogin.setText(
                    "Not available"
            );

            return;
        }

        long lastSignInTime =
                currentUser
                        .getMetadata()
                        .getLastSignInTimestamp();

        if (lastSignInTime <= 0) {
            binding.tvLastLogin.setText(
                    "Not available"
            );

            return;
        }

        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        Locale.getDefault()
                );

        binding.tvLastLogin.setText(
                formatter.format(
                        lastSignInTime
                )
        );
    }

    private void loadReportStatistics() {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (
                currentUser == null ||
                        binding == null
        ) {
            return;
        }

        String userId =
                currentUser.getUid();

        binding.tvMyReportsCount.setText(
                "0"
        );

        binding.tvResolvedCount.setText(
                "0"
        );

        binding.tvPendingCount.setText(
                "0"
        );

        firestore.collection("reports")
                .whereEqualTo(
                        "userId",
                        userId
                )
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (binding == null) {
                        return;
                    }

                    int totalReports =
                            querySnapshot.size();

                    int resolvedReports = 0;
                    int activeReports = 0;

                    for (
                            DocumentSnapshot document :
                            querySnapshot.getDocuments()
                    ) {
                        String status =
                                document.getString(
                                        "status"
                                );

                        if (status == null) {
                            activeReports++;
                            continue;
                        }

                        String normalizedStatus =
                                status.trim()
                                        .toLowerCase(
                                                Locale.ROOT
                                        );

                        if (
                                normalizedStatus.equals(
                                        "resolved"
                                ) ||
                                        normalizedStatus.equals(
                                                "completed"
                                        ) ||
                                        normalizedStatus.equals(
                                                "fixed"
                                        ) ||
                                        normalizedStatus.equals(
                                                "closed"
                                        )
                        ) {
                            resolvedReports++;
                        } else {
                            activeReports++;
                        }
                    }

                    binding.tvMyReportsCount.setText(
                            String.valueOf(
                                    totalReports
                            )
                    );

                    binding.tvResolvedCount.setText(
                            String.valueOf(
                                    resolvedReports
                            )
                    );

                    binding.tvPendingCount.setText(
                            String.valueOf(
                                    activeReports
                            )
                    );

                    binding.tvReportSummary.setText(
                            totalReports == 0
                                    ? "No reports submitted yet"
                                    : "View " +
                                    totalReports +
                                    " submitted report" +
                                    (
                                            totalReports == 1
                                                    ? ""
                                                    : "s"
                                    ) +
                                    " and status updates"
                    );
                })
                .addOnFailureListener(exception -> {

                    if (binding == null) {
                        return;
                    }

                    binding.tvMyReportsCount.setText(
                            "0"
                    );

                    binding.tvResolvedCount.setText(
                            "0"
                    );

                    binding.tvPendingCount.setText(
                            "0"
                    );

                    showToast(
                            "Unable to load report summary: " +
                                    exception.getMessage()
                    );
                });
    }

    private void openMyReportsPage() {
        if (!isAdded()) {
            return;
        }

        /*
         * MainActivity keeps the Profile navigation item selected.
         */
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity())
                    .openMyReportsFragment();
        }
    }

    private void setPhotoLoading(
            boolean loading
    ) {
        isProcessingPhoto = loading;

        if (binding == null) {
            return;
        }

        binding.ivProfileAvatar.setEnabled(
                !loading
        );

        binding.btnChangePhoto.setEnabled(
                !loading
        );

        binding.cardChangePhoto.setEnabled(
                !loading
        );

        binding.profileProgressBar.setVisibility(
                loading
                        ? View.VISIBLE
                        : View.GONE
        );

        binding.btnChangePhoto.setText(
                loading
                        ? "Processing..."
                        : "Change Photo"
        );
    }

    private void logoutUser() {
        firebaseAuth.signOut();

        showToast(
                "Logged out successfully."
        );

        openLoginPage();
    }

    private void openLoginPage() {
        if (!isAdded()) {
            return;
        }

        Intent intent =
                new Intent(
                        requireActivity(),
                        LoginActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(
                intent
        );

        requireActivity().finish();
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
    public void onResume() {
        super.onResume();

        if (
                firestore != null &&
                        firebaseAuth != null &&
                        binding != null
        ) {
            loadReportStatistics();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}