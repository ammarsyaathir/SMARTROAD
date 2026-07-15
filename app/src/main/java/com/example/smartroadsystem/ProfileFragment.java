package com.example.smartroadsystem;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.smartroadsystem.databinding.FragmentProfileBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage firebaseStorage;

    private ActivityResultLauncher<String> imagePickerLauncher;

    private boolean isUploadingPhoto = false;

    public ProfileFragment() {
        // Required empty public constructor.
    }

    @Override
    public void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        /*
         * Open Android gallery/image picker.
         *
         * The selected Uri points to the original image file,
         * so the image is uploaded without Bitmap compression.
         */
        imagePickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.GetContent(),
                        imageUri -> {

                            if (imageUri == null) {
                                return;
                            }

                            /*
                             * Display a preview before uploading.
                             */
                            if (binding != null) {
                                Glide.with(this)
                                        .load(imageUri)
                                        .centerCrop()
                                        .into(
                                                binding.ivProfileAvatar
                                        );
                            }

                            uploadProfileImage(
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

        /*
         * Automatically use the Firebase Storage bucket
         * configured in google-services.json.
         */
        firebaseStorage =
                FirebaseStorage.getInstance();
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

        /*
         * Camera icon displayed above the profile picture.
         */
        binding.cardChangePhoto
                .setOnClickListener(view ->
                        openImagePicker()
                );

        /*
         * Open report-history page.
         */
        binding.cardMyReports
                .setOnClickListener(view ->
                        openMyReportsPage()
                );

        /*
         * The report-summary card also opens report history.
         */
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
        if (isUploadingPhoto) {
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

        String userId =
                currentUser.getUid();

        displayAuthenticationEmail(
                currentUser
        );

        displayAuthenticationLastLogin(
                currentUser
        );

        binding.tvProfileName.setText(
                "Loading..."
        );

        firestore.collection("users")
                .document(userId)
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

        if (currentUser.getPhotoUrl() != null) {
            userData.put(
                    "photoUrl",
                    currentUser
                            .getPhotoUrl()
                            .toString()
            );

            userData.put(
                    "profileImageUrl",
                    currentUser
                            .getPhotoUrl()
                            .toString()
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

    private void displayAuthenticationEmail(
            @NonNull FirebaseUser currentUser
    ) {
        if (binding == null) {
            return;
        }

        String email =
                currentUser.getEmail();

        if (
                email == null ||
                        email.trim().isEmpty()
        ) {
            binding.tvProfileEmail.setText(
                    "Email unavailable"
            );
        } else {
            binding.tvProfileEmail.setText(
                    email
            );
        }
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
         * Compatibility with code that previously used
         * profileImageUrl.
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
        }
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

    private void loadProfileImage(
            @Nullable String imageUrl
    ) {
        if (
                binding == null ||
                        imageUrl == null ||
                        imageUrl.trim().isEmpty()
        ) {
            return;
        }

        /*
         * Do not use override(124, 124).
         * That could decode the image at a lower resolution.
         *
         * The original image remains stored in Firebase Storage.
         */
        Glide.with(this)
                .load(imageUrl)
                .dontAnimate()
                .centerCrop()
                .placeholder(
                        android.R.drawable.sym_def_app_icon
                )
                .error(
                        android.R.drawable.sym_def_app_icon
                )
                .skipMemoryCache(true)
                .into(
                        binding.ivProfileAvatar
                );
    }

    private void uploadProfileImage(
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

        if (isUploadingPhoto) {
            return;
        }

        setPhotoLoading(true);

        String userId =
                currentUser.getUid();

        String mimeType =
                getImageMimeType(
                        imageUri
                );

        String extension =
                getImageExtension(
                        imageUri
                );

        /*
         * Use a new filename every time.
         *
         * This avoids displaying an older cached picture.
         */
        String fileName =
                "profile_" +
                        System.currentTimeMillis() +
                        "." +
                        extension;

        StorageReference imageReference =
                firebaseStorage
                        .getReference()
                        .child("profile_images")
                        .child(userId)
                        .child(fileName);

        StorageMetadata metadata =
                new StorageMetadata.Builder()
                        .setContentType(
                                mimeType
                        )
                        .setCustomMetadata(
                                "uploadedBy",
                                userId
                        )
                        .build();

        /*
         * putFile uploads the original selected image.
         *
         * There is no Bitmap conversion and no JPEG compression,
         * so the stored image retains its original quality.
         */
        imageReference
                .putFile(
                        imageUri,
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
                .addOnSuccessListener(downloadUri -> {

                    saveProfileImageUrl(
                            currentUser,
                            downloadUri
                    );
                })
                .addOnFailureListener(exception -> {

                    setPhotoLoading(false);

                    /*
                     * Reload the previously saved photo when
                     * the new upload fails.
                     */
                    loadUserData();

                    showStorageError(
                            "Unable to upload profile image.",
                            exception
                    );
                });
    }

    @NonNull
    private String getImageMimeType(
            @NonNull Uri imageUri
    ) {
        ContentResolver contentResolver =
                requireContext()
                        .getContentResolver();

        String mimeType =
                contentResolver.getType(
                        imageUri
                );

        if (
                mimeType == null ||
                        !mimeType.startsWith("image/")
        ) {
            return "image/jpeg";
        }

        return mimeType;
    }

    @NonNull
    private String getImageExtension(
            @NonNull Uri imageUri
    ) {
        String mimeType =
                getImageMimeType(
                        imageUri
                );

        String extension =
                MimeTypeMap
                        .getSingleton()
                        .getExtensionFromMimeType(
                                mimeType
                        );

        if (
                extension == null ||
                        extension.trim().isEmpty()
        ) {
            return "jpg";
        }

        return extension;
    }

    private void saveProfileImageUrl(
            @NonNull FirebaseUser currentUser,
            @NonNull Uri downloadUri
    ) {
        String imageUrl =
                downloadUri.toString();

        Map<String, Object> profileUpdates =
                new HashMap<>();

        /*
         * Save both fields for compatibility with your
         * current and previous code.
         */
        profileUpdates.put(
                "photoUrl",
                imageUrl
        );

        profileUpdates.put(
                "profileImageUrl",
                imageUrl
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

                    updateAuthenticationPhoto(
                            currentUser,
                            downloadUri
                    );
                })
                .addOnFailureListener(exception -> {

                    setPhotoLoading(false);

                    showToast(
                            "The image was uploaded, but its URL could not be saved to Firestore: " +
                                    exception.getMessage()
                    );
                });
    }

    private void updateAuthenticationPhoto(
            @NonNull FirebaseUser currentUser,
            @NonNull Uri downloadUri
    ) {
        UserProfileChangeRequest profileUpdates =
                new UserProfileChangeRequest.Builder()
                        .setPhotoUri(
                                downloadUri
                        )
                        .build();

        currentUser
                .updateProfile(
                        profileUpdates
                )
                .addOnSuccessListener(unused -> {

                    setPhotoLoading(false);

                    loadProfileImage(
                            downloadUri.toString()
                    );

                    showToast(
                            "Profile picture updated successfully."
                    );
                })
                .addOnFailureListener(exception -> {

                    /*
                     * Storage and Firestore were already updated.
                     * Only the Firebase Authentication profile failed.
                     */
                    setPhotoLoading(false);

                    loadProfileImage(
                            downloadUri.toString()
                    );

                    showToast(
                            "Picture saved, but the authentication profile could not be updated."
                    );
                });
    }

    private void showStorageError(
            @NonNull String title,
            @NonNull Exception exception
    ) {
        if (!isAdded()) {
            return;
        }

        String message = title;

        if (exception instanceof StorageException) {
            StorageException storageException =
                    (StorageException) exception;

            message =
                    title +
                            "\nStorage code: " +
                            storageException.getErrorCode() +
                            "\n" +
                            storageException.getMessage();

        } else if (
                exception.getMessage() != null
        ) {
            message =
                    title +
                            "\n" +
                            exception.getMessage();
        }

        Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_LONG
        ).show();
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

        binding.tvMyReportsCount.setText("0");
        binding.tvResolvedCount.setText("0");
        binding.tvPendingCount.setText("0");

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
                                normalizedStatus.equals("resolved") ||
                                        normalizedStatus.equals("completed") ||
                                        normalizedStatus.equals("fixed") ||
                                        normalizedStatus.equals("closed")
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
                                    (totalReports == 1
                                            ? ""
                                            : "s") +
                                    " and status updates"
                    );
                })
                .addOnFailureListener(exception -> {

                    if (binding == null) {
                        return;
                    }

                    binding.tvMyReportsCount.setText("0");
                    binding.tvResolvedCount.setText("0");
                    binding.tvPendingCount.setText("0");

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
         * MyReportsFragment must exist before using this method.
         */
        getParentFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .replace(
                        R.id.fragment_container,
                        new MyReportsFragment()
                )
                .addToBackStack(
                        "my_reports"
                )
                .commit();
    }

    private void setPhotoLoading(
            boolean loading
    ) {
        isUploadingPhoto = loading;

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
                        ? "Uploading..."
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

        startActivity(intent);
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

        /*
         * Refresh report statistics after returning
         * from My Reports.
         */
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