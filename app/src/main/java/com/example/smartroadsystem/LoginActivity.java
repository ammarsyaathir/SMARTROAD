package com.example.smartroadsystem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartroadsystem.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private ActivityLoginBinding binding;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(
                getLayoutInflater()
        );

        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        setupGoogleSignInLauncher();
        configureGoogleSignIn();

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser != null) {
            moveToMainActivity();
            return;
        }

        setupClickListeners();
    }

    private void configureGoogleSignIn() {

        String webClientId =
                getString(R.string.default_web_client_id);

        Log.d(
                TAG,
                "Default Web Client ID: " + webClientId
        );

        GoogleSignInOptions googleSignInOptions =
                new GoogleSignInOptions.Builder(
                        GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .requestProfile()
                        .build();

        googleSignInClient =
                GoogleSignIn.getClient(
                        this,
                        googleSignInOptions
                );
    }

    private void setupGoogleSignInLauncher() {

        googleSignInLauncher =
                registerForActivityResult(
                        new ActivityResultContracts
                                .StartActivityForResult(),
                        result -> {

                            setLoading(false);

                            if (result.getData() == null) {
                                Toast.makeText(
                                        LoginActivity.this,
                                        "Google sign-in was cancelled.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            Task<GoogleSignInAccount> signInTask =
                                    GoogleSignIn
                                            .getSignedInAccountFromIntent(
                                                    result.getData()
                                            );

                            try {

                                GoogleSignInAccount account =
                                        signInTask.getResult(
                                                ApiException.class
                                        );

                                if (account == null) {
                                    Toast.makeText(
                                            LoginActivity.this,
                                            "Unable to retrieve the selected Google account.",
                                            Toast.LENGTH_LONG
                                    ).show();

                                    return;
                                }

                                String idToken =
                                        account.getIdToken();

                                if (idToken == null ||
                                        idToken.trim().isEmpty()) {

                                    Toast.makeText(
                                            LoginActivity.this,
                                            "Google ID token was not received. Check your Web Client ID, SHA-1 and SHA-256.",
                                            Toast.LENGTH_LONG
                                    ).show();

                                    return;
                                }

                                firebaseAuthWithGoogle(account);

                            } catch (ApiException exception) {

                                Log.e(
                                        TAG,
                                        "Google Sign-In failed. Status code: "
                                                + exception.getStatusCode(),
                                        exception
                                );

                                showGoogleSignInError(
                                        exception.getStatusCode()
                                );
                            }
                        }
                );
    }

    private void setupClickListeners() {

        binding.btnLogin.setOnClickListener(view ->
                validateAndLoginWithEmail()
        );

        binding.btnGoogleSignIn.setOnClickListener(view ->
                startGoogleSignIn()
        );

        binding.tvForgotPassword.setOnClickListener(view ->
                resetPassword()
        );

        binding.tvRegister.setOnClickListener(view -> {

            Intent intent = new Intent(
                    LoginActivity.this,
                    RegisterActivity.class
            );

            startActivity(intent);
        });
    }

    private void validateAndLoginWithEmail() {

        String email =
                binding.etEmail.getText() == null
                        ? ""
                        : binding.etEmail
                        .getText()
                        .toString()
                        .trim();

        String password =
                binding.etPassword.getText() == null
                        ? ""
                        : binding.etPassword
                        .getText()
                        .toString();

        binding.emailInputLayout.setError(null);
        binding.passwordInputLayout.setError(null);

        if (email.isEmpty()) {

            binding.emailInputLayout.setError(
                    "Please enter your email address."
            );

            binding.etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns
                .EMAIL_ADDRESS
                .matcher(email)
                .matches()) {

            binding.emailInputLayout.setError(
                    "Please enter a valid email address."
            );

            binding.etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {

            binding.passwordInputLayout.setError(
                    "Please enter your password."
            );

            binding.etPassword.requestFocus();
            return;
        }

        loginUser(email, password);
    }

    private void loginUser(
            String email,
            String password
    ) {

        setLoading(true);

        firebaseAuth
                .signInWithEmailAndPassword(
                        email,
                        password
                )
                .addOnCompleteListener(
                        this,
                        task -> {

                            if (!task.isSuccessful()) {

                                setLoading(false);

                                String errorMessage =
                                        task.getException() == null
                                                ? "Unable to log in."
                                                : task.getException()
                                                .getMessage();

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Login failed: "
                                                + errorMessage,
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            FirebaseUser firebaseUser =
                                    firebaseAuth.getCurrentUser();

                            if (firebaseUser == null) {

                                setLoading(false);

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Unable to retrieve user information.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            updateLastLoginAndContinue(
                                    firebaseUser.getUid()
                            );
                        }
                );
    }

    private void startGoogleSignIn() {

        if (googleSignInClient == null) {

            Toast.makeText(
                    this,
                    "Google Sign-In is not configured.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        setLoading(true);

        /*
         * Clear any cached Google account while testing.
         * This forces the account chooser to appear.
         */
        googleSignInClient
                .signOut()
                .addOnCompleteListener(task -> {

                    Intent signInIntent =
                            googleSignInClient
                                    .getSignInIntent();

                    googleSignInLauncher.launch(
                            signInIntent
                    );
                });
    }

    private void firebaseAuthWithGoogle(
            GoogleSignInAccount googleAccount
    ) {

        String idToken =
                googleAccount.getIdToken();

        if (idToken == null ||
                idToken.trim().isEmpty()) {

            setLoading(false);

            Toast.makeText(
                    this,
                    "Google ID token is missing.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        setLoading(true);

        AuthCredential credential =
                GoogleAuthProvider.getCredential(
                        idToken,
                        null
                );

        firebaseAuth
                .signInWithCredential(credential)
                .addOnCompleteListener(
                        this,
                        task -> {

                            if (!task.isSuccessful()) {

                                setLoading(false);

                                String errorMessage =
                                        task.getException() == null
                                                ? "Unknown authentication error."
                                                : task.getException()
                                                .getMessage();

                                Log.e(
                                        TAG,
                                        "Firebase Google authentication failed.",
                                        task.getException()
                                );

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Google authentication failed: "
                                                + errorMessage,
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            FirebaseUser firebaseUser =
                                    firebaseAuth.getCurrentUser();

                            if (firebaseUser == null) {

                                setLoading(false);

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Firebase user information was not received.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            checkAndCreateFirestoreUser(
                                    firebaseUser,
                                    googleAccount
                            );
                        }
                );
    }

    private void checkAndCreateFirestoreUser(
            FirebaseUser firebaseUser,
            GoogleSignInAccount googleAccount
    ) {

        String uid =
                firebaseUser.getUid();

        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        String name =
                                documentSnapshot
                                        .getString("name");

                        if (name == null ||
                                name.trim().isEmpty()) {

                            name =
                                    firebaseUser
                                            .getDisplayName();
                        }

                        String finalName =
                                normalizeName(name);

                        Map<String, Object> updates =
                                new HashMap<>();

                        updates.put(
                                "lastLoginAt",
                                Timestamp.now()
                        );

                        if (firebaseUser.getEmail() != null) {
                            updates.put(
                                    "email",
                                    firebaseUser.getEmail()
                            );
                        }

                        firestore.collection("users")
                                .document(uid)
                                .set(
                                        updates,
                                        SetOptions.merge()
                                )
                                .addOnCompleteListener(task -> {

                                    setLoading(false);

                                    showWelcomeMessage(
                                            finalName,
                                            false
                                    );

                                    moveToMainActivity();
                                });

                        return;
                    }

                    createGoogleUserProfile(
                            firebaseUser,
                            googleAccount
                    );
                })
                .addOnFailureListener(exception -> {

                    setLoading(false);

                    Log.e(
                            TAG,
                            "Unable to check Firestore user.",
                            exception
                    );

                    Toast.makeText(
                            LoginActivity.this,
                            "Unable to load the user profile: "
                                    + exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void createGoogleUserProfile(
            FirebaseUser firebaseUser,
            GoogleSignInAccount googleAccount
    ) {

        String uid =
                firebaseUser.getUid();

        String name =
                googleAccount.getDisplayName();

        if (name == null ||
                name.trim().isEmpty()) {

            name =
                    firebaseUser.getDisplayName();
        }

        name = normalizeName(name);

        String email =
                googleAccount.getEmail();

        if (email == null ||
                email.trim().isEmpty()) {

            email =
                    firebaseUser.getEmail();
        }

        String photoUrl = "";

        if (googleAccount.getPhotoUrl() != null) {

            photoUrl =
                    googleAccount
                            .getPhotoUrl()
                            .toString();

        } else if (firebaseUser.getPhotoUrl() != null) {

            photoUrl =
                    firebaseUser
                            .getPhotoUrl()
                            .toString();
        }

        Map<String, Object> userData =
                new HashMap<>();

        userData.put("uid", uid);
        userData.put("name", name);
        userData.put("email", email);
        userData.put("photoUrl", photoUrl);
        userData.put("provider", "google");
        userData.put("role", "user");
        userData.put(
                "createdAt",
                Timestamp.now()
        );
        userData.put(
                "lastLoginAt",
                Timestamp.now()
        );

        String finalName = name;

        firestore.collection("users")
                .document(uid)
                .set(
                        userData,
                        SetOptions.merge()
                )
                .addOnSuccessListener(unused -> {

                    setLoading(false);

                    showWelcomeMessage(
                            finalName,
                            true
                    );

                    moveToMainActivity();
                })
                .addOnFailureListener(exception -> {

                    setLoading(false);

                    Log.e(
                            TAG,
                            "Unable to save Google user profile.",
                            exception
                    );

                    Toast.makeText(
                            LoginActivity.this,
                            "Google login succeeded, but the user profile could not be saved: "
                                    + exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void updateLastLoginAndContinue(
            String uid
    ) {

        Map<String, Object> updates =
                new HashMap<>();

        updates.put(
                "lastLoginAt",
                Timestamp.now()
        );

        firestore.collection("users")
                .document(uid)
                .set(
                        updates,
                        SetOptions.merge()
                )
                .addOnCompleteListener(task ->
                        fetchUserProfile(uid)
                );
    }

    private void fetchUserProfile(
            String uid
    ) {

        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    setLoading(false);

                    String name = "User";

                    if (documentSnapshot.exists()) {

                        String storedName =
                                documentSnapshot
                                        .getString("name");

                        name =
                                normalizeName(storedName);
                    }

                    showWelcomeMessage(
                            name,
                            false
                    );

                    moveToMainActivity();
                })
                .addOnFailureListener(exception -> {

                    setLoading(false);

                    Log.e(
                            TAG,
                            "Unable to retrieve user profile.",
                            exception
                    );

                    moveToMainActivity();
                });
    }

    private void resetPassword() {

        String email =
                binding.etEmail.getText() == null
                        ? ""
                        : binding.etEmail
                        .getText()
                        .toString()
                        .trim();

        binding.emailInputLayout.setError(null);

        if (email.isEmpty()) {

            binding.emailInputLayout.setError(
                    "Enter your email address first."
            );

            binding.etEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns
                .EMAIL_ADDRESS
                .matcher(email)
                .matches()) {

            binding.emailInputLayout.setError(
                    "Please enter a valid email address."
            );

            binding.etEmail.requestFocus();
            return;
        }

        setLoading(true);

        firebaseAuth
                .sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {

                    setLoading(false);

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                LoginActivity.this,
                                "Password reset instructions were sent to "
                                        + email,
                                Toast.LENGTH_LONG
                        ).show();

                    } else {

                        String errorMessage =
                                task.getException() == null
                                        ? "Unable to send the reset email."
                                        : task.getException()
                                        .getMessage();

                        Toast.makeText(
                                LoginActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void showGoogleSignInError(
            int statusCode
    ) {

        setLoading(false);

        String message;

        if (statusCode == 10) {

            message =
                    "Google configuration error (Code 10).\n\n"
                            + "1. Run .\\gradlew signingReport\n"
                            + "2. Add the DEBUG SHA-1 and SHA-256 to Firebase\n"
                            + "3. Download a new google-services.json\n"
                            + "4. Replace app/google-services.json\n"
                            + "5. Uninstall and reinstall SMARTROAD";

        } else if (statusCode == 12501) {

            message =
                    "Google sign-in was cancelled.";

        } else if (statusCode == 7) {

            message =
                    "Network error. Check your internet connection.";

        } else {

            message =
                    "Google sign-in failed. Error code: "
                            + statusCode;
        }

        Toast.makeText(
                LoginActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private String normalizeName(
            String name
    ) {

        if (name == null ||
                name.trim().isEmpty()) {

            return "SMARTROAD User";
        }

        return name.trim();
    }

    private void showWelcomeMessage(
            String name,
            boolean newAccount
    ) {

        name = normalizeName(name);

        String message =
                newAccount
                        ? "Account created successfully. Welcome, "
                        + name + "!"
                        : "Welcome back, "
                        + name + "!";

        Toast.makeText(
                LoginActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void setLoading(
            boolean loading
    ) {

        if (binding == null) {
            return;
        }

        binding.btnLogin.setEnabled(!loading);
        binding.btnGoogleSignIn.setEnabled(!loading);
        binding.tvRegister.setEnabled(!loading);
        binding.tvForgotPassword.setEnabled(!loading);

        binding.btnLogin.setText(
                loading
                        ? "Please wait..."
                        : "Login"
        );
    }

    private void moveToMainActivity() {

        Intent intent = new Intent(
                LoginActivity.this,
                MainActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}