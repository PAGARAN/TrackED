package com.example.tracked;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.api.services.tasks.TasksScopes;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private ProgressBar progressBar;
    private View googleSignInContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign In with minimal scopes
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                // Remove TasksScopes if possible during testing
                // .requestScopes(new Scope(TasksScopes.TASKS))
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize views
        progressBar = findViewById(R.id.loginProgress);
        googleSignInContainer = findViewById(R.id.googleSignInContainer);
        
        // Set click listener for Google Sign In button
        googleSignInContainer.setOnClickListener(v -> signIn());
        
        // Log the web client ID to verify it's correct
        Log.d(TAG, "Web client ID: " + getString(R.string.default_web_client_id));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in
        if (mAuth.getCurrentUser() != null) {
            Log.d(TAG, "User is already signed in: " + mAuth.getCurrentUser().getEmail());
            startDashboardActivity();
        }
    }

    private void signIn() {
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        googleSignInContainer.setEnabled(false);

        // Create a simple sign-in intent
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google sign in succeeded");
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed: " + e.getStatusCode(), e);
                progressBar.setVisibility(View.GONE);
                googleSignInContainer.setEnabled(true);
                String errorMessage = "Google Sign In failed: " + e.getStatusCode();
                if (e.getStatusCode() == 12501) {
                    errorMessage = "Sign in was canceled. Please try again.";
                } else if (e.getStatusCode() == 7) {
                    errorMessage = "Network error. Please check your internet connection.";
                } else if (e.getStatusCode() == 10) {
                    errorMessage = "Developer error. Please contact support.";
                } else if (e.getStatusCode() == 12) {
                    errorMessage = "Sign in currently in progress. Please try again.";
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null) {
            Log.e(TAG, "ID Token is null");
            Toast.makeText(this, "Authentication failed: No ID token", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            googleSignInContainer.setEnabled(true);
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    googleSignInContainer.setEnabled(true);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        startDashboardActivity();
                    } else {
                        Log.e(TAG, "signInWithCredential:failure", task.getException());
                        String errorMessage = "Authentication Failed: " + 
                            (task.getException() != null ? task.getException().getMessage() : "Unknown error");
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startDashboardActivity() {
        try {
            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error starting DashboardActivity", e);
            Toast.makeText(this, "Error launching dashboard: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
























