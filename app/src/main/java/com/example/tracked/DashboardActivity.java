package com.example.tracked;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.example.tracked.api.WeatherApi;
import com.example.tracked.api.ZenQuotesApi;
import com.example.tracked.model.Quote;
import com.example.tracked.model.Weather;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.ProgressDialog;

public class DashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "DashboardActivity";
    private static final String WEATHER_API_KEY = "143810e7f766db7393b7d4cfc39f6321"; // Replace with a valid API key from OpenWeatherMap
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    private DrawerLayout drawerLayout;
    private TextView quoteText;
    private TextView quoteAuthor;
    private ProgressBar quoteProgress;
    private ZenQuotesApi zenQuotesApi;
    private View addTaskOverlay;
    private boolean isOverlayVisible = false;
    private WeatherApi weatherApi;
    private ProgressBar weatherProgress;
    private ImageView weatherIcon;
    private TextView temperatureText;
    private TextView weatherDescription;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private void initializeLocation() {
        // Initialize location request
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setIntervalMillis(10000)  // 10 seconds
            .setMinUpdateIntervalMillis(5000) // 5 seconds
            .build();

        // Initialize location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.e(TAG, "Location result is null");
                    showWeatherError("Location not available");
                    return;
                }
                android.location.Location location = locationResult.getLastLocation();
                if (location != null) {
                    Log.d(TAG, "New location obtained: " + location.getLatitude() + ", " + location.getLongitude());
                    fetchWeatherWithLocation(location);
                    // Remove location updates after getting location
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                }
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Add back button handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isOverlayVisible) {
                    hideAddTaskOverlay();
                } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Initialize views
        quoteText = findViewById(R.id.quoteText);
        quoteAuthor = findViewById(R.id.quoteAuthor);
        quoteProgress = findViewById(R.id.quoteProgress);
        weatherProgress = findViewById(R.id.weatherProgress);
        weatherIcon = findViewById(R.id.weatherIcon);
        temperatureText = findViewById(R.id.temperatureText);
        weatherDescription = findViewById(R.id.weatherDescription);

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        initializeLocation();

        // Set up logging interceptor
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Set up OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        // Set up Retrofit for both APIs
        Retrofit quoteRetrofit = new Retrofit.Builder()
            .baseUrl("https://zenquotes.io")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        Retrofit weatherRetrofit = new Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        zenQuotesApi = quoteRetrofit.create(ZenQuotesApi.class);
        weatherApi = weatherRetrofit.create(WeatherApi.class);

        // Set up the drawer layout
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Set up menu button
        ImageButton menuButton = findViewById(R.id.menuButton);
        menuButton.setOnClickListener(v -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // Initialize the overlay
        addTaskOverlay = getLayoutInflater().inflate(R.layout.overlay_add_task, null);
        ((ViewGroup) findViewById(android.R.id.content)).addView(addTaskOverlay);
        addTaskOverlay.setVisibility(View.GONE);

        // Setup task type dropdown
        setupTaskTypeDropdown();

        // Set up FAB
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, AddTaskActivity.class);
            startActivity(intent);
        });

        // Set up close button
        ImageButton closeButton = addTaskOverlay.findViewById(R.id.closeOverlayButton);
        closeButton.setOnClickListener(v -> hideAddTaskOverlay());

        // Set up add task button
        MaterialButton addTaskButton = addTaskOverlay.findViewById(R.id.addTaskButton);
        addTaskButton.setOnClickListener(v -> {
            TextInputEditText titleInput = addTaskOverlay.findViewById(R.id.taskTitleInput);
            TextInputEditText descriptionInput = addTaskOverlay.findViewById(R.id.taskDescriptionInput);
            
            String title = titleInput.getText().toString();
            String description = descriptionInput.getText().toString();
            
            if (!title.isEmpty()) {
                // Add your task creation logic here
                hideAddTaskOverlay();
                // Clear inputs
                titleInput.setText("");
                descriptionInput.setText("");
            } else {
                titleInput.setError("Title is required");
            }
        });

        // Set up date picker
        MaterialButton datePickerButton = addTaskOverlay.findViewById(R.id.datePickerButton);
        datePickerButton.setOnClickListener(v -> {
            // Show date picker dialog
            // Add your date picker logic here
        });

        // Set up priority button
        MaterialButton priorityButton = addTaskOverlay.findViewById(R.id.priorityButton);
        priorityButton.setOnClickListener(v -> {
            // Show priority selection dialog
            // Add your priority selection logic here
        });

        // Initialize other UI components and fetch data
        fetchQuote();
        checkLocationPermissionAndFetchWeather();
    }

    private void setupTaskTypeDropdown() {
        String[] taskTypes = new String[]{"Assignment", "Quiz", "Exam", "Project", "Reading", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            R.layout.dropdown_item,
            taskTypes
        );

        AutoCompleteTextView taskTypeDropdown = addTaskOverlay.findViewById(R.id.taskTypeDropdown);
        taskTypeDropdown.setAdapter(adapter);
        taskTypeDropdown.setText(taskTypes[0], false); // Set default selection
    }

    private void showAddTaskOverlay() {
        addTaskOverlay.setVisibility(View.VISIBLE);
        // Optional: Add animation or dim background
        View rootView = findViewById(android.R.id.content);
        rootView.setAlpha(0.7f); // Dim the background
    }

    private void hideAddTaskOverlay() {
        addTaskOverlay.setVisibility(View.GONE);
        // Optional: Remove animation or dim
        View rootView = findViewById(android.R.id.content);
        rootView.setAlpha(1.0f); // Restore background
    }

    private void fetchQuote() {
        quoteProgress.setVisibility(View.VISIBLE);
        quoteText.setVisibility(View.GONE);
        quoteAuthor.setVisibility(View.GONE);

        zenQuotesApi.getTodayQuote().enqueue(new Callback<List<Quote>>() {
            @Override
            public void onResponse(Call<List<Quote>> call, Response<List<Quote>> response) {
                quoteProgress.setVisibility(View.GONE);
                quoteText.setVisibility(View.VISIBLE);
                quoteAuthor.setVisibility(View.VISIBLE);

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Quote quote = response.body().get(0);
                    Log.d(TAG, "Quote received: " + quote.getText());
                    quoteText.setText(quote.getText());
                    quoteAuthor.setText("- " + quote.getAuthor());
                } else {
                    String errorBody = "";
                    try {
                        if (response.errorBody() != null) {
                            errorBody = response.errorBody().string();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                    }
                    Log.e(TAG, "Failed to load quote. Code: " + response.code() + 
                              " Error: " + errorBody);
                    quoteText.setText("Failed to load quote. Please try again later.");
                    quoteAuthor.setText("");
                }
            }

            @Override
            public void onFailure(Call<List<Quote>> call, Throwable t) {
                Log.e(TAG, "Error loading quote", t);
                quoteProgress.setVisibility(View.GONE);
                quoteText.setVisibility(View.VISIBLE);
                quoteAuthor.setVisibility(View.VISIBLE);
                quoteText.setText("Error loading quote. Please check your internet connection.");
                quoteAuthor.setText("");
            }
        });
    }

    private void checkLocationPermissionAndFetchWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission not granted, requesting permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "Location permission already granted, requesting location updates");
            requestLocationUpdates();
        }
    }

    private void requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted in requestLocationUpdates");
            return;
        }

        Log.d(TAG, "Starting location request");
        // First try to get last known location
        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(this, location -> {
                if (location != null) {
                    Log.d(TAG, "Last known location obtained: " + location.getLatitude() + ", " + location.getLongitude());
                    fetchWeatherWithLocation(location);
                } else {
                    Log.d(TAG, "Last location null, requesting updates");
                    // If last known location is null, request location updates
                    try {
                        fusedLocationClient.requestLocationUpdates(locationRequest,
                                locationCallback,
                                Looper.getMainLooper())
                                .addOnSuccessListener(unused -> Log.d(TAG, "Location updates request successful"))
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Location updates request failed", e);
                                    showWeatherError("Location service error");
                                });
                    } catch (SecurityException e) {
                        Log.e(TAG, "Security exception when requesting location updates", e);
                        showWeatherError("Location permission error");
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting last location", e);
                showWeatherError("Failed to get location");
            });
    }

    private void fetchWeatherWithLocation(android.location.Location location) {
        weatherProgress.setVisibility(View.VISIBLE);
        weatherIcon.setVisibility(View.GONE);
        temperatureText.setVisibility(View.GONE);
        weatherDescription.setVisibility(View.GONE);

        Log.d(TAG, "Fetching weather for location: " + location.getLatitude() + ", " + location.getLongitude());

        Call<Weather> call = weatherApi.getCurrentWeather(
            location.getLatitude(),
            location.getLongitude(),
            WEATHER_API_KEY,
            "metric"
        );

        Log.d(TAG, "Weather API request URL: " + call.request().url());

        call.enqueue(new Callback<Weather>() {
            @Override
            public void onResponse(Call<Weather> call, Response<Weather> response) {
                Log.d(TAG, "Weather API response code: " + response.code());
                
                if (!response.isSuccessful()) {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                        Log.e(TAG, "Weather API error response: " + errorBody);
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                    }
                }

                weatherProgress.setVisibility(View.GONE);
                weatherIcon.setVisibility(View.VISIBLE);
                temperatureText.setVisibility(View.VISIBLE);
                weatherDescription.setVisibility(View.VISIBLE);

                if (response.isSuccessful() && response.body() != null) {
                    Weather weather = response.body();
                    Log.d(TAG, "Weather data received: " + weather.getMain().getTemperature() + "°C");
                    updateWeatherUI(weather);
                } else {
                    showWeatherError("Unable to fetch weather data");
                }
            }

            @Override
            public void onFailure(Call<Weather> call, Throwable t) {
                Log.e(TAG, "Weather API call failed", t);
                weatherProgress.setVisibility(View.GONE);
                showWeatherError("Network error: " + t.getMessage());
            }
        });
    }

    private void updateWeatherUI(Weather weather) {
        try {
            double temperature = weather.getMain().getTemperature();
            String description = weather.getWeather()[0].getDescription();
            String iconCode = weather.getWeather()[0].getIcon();
            
            Log.d(TAG, "Updating UI with temperature: " + temperature + 
                      "°C, description: " + description + 
                      ", icon: " + iconCode);

            temperatureText.setText(String.format(Locale.getDefault(), "%.1f°C", temperature));
            weatherDescription.setText(description);
            
            String iconUrl = String.format("https://openweathermap.org/img/w/%s.png", iconCode);
            Glide.with(this)
                .load(iconUrl)
                .error(android.R.drawable.ic_menu_help)
                .into(weatherIcon);
                
            Log.d(TAG, "Weather UI updated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error updating weather UI", e);
            showWeatherError("Error displaying weather");
        }
    }

    private void showWeatherError(String errorMessage) {
        weatherIcon.setVisibility(View.VISIBLE);
        temperatureText.setVisibility(View.VISIBLE);
        weatherDescription.setVisibility(View.VISIBLE);
        
        temperatureText.setText("--°C");
        weatherDescription.setText(errorMessage);
        weatherIcon.setImageResource(android.R.drawable.ic_menu_help);
        Log.e(TAG, "Weather error: " + errorMessage);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted");
                requestLocationUpdates();
            } else {
                Log.e(TAG, "Location permission denied");
                showWeatherError("Location permission required");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Handle home action
        } else if (id == R.id.nav_calendar) {
            // Handle calendar action
        } else if (id == R.id.nav_tasks) {
            // Handle tasks action
        } else if (id == R.id.nav_news) {
            // Handle news action
        } else if (id == R.id.nav_profile) {
            // Handle profile action
        } else if (id == R.id.nav_settings) {
            // Handle settings action
        } else if (id == R.id.nav_logout) {
            signOut();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void signOut() {
        // Show a confirmation dialog
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Show progress dialog
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Signing out...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    // Sign out from Google
                    mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                        // Sign out from Firebase
                        mAuth.signOut();
                        progressDialog.dismiss();
                        
                        // Clear any stored user data if needed
                        // ...

                        // Return to login screen
                        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                })
                .setNegativeButton("No", null)
                .show();
    }
}
























