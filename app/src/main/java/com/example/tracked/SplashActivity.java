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

            // Initialize Firebase in background thread
            new Thread(() -> {
                try {
                    FirebaseApp.initializeApp(getApplicationContext());
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing Firebase", e);
                }
            }).start();

            // Handle navigation after splash duration
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    Intent intent;
                    
                    if (currentUser != null) {
                        intent = new Intent(SplashActivity.this, DashboardActivity.class);
                    } else {
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



