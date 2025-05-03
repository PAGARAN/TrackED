package com.example.tracked;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import androidx.recyclerview.widget.PagerSnapHelper;

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
import java.util.ArrayList;
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
import com.google.android.material.tabs.TabLayout;
import android.graphics.Rect;
import android.net.Uri;
import com.example.tracked.api.NewsApi;
import com.example.tracked.model.NewsArticle;
import com.example.tracked.model.NewsResponse;
import com.example.tracked.adapters.NewsAdapter;
import com.example.tracked.api.RssFeedService;

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
    // TabLayout field declaration removed

    // Add these new field declarations
    private ImageView newsImage;
    private TextView newsTitle;
    private TextView newsDescription;
    private ProgressBar newsProgress;
    private NewsApi newsApi;
    private RecyclerView newsRecyclerView;
    private NewsAdapter newsAdapter;
    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private final int AUTO_SCROLL_DELAY = 5000; // 5 seconds between scrolls
    private RssFeedService rssFeedService;

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
        
        // Initialize news category views
        TextView buksuNewsButton = findViewById(R.id.buksuNewsButton);
        TextView schoolNewsButton = findViewById(R.id.schoolNewsButton);
        
        // Set up news category click listeners
        buksuNewsButton.setOnClickListener(v -> {
            fetchBuksuNews();
        });
        
        schoolNewsButton.setOnClickListener(v -> {
            showSchoolSelectionDialog();
        });

        // Initialize news views
        newsRecyclerView = findViewById(R.id.newsRecyclerView);
        newsProgress = findViewById(R.id.newsProgress);
        
        // Set up news RecyclerView
        LinearLayoutManager newsLayoutManager = new LinearLayoutManager(this,
            LinearLayoutManager.HORIZONTAL, false);
        newsRecyclerView.setLayoutManager(newsLayoutManager);
        
        // Add PagerSnapHelper to snap to full items
        PagerSnapHelper newsSnapHelper = new PagerSnapHelper();
        newsSnapHelper.attachToRecyclerView(newsRecyclerView);
        
        // Initialize adapter
        newsAdapter = new NewsAdapter(this);
        newsRecyclerView.setAdapter(newsAdapter);

        // Initialize auto-scroll handler
        autoScrollHandler = new Handler();
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (newsAdapter.getItemCount() > 0 && isActivityActive) {
                    int currentPosition = ((LinearLayoutManager) newsRecyclerView.getLayoutManager())
                            .findFirstVisibleItemPosition();
                    int nextPosition = (currentPosition + 1) % newsAdapter.getItemCount();
                    newsRecyclerView.smoothScrollToPosition(nextPosition);
                    
                    // Schedule the next scroll
                    autoScrollHandler.postDelayed(this, AUTO_SCROLL_DELAY);
                }
            }
        };

        // Set up RecyclerView for tasks
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        tasksRecyclerView.setLayoutManager(layoutManager);

        // Initialize adapter for tasks
        taskAdapter = new TaskAdapter();
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
        View headerView = navigationView.getHeaderView(0);
        TextView userEmailView = headerView.findViewById(R.id.userEmail);

        if (mAuth.getCurrentUser() != null) {
            String userEmail = mAuth.getCurrentUser().getEmail();
            if (userEmailView != null) {
                userEmailView.setText(userEmail);
            }
            initializeTasksApiService(userEmail);
        }

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
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
        
        // Create logging interceptor
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        
        // Create OkHttpClient for all API calls
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        
        // Initialize ZenQuotesApi
        Retrofit zenQuotesRetrofit = new Retrofit.Builder()
            .baseUrl("https://zenquotes.io/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        
        zenQuotesApi = zenQuotesRetrofit.create(ZenQuotesApi.class);
        
        // Initialize WeatherApi
        Retrofit weatherRetrofit = new Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        
        weatherApi = weatherRetrofit.create(WeatherApi.class);
        
        // Initialize NewsApi
        Retrofit newsRetrofit = new Retrofit.Builder()
            .baseUrl("https://newsapi.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        
        newsApi = newsRetrofit.create(NewsApi.class);
        
        // Initialize RSS feed service
        rssFeedService = new RssFeedService();
        
        // Start fetching data
        fetchQuote();
        checkLocationPermissionAndFetchWeather();
        fetchEducationNews(); // Default news category
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
        // Start auto-scrolling when the activity is visible
        startAutoScroll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        // Stop auto-scrolling when the activity is not visible
        stopAutoScroll();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Show dashboard content, hide fragment container
            findViewById(R.id.tasksContainer).setVisibility(View.VISIBLE);
            findViewById(R.id.appBarLayout).setVisibility(View.VISIBLE);
            findViewById(R.id.fragmentContainer).setVisibility(View.GONE);
        } else if (id == R.id.nav_calendar) {
            // Handle calendar action
            Toast.makeText(this, "Calendar feature coming soon", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_tasks) {
            showTaskListFragment();
        } else if (id == R.id.nav_news) {
            // Show the news fragment
            showNewsFragment();
        } else if (id == R.id.nav_profile) {
            // Handle profile action
            Toast.makeText(this, "Profile feature coming soon", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_logout) {
            signOut();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void signOut() {
                    // Show progress dialog
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Signing out...");
                    progressDialog.show();

                    // Sign out from Google
                    mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Revoke access to ensure complete sign-out
            mGoogleSignInClient.revokeAccess().addOnCompleteListener(this, revokeTask -> {
                        // Sign out from Firebase
                        mAuth.signOut();

                // Dismiss progress dialog
                        progressDialog.dismiss();

                // Clear all activities and start login
                        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_TASK_REQUEST_CODE && resultCode == RESULT_OK) {
            // Refresh task list when a new task is added
            loadTasks();
            
            // Also refresh tasks in the TaskListFragment if it's active
            TaskListFragment taskListFragment = (TaskListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainer);
            if (taskListFragment != null) {
                taskListFragment.refreshTasks();
            }
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
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load tasks", e);
                    Toast.makeText(DashboardActivity.this, 
                        "Error loading tasks: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                    emptyTasksView.setVisibility(View.VISIBLE);
                    tasksRecyclerView.setVisibility(View.GONE);
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

    public void fetchEducationNews() {
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        String apiKey = getString(R.string.news_api_key);
        
        newsApi.getEducationNews(
            "\"DepEd\" OR \"Department of Education Philippines\" OR \"Philippine education\" OR \"Philippine schools\"",
            "publishedAt",
            "en",
            apiKey
        ).enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                newsProgress.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null && 
                    !response.body().getArticles().isEmpty()) {
                    
                    List<NewsArticle> articles = response.body().getArticles();
                    // Limit to 5 articles
                    if (articles.size() > 5) {
                        articles = articles.subList(0, 5);
                    }
                    
                    newsAdapter.setNewsArticles(articles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Start auto-scrolling once we have news articles
                    startAutoScroll();
                } else {
                    showNewsError("Failed to load DepEd news");
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                newsProgress.setVisibility(View.GONE);
                showNewsError("Network error: " + t.getMessage());
            }
        });
    }

    private void showNewsError(String message) {
        Toast.makeText(this, "News error: " + message, Toast.LENGTH_SHORT).show();
        newsRecyclerView.setVisibility(View.GONE);
    }

    private void startAutoScroll() {
        // Remove any existing callbacks to avoid duplicates
        stopAutoScroll();
        // Start auto-scrolling
        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
    }

    private void stopAutoScroll() {
        // Remove the auto-scroll callback
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
    }

    private void fetchDepEdRssFeed() {
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        // DepEd official RSS feed URL
        String depEdRssFeedUrl = "https://www.deped.gov.ph/feed/";
        
        rssFeedService.fetchFeed(depEdRssFeedUrl, new RssFeedService.RssFeedCallback() {
            @Override
            public void onSuccess(List<NewsArticle> newsArticles) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    
                    if (newsArticles.isEmpty()) {
                        showNewsError("No news articles found");
                        return;
                    }
                    
                    // Create a new list to avoid modifying the original
                    List<NewsArticle> limitedArticles = new ArrayList<>(newsArticles);
                    // Limit to 5 articles
                    if (limitedArticles.size() > 5) {
                        limitedArticles = limitedArticles.subList(0, 5);
                    }
                    
                    newsAdapter.setNewsArticles(limitedArticles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Start auto-scrolling once we have news articles
                    startAutoScroll();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    showNewsError("Error loading news: " + e.getMessage());
                });
            }
        });
    }

    // Add this method to fetch news for a specific school
    private void fetchSchoolNews(String schoolName) {
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        String apiKey = getString(R.string.news_api_key);
        
        // Create a query that combines the school name with education-related terms
        String query = "\"" + schoolName + "\" AND (education OR school OR students OR teachers)";
        
        newsApi.getSchoolNews(
            query,
            "publishedAt",
            "en",
            apiKey
        ).enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                newsProgress.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null && 
                    !response.body().getArticles().isEmpty()) {
                    
                    List<NewsArticle> articles = response.body().getArticles();
                    // Limit to 5 articles
                    List<NewsArticle> limitedArticles = new ArrayList<>(articles);
                    if (limitedArticles.size() > 5) {
                        limitedArticles = limitedArticles.subList(0, 5);
                    }
                    
                    newsAdapter.setNewsArticles(limitedArticles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Start auto-scrolling once we have news articles
                    startAutoScroll();
                } else {
                    showNewsError("No news found for " + schoolName);
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                newsProgress.setVisibility(View.GONE);
                showNewsError("Network error: " + t.getMessage());
            }
        });
    }

    // Alternative method using RSS feeds for school-specific news
    private void fetchSchoolRssFeed(String schoolName) {
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        // You can use a general education RSS feed and filter it
        String educationRssFeedUrl = "https://www.deped.gov.ph/feed/";
        
        rssFeedService.fetchFeed(educationRssFeedUrl, new RssFeedService.RssFeedCallback() {
            @Override
            public void onSuccess(List<NewsArticle> newsArticles) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    
                    // Filter articles that mention the school name
                    List<NewsArticle> schoolArticles = new ArrayList<>();
                    for (NewsArticle article : newsArticles) {
                        if ((article.getTitle() != null && 
                             article.getTitle().toLowerCase().contains(schoolName.toLowerCase())) ||
                            (article.getDescription() != null && 
                             article.getDescription().toLowerCase().contains(schoolName.toLowerCase()))) {
                            schoolArticles.add(article);
                        }
                    }
                    
                    if (schoolArticles.isEmpty()) {
                        showNewsError("No news found for " + schoolName);
                        return;
                    }
                    
                    // Limit to 5 articles
                    List<NewsArticle> limitedArticles = new ArrayList<>(schoolArticles);
                    if (limitedArticles.size() > 5) {
                        limitedArticles = limitedArticles.subList(0, 5);
                    }
                    
                    newsAdapter.setNewsArticles(limitedArticles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Start auto-scrolling once we have news articles
                    startAutoScroll();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    showNewsError("Error loading news: " + e.getMessage());
                });
            }
        });
    }

    public void showSchoolSelectionDialog() {
        // Create an EditText for user input
        final EditText input = new EditText(this);
        input.setHint("Enter school name");
        
        // Use LinearLayout to add padding
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding);
        layout.setPadding(padding, padding, padding, padding);
        layout.addView(input);
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("School News")
            .setView(layout)
            .setPositiveButton("Search", (dialog, which) -> {
                String schoolName = input.getText().toString().trim();
                if (!schoolName.isEmpty()) {
                    // Use NewsAPI for more accurate results
                    fetchSchoolNews(schoolName);
                    // Or use RSS feed filtering
                    // fetchSchoolRssFeed(schoolName);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    public void fetchBuksuNews() {
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        String apiKey = getString(R.string.news_api_key);
        
        // Create a specific query for Bukidnon State University
        // Include the domain to get more relevant results
        String query = "\"Bukidnon State University\" OR \"BukSU\" OR \"BSU Bukidnon\" OR buksu.edu.ph";
        
        newsApi.getSchoolNews(
            query,
            "publishedAt",
            "en",
            apiKey
        ).enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                newsProgress.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null && 
                    !response.body().getArticles().isEmpty()) {
                    
                    List<NewsArticle> articles = response.body().getArticles();
                    // Limit to 5 articles
                    List<NewsArticle> limitedArticles = new ArrayList<>(articles);
                    if (limitedArticles.size() > 5) {
                        limitedArticles = limitedArticles.subList(0, 5);
                    }
                    
                    newsAdapter.setNewsArticles(limitedArticles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Start auto-scrolling once we have news articles
                    startAutoScroll();
                } else {
                    // If no results from NewsAPI, try RSS feed approach
                    fetchBuksuRssFeed();
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                // On failure, try RSS feed approach
                fetchBuksuRssFeed();
            }
        });
    }

    // Add this method to fetch from the correct BukSU RSS feed
    private void fetchBuksuRssFeed() {
        // Try to fetch from Bukidnon State University's own RSS feed
        // Based on the website, let's try common RSS feed paths
        String buksuFeedUrl = "https://buksu.edu.ph/feed/";
        
        // We'll also try alternative paths if the main one fails
        rssFeedService.fetchFeed(buksuFeedUrl, new RssFeedService.RssFeedCallback() {
            @Override
            public void onSuccess(List<NewsArticle> newsArticles) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    
                    if (!newsArticles.isEmpty()) {
                        // Limit to 5 articles
                        List<NewsArticle> limitedArticles = new ArrayList<>(newsArticles);
                        if (limitedArticles.size() > 5) {
                            limitedArticles = limitedArticles.subList(0, 5);
                        }
                        
                        newsAdapter.setNewsArticles(limitedArticles);
                        newsRecyclerView.setVisibility(View.VISIBLE);
                        
                        // Start auto-scrolling once we have news articles
                        startAutoScroll();
                    } else {
                        // Try alternative feed URL
                        tryAlternativeBuksuFeed();
                    }
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                // Try alternative feed URL
                tryAlternativeBuksuFeed();
            }
        });
    }

    // Add this method to try alternative BukSU feed URLs
    private void tryAlternativeBuksuFeed() {
        // Try alternative feed URL (news section)
        String altFeedUrl = "https://buksu.edu.ph/news/feed/";
        
        rssFeedService.fetchFeed(altFeedUrl, new RssFeedService.RssFeedCallback() {
            @Override
            public void onSuccess(List<NewsArticle> newsArticles) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    
                    if (!newsArticles.isEmpty()) {
                        // Limit to 5 articles
                        List<NewsArticle> limitedArticles = new ArrayList<>(newsArticles);
                        if (limitedArticles.size() > 5) {
                            limitedArticles = limitedArticles.subList(0, 5);
                        }
                        
                        newsAdapter.setNewsArticles(limitedArticles);
                        newsRecyclerView.setVisibility(View.VISIBLE);
                        
                        // Start auto-scrolling once we have news articles
                        startAutoScroll();
                    } else {
                        // If still no results, try scraping the news page directly
                        fetchAndFilterDepEdFeedForBuksu();
                    }
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                // If all RSS feed attempts fail, fall back to DepEd feed filtering
                fetchAndFilterDepEdFeedForBuksu();
            }
        });
    }

    // Add this method to fetch and filter DepEd feed for BukSU
    private void fetchAndFilterDepEdFeedForBuksu() {
        String depEdFeedUrl = "https://www.deped.gov.ph/feed/";
        
        rssFeedService.fetchFeed(depEdFeedUrl, new RssFeedService.RssFeedCallback() {
            @Override
            public void onSuccess(List<NewsArticle> newsArticles) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    
                    // Filter articles that mention Bukidnon State University
                    List<NewsArticle> buksuArticles = new ArrayList<>();
                    for (NewsArticle article : newsArticles) {
                        String title = article.getTitle() != null ? article.getTitle().toLowerCase() : "";
                        String description = article.getDescription() != null ? article.getDescription().toLowerCase() : "";
                        
                        if (title.contains("bukidnon state university") || 
                            title.contains("buksu") || 
                            title.contains("bsu bukidnon") ||
                            description.contains("bukidnon state university") || 
                            description.contains("buksu") || 
                            description.contains("bsu bukidnon")) {
                            buksuArticles.add(article);
                        }
                    }
                    
                    if (!buksuArticles.isEmpty()) {
                        // Limit to 5 articles
                        List<NewsArticle> limitedArticles = new ArrayList<>(buksuArticles);
                        if (limitedArticles.size() > 5) {
                            limitedArticles = limitedArticles.subList(0, 5);
                        }
                        
                        newsAdapter.setNewsArticles(limitedArticles);
                        newsRecyclerView.setVisibility(View.VISIBLE);
                        
                        // Start auto-scrolling once we have news articles
                        startAutoScroll();
                    } else {
                        showNewsError("No Bukidnon State University news found");
                    }
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    showNewsError("Error loading Bukidnon State University news: " + e.getMessage());
                });
            }
        });
    }

    // Add this method to show a dialog with popular schools
    private void showPopularSchoolsDialog() {
        // List of popular schools in the Philippines
        final String[] popularSchools = new String[] {
            "University of the Philippines",
            "Ateneo de Manila University",
            "De La Salle University",
            "University of Santo Tomas",
            "Mindanao State University",
            "Central Mindanao University",
            "Xavier University",
            "Far Eastern University"
        };
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("Select School")
            .setItems(popularSchools, (dialog, which) -> {
                if (which == popularSchools.length - 1) {
                    // Last option is "Other (Search)" - show search dialog
                    showSchoolSelectionDialog();
                } else {
                    // Fetch news for the selected school
                    fetchSchoolNews(popularSchools[which]);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Add this method to get the TasksApiService
    public TasksApiService getTasksApiService() {
        return tasksApiService;
    }

    // Add this method to start the AddTaskActivity
    public void startAddTaskActivity() {
        Intent intent = new Intent(DashboardActivity.this, AddTaskActivity.class);
        startActivityForResult(intent, ADD_TASK_REQUEST_CODE);
    }

    // When showing the TaskListFragment
    private void showTaskListFragment() {
        // Hide all other content
        findViewById(R.id.tasksContainer).setVisibility(View.GONE);
        findViewById(R.id.appBarLayout).setVisibility(View.GONE);
        
        // Show fragment container
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);
        
        // Make sure it fills the screen
        ViewGroup.LayoutParams params = fragmentContainer.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        fragmentContainer.setLayoutParams(params);
        
        // Load the TaskListFragment
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragmentContainer, new TaskListFragment())
            .commit();
    }

    // Add these methods to DashboardActivity to expose the news services to the fragment
    public NewsApi getNewsApi() {
        return newsApi;
    }

    public RssFeedService getRssFeedService() {
        return rssFeedService;
    }

    // Add this method to show the NewsFragment
    private void showNewsFragment() {
        // Hide all other content
        findViewById(R.id.tasksContainer).setVisibility(View.GONE);
        findViewById(R.id.appBarLayout).setVisibility(View.GONE);
        
        // Show fragment container
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentContainer.setVisibility(View.VISIBLE);
        
        // Make sure it fills the screen
        ViewGroup.LayoutParams params = fragmentContainer.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        fragmentContainer.setLayoutParams(params);
        
        // Load the NewsFragment
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragmentContainer, new NewsFragment())
            .commit();
    }

    // Add this method to open the navigation drawer
    public void openDrawer() {
        if (drawerLayout != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        }
    }
}

















