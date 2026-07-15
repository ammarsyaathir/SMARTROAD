package com.example.smartroadsystem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartroadsystem.databinding.ActivityRegisterBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        binding.btnRegister.setOnClickListener(view ->
                validateAndRegister()
        );

        binding.tvBackToLogin.setOnClickListener(view ->
                finish()
        );
    }

    private void validateAndRegister() {

        String name = binding.etName.getText() == null
                ? ""
                : binding.etName.getText().toString().trim();

        String email = binding.etEmail.getText() == null
                ? ""
                : binding.etEmail.getText().toString().trim();

        String password = binding.etPassword.getText() == null
                ? ""
                : binding.etPassword.getText().toString();

        String confirmPassword =
                binding.etConfirmPassword.getText() == null
                        ? ""
                        : binding.etConfirmPassword.getText().toString();

        // Remove previous errors
        binding.nameInputLayout.setError(null);
        binding.emailInputLayout.setError(null);
        binding.passwordInputLayout.setError(null);
        binding.confirmPasswordInputLayout.setError(null);

        if (name.isEmpty()) {
            binding.nameInputLayout.setError(
                    "Please enter your full name."
            );
            binding.etName.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            binding.emailInputLayout.setError(
                    "Please enter your email address."
            );
            binding.etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(
                    "Please enter a valid email address."
            );
            binding.etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            binding.passwordInputLayout.setError(
                    "Please enter a password."
            );
            binding.etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            binding.passwordInputLayout.setError(
                    "Password must contain at least 6 characters."
            );
            binding.etPassword.requestFocus();
            return;
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.setError(
                    "Please confirm your password."
            );
            binding.etConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            binding.confirmPasswordInputLayout.setError(
                    "Passwords do not match."
            );
            binding.etConfirmPassword.requestFocus();
            return;
        }

        registerUser(name, email, password);
    }

    private void registerUser(
            String name,
            String email,
            String password
    ) {

        setLoading(true);

        firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        setLoading(false);

                        String errorMessage;

                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        } else {
                            errorMessage = "Registration failed.";
                        }

                        Toast.makeText(
                                RegisterActivity.this,
                                "Registration failed: " + errorMessage,
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    FirebaseUser firebaseUser =
                            firebaseAuth.getCurrentUser();

                    if (firebaseUser == null) {
                        setLoading(false);

                        Toast.makeText(
                                RegisterActivity.this,
                                "Account was created, but user information could not be retrieved.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    saveUserProfile(
                            firebaseUser.getUid(),
                            name,
                            email
                    );
                });
    }

    private void saveUserProfile(
            String uid,
            String name,
            String email
    ) {

        Map<String, Object> userData = new HashMap<>();

        userData.put("uid", uid);
        userData.put("name", name);
        userData.put("email", email);
        userData.put("photoUrl", "");
        userData.put("provider", "password");
        userData.put("role", "user");
        userData.put("createdAt", Timestamp.now());
        userData.put("lastLoginAt", Timestamp.now());

        firestore.collection("users")
                .document(uid)
                .set(userData)
                .addOnSuccessListener(unused -> {

                    setLoading(false);

                    /*
                     * Firebase automatically signs in a newly registered user.
                     * Sign out so the user can return to the Login page.
                     */
                    firebaseAuth.signOut();

                    Toast.makeText(
                            RegisterActivity.this,
                            "Account registered successfully. Please log in.",
                            Toast.LENGTH_LONG
                    ).show();

                    Intent intent = new Intent(
                            RegisterActivity.this,
                            LoginActivity.class
                    );

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    );

                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(exception -> {

                    setLoading(false);

                    Toast.makeText(
                            RegisterActivity.this,
                            "Account was created, but the profile could not be saved: "
                                    + exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void setLoading(boolean loading) {

        if (binding == null) {
            return;
        }

        binding.btnRegister.setEnabled(!loading);
        binding.tvBackToLogin.setEnabled(!loading);

        binding.etName.setEnabled(!loading);
        binding.etEmail.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
        binding.etConfirmPassword.setEnabled(!loading);

        binding.btnRegister.setText(
                loading
                        ? "Creating account..."
                        : "Register"
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}