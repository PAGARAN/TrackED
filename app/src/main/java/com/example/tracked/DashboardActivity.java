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
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

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
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.ProgressDialog;
import com.google.api.services.tasks.model.Task;
import com.example.tracked.api.TasksApiService;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.tasks.TasksScopes;
import com.google.android.gms.common.api.Scope;

public class DashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "DashboardActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int ADD_TASK_REQUEST_CODE = 1003;
    private static final int REQUEST_AUTHORIZATION = 1002;
    private static final int RC_SIGN_IN = 9001;
    
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
    private TasksApiService tasksApiService;
    private RecyclerView taskRecyclerView;
    private TaskAdapter taskAdapter;
    private boolean isActivityActive = false;
    private RecyclerView tasksRecyclerView;
    private TextView emptyTasksView;
    private ImageButton menuButton;

    private void initializeLocation() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setIntervalMillis(300000)  // 5 minutes
            .setMinUpdateIntervalMillis(180000) // 3 minutes
            .setMaxUpdates(1) // Get only one update
            .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || locationResult.getLastLocation() == null) {
                    showWeatherError("Location not available");
                    return;
                }
                fetchWeatherWithLocation(locationResult.getLastLocation());
                // Remove location updates after getting the location
                if (fusedLocationClient != null) {
                    fusedLocationClient.removeLocationUpdates(this);
                }
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize views first
        drawerLayout = findViewById(R.id.drawer_layout);
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView);
        emptyTasksView = findViewById(R.id.emptyTasksView);
        menuButton = findViewById(R.id.menuButton);

        // Set up RecyclerView
        taskAdapter = new TaskAdapter();
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tasksRecyclerView.setAdapter(taskAdapter);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestScopes(new Scope(TasksScopes.TASKS))
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Set up navigation drawer
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Set up menu button
        menuButton.setOnClickListener(v -> {
            if (drawerLayout != null) {
                if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            }
        });

        // Set up FAB
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AddTaskActivity.class);
                startActivityForResult(intent, ADD_TASK_REQUEST_CODE);
            });
        }

        // Initialize user info
        if (mAuth.getCurrentUser() != null) {
            String userEmail = mAuth.getCurrentUser().getEmail();
            TextView userEmailView = findViewById(R.id.userEmail);
            if (userEmailView != null) {
                userEmailView.setText(userEmail);
            }
            initializeTasksApiService(userEmail);
        }

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

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

        // Initialize the overlay
        addTaskOverlay = getLayoutInflater().inflate(R.layout.overlay_add_task, null);
        ((ViewGroup) findViewById(android.R.id.content)).addView(addTaskOverlay);
        addTaskOverlay.setVisibility(View.GONE);

        // Setup task type dropdown
        setupTaskTypeDropdown();

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
            }
        });

        // Start fetching data
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

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            
            // Show a dialog explaining why we need location permission
            new MaterialAlertDialogBuilder(this)
                .setTitle("Location Permission Required")
                .setMessage("This app needs location permission to show weather information for your area. Please grant location permission to continue.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                        LOCATION_PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    showWeatherError("Weather information requires location permission");
                })
                .setCancelable(false)
                .show();
        } else {
            // Permission already granted, proceed with location updates
            requestLocationUpdates();
        }
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
                showWeatherError("Please enable location permission in Settings to see weather information");
                // Show a dialog with instructions to enable permission in settings
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("To see weather information, please enable location permission in Settings.")
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Not Now", null)
                    .show();
            }
        }
    }

    private void checkLocationPermissionAndFetchWeather() {
        requestLocationPermission();
    }

    private void requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted in requestLocationUpdates");
            showWeatherError("Location permission required");
            // Show a dialog explaining why we need location permission
            new MaterialAlertDialogBuilder(this)
                .setTitle("Location Permission Required")
                .setMessage("This app needs location permission to show weather information for your area. Please grant location permission to continue.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    showWeatherError("Weather information requires location permission");
                })
                .setCancelable(false)
                .show();
            return;
        }

        Log.d(TAG, "Starting location request");
        // First try to get last known location
        try {
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
                                        showWeatherError("Location service error. Please try again later.");
                                    });
                        } catch (SecurityException e) {
                            Log.e(TAG, "Security exception when requesting location updates", e);
                            showWeatherError("Location permission error. Please grant location permission in Settings.");
                            // Show a dialog with instructions to enable permission in settings
                            new MaterialAlertDialogBuilder(this)
                                .setTitle("Location Permission Required")
                                .setMessage("To see weather information, please enable location permission in Settings.")
                                .setPositiveButton("Open Settings", (dialog, which) -> {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                                    startActivity(intent);
                                })
                                .setNegativeButton("Not Now", null)
                                .show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting last location", e);
                    showWeatherError("Failed to get location. Please try again later.");
                });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when getting last location", e);
            showWeatherError("Location permission error. Please grant location permission in Settings.");
            // Show a dialog with instructions to enable permission in settings
            new MaterialAlertDialogBuilder(this)
                .setTitle("Location Permission Required")
                .setMessage("To see weather information, please enable location permission in Settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Not Now", null)
                .show();
        }
    }

    private void fetchWeatherWithLocation(android.location.Location location) {
        if (!isActivityActive) return;

        String apiKey = getString(R.string.weather_api_key);
        weatherApi.getCurrentWeather(
            location.getLatitude(),
            location.getLongitude(),
            apiKey,
            "metric"
        ).enqueue(new Callback<Weather>() {
            @Override
            public void onResponse(Call<Weather> call, Response<Weather> response) {
                if (!isActivityActive) return;

                if (response.isSuccessful() && response.body() != null) {
                    updateWeatherUI(response.body());
                } else {
                    showWeatherError("Failed to get weather data");
                }
            }

            @Override
            public void onFailure(Call<Weather> call, Throwable t) {
                if (!isActivityActive) return;
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

            // Hide loading animation
            weatherProgress.setVisibility(View.GONE);
            
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
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (tasksApiService != null) {
            loadTasks();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_TASK_REQUEST_CODE && resultCode == RESULT_OK) {
            // Refresh task list when a new task is added
            loadTasks();
        }
        if (requestCode == REQUEST_AUTHORIZATION) {
            if (resultCode == RESULT_OK) {
                // Retry fetching tasks after authorization
                loadTasks();
            } else {
                Toast.makeText(this, "Authorization required to access tasks", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadTasks() {
        if (tasksApiService == null) {
            Log.e(TAG, "TasksApiService is null");
            return;
        }

        tasksApiService.getTasks(new TasksApiService.TaskListCallback() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (!isActivityActive) return;
                
                runOnUiThread(() -> {
                    if (tasks.isEmpty()) {
                        emptyTasksView.setVisibility(View.VISIBLE);
                        tasksRecyclerView.setVisibility(View.GONE);
                    } else {
                        emptyTasksView.setVisibility(View.GONE);
                        tasksRecyclerView.setVisibility(View.VISIBLE);
                        taskAdapter.setTasks(tasks);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (!isActivityActive) return;
                
                runOnUiThread(() -> {
                    Log.e(TAG, "Error loading tasks", e);
                    Toast.makeText(DashboardActivity.this, 
                        "Error loading tasks: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksApiService != null) {
            tasksApiService.shutdown();
        }
        // Clean up resources
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        // Clear references
        weatherApi = null;
        zenQuotesApi = null;
        taskAdapter = null;
        locationCallback = null;
    }

    private void checkTasksApiAccess() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getGrantedScopes().contains(new Scope(TasksScopes.TASKS))) {
            // We have proper access
            initializeTasksApiService(account.getEmail());
        } else {
            // Need to request access
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(new Scope(TasksScopes.TASKS))
                    .build();
            GoogleSignInClient signInClient = GoogleSignIn.getClient(this, gso);
            startActivityForResult(signInClient.getSignInIntent(), REQUEST_AUTHORIZATION);
        }
    }

    private void testTasksApi() {
        if (tasksApiService != null) {
            Log.d(TAG, "Testing Tasks API connection...");
            tasksApiService.getTasks(new TasksApiService.TaskListCallback() {
                @Override
                public void onSuccess(List<Task> tasks) {
                    Log.d(TAG, "Successfully retrieved " + tasks.size() + " tasks");
                    runOnUiThread(() -> Toast.makeText(DashboardActivity.this, 
                        "Successfully retrieved " + tasks.size() + " tasks", 
                        Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Failed to retrieve tasks", e);
                    runOnUiThread(() -> Toast.makeText(DashboardActivity.this,
                        "Error: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
                }
            });
        } else {
            Log.e(TAG, "TasksApiService is null");
        }
    }

    private void initializeTasksApiService(String email) {
        try {
            tasksApiService = new TasksApiService(this, email);
            testTasksConnection();
            loadTasks();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            Toast.makeText(this, "Error initializing tasks service: " + e.getMessage(), 
                          Toast.LENGTH_SHORT).show();
        }
    }

    private void testTasksConnection() {
        if (tasksApiService != null) {
            Log.d(TAG, "Testing Tasks API connection...");
            tasksApiService.getTasks(new TasksApiService.TaskListCallback() {
                @Override
                public void onSuccess(List<Task> tasks) {
                    runOnUiThread(() -> {
                        String message = "Successfully connected to Tasks API. Found " + tasks.size() + " tasks.";
                        Log.d(TAG, message);
                        Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        String error = "Tasks API Error: " + e.getMessage();
                        Log.e(TAG, error, e);
                        Toast.makeText(DashboardActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            Log.e(TAG, "TasksApiService is not initialized");
        }
    }
}












