package com.example.tracked;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DURATION = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_splash);

            // Initialize Firebase
            FirebaseApp.initializeApp(this);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // Check if user is already signed in
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    Intent intent;
                    
                    if (currentUser != null) {
                        // User is signed in, go to Dashboard
                        intent = new Intent(SplashActivity.this, DashboardActivity.class);
                    } else {
                        // No user signed in, go to Login
                        intent = new Intent(SplashActivity.this, LoginActivity.class);
                    }
                    
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "Error in splash navigation", e);
                    Toast.makeText(this, "Error starting app. Please try again.", Toast.LENGTH_LONG).show();
                }
            }, SPLASH_DURATION);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing app. Please try again.", Toast.LENGTH_LONG).show();
        }
    }
}


