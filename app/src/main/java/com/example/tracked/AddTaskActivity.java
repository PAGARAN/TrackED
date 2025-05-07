package com.example.tracked;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Calendar;
import java.text.ParseException;

import com.example.tracked.api.TasksApiService;
import com.example.tracked.services.CalendarApiService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.Task;
import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.SharedPreferences;
import android.app.TimePickerDialog;
import android.widget.TimePicker;
import android.text.Editable;
import android.text.TextWatcher;
import com.example.tracked.utils.DateTimeUtils;

public class AddTaskActivity extends AppCompatActivity {
    private static final String TAG = "AddTaskActivity";
    private static final int REQUEST_AUTHORIZATION = 1001;
    private static final int RC_SIGN_IN = 1002;
    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 1003;

    private GoogleSignInClient mGoogleSignInClient;
    private TasksApiService tasksApiService;
    private String selectedStartDate;
    private String selectedDueDate;
    private TextView taskTypeText;
    private String selectedTaskType;
    private MaterialCardView startDateCard;
    private MaterialCardView dueDateCard;
    private MaterialCardView addToCalendarCard;
    
    // Add these field declarations
    private EditText taskNameInput;
    private EditText descriptionInput;
    private MaterialButton addTaskButton;
    private com.google.android.material.switchmaterial.SwitchMaterial addToCalendarSwitch;
    private com.google.android.material.chip.Chip lowPriorityChip, mediumPriorityChip, highPriorityChip;
    private String selectedPriority = "Medium"; // Default priority
    private boolean addToCalendar = false;
    private CalendarApiService calendarApiService;
    private EditText startHourInput, startMinuteInput, dueHourInput, dueMinuteInput;
    private String selectedStartDateOnly, selectedDueDateOnly;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        // Initialize views and other setup
        initializeViews();
        setupTaskTypeDropdown();
        setupPriorityChips();

        // Configure Google Sign In with explicit Tasks scope
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(TasksScopes.TASKS))
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Check for existing Google Sign In account
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            Log.d(TAG, "Found signed in account: " + account.getEmail());
            initializeTasksApiService(account.getEmail());
        } else {
            Log.d(TAG, "No signed in account found, requesting sign-in");
            signIn();
        }
        
        // Initialize calendar service if we have permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) 
                == PackageManager.PERMISSION_GRANTED) {
            calendarApiService = new CalendarApiService(this);
        }
    }

    private void initializeTasksApiService(String email) {
        try {
            if (email == null || email.isEmpty()) {
                Log.e(TAG, "Cannot initialize TasksApiService with null or empty email");
                Toast.makeText(this, "Invalid account email", Toast.LENGTH_SHORT).show();
                signIn(); // Try to sign in again
                return;
            }
            
            // Save user email to shared preferences for services to use
            SharedPreferences prefs = getSharedPreferences("TrackedPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_email", email);
            editor.apply();
            
            Log.d(TAG, "Initializing TasksApiService with email: " + email);
            tasksApiService = new TasksApiService(this, email);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            Toast.makeText(this, "Error initializing Tasks API: " + e.getMessage(), 
                         Toast.LENGTH_LONG).show();
            signIn(); // Try to sign in again
        }
    }

    private void signIn() {
        Log.d(TAG, "Starting sign-in process");
        
        // Configure sign-in to request the user's ID, email address, and basic profile
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(TasksScopes.TASKS))
                .build();
        
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Sign out first to ensure we get a fresh sign-in
        mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
            Log.d(TAG, "Sign-out completed, starting sign-in intent");
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "Sign-in result received with resultCode: " + resultCode);
            com.google.android.gms.tasks.Task<GoogleSignInAccount> task = 
                GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        } else if (requestCode == REQUEST_AUTHORIZATION) {
            Log.d(TAG, "Authorization result received with resultCode: " + resultCode);
            if (resultCode == RESULT_OK) {
                // Re-initialize the Tasks API service with the current account
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null && account.getEmail() != null) {
                    initializeTasksApiService(account.getEmail());
                    addTask();
                } else {
                    Toast.makeText(this, "Failed to get account after authorization", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Authorization required to add tasks", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleSignInResult(com.google.android.gms.tasks.Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getEmail() != null) {
                Log.d(TAG, "Sign-in successful: " + account.getEmail());
                initializeTasksApiService(account.getEmail());
            } else {
                Log.e(TAG, "Sign-in successful but account or email is null");
                Toast.makeText(this, "Failed to get email from Google account", Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            Log.e(TAG, "signInResult:failed code=" + e.getStatusCode(), e);
            Toast.makeText(this, "Google Sign In failed: " + getSignInErrorMessage(e.getStatusCode()), 
                         Toast.LENGTH_SHORT).show();
        }
    }

    private String getSignInErrorMessage(int statusCode) {
        switch (statusCode) {
            case 7:
                return "Network error. Please check your internet connection.";
            case 10:
                return "Developer error. Please contact support.";
            case 12:
                return "Sign in canceled by user.";
            case 13:
                return "Sign in currently in progress.";
            default:
                return "Error code: " + statusCode;
        }
    }

    private void initializeViews() {
        taskNameInput = findViewById(R.id.taskNameInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        addTaskButton = findViewById(R.id.addTaskButton);
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton saveButton = findViewById(R.id.saveButton);

        // Initialize priority chips
        lowPriorityChip = findViewById(R.id.lowPriorityChip);
        mediumPriorityChip = findViewById(R.id.mediumPriorityChip);
        highPriorityChip = findViewById(R.id.highPriorityChip);

        // Initialize calendar switch
        addToCalendarSwitch = findViewById(R.id.addToCalendarSwitch);
        addToCalendarCard = findViewById(R.id.addToCalendarCard);

        // Initialize time input fields
        startHourInput = findViewById(R.id.startHourInput);
        startMinuteInput = findViewById(R.id.startMinuteInput);
        dueHourInput = findViewById(R.id.dueHourInput);
        dueMinuteInput = findViewById(R.id.dueMinuteInput);
        
        // Set default values (current time)
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);
        
        startHourInput.setText(String.format(Locale.US, "%02d", currentHour));
        startMinuteInput.setText(String.format(Locale.US, "%02d", currentMinute));
        dueHourInput.setText(String.format(Locale.US, "%02d", currentHour));
        dueMinuteInput.setText(String.format(Locale.US, "%02d", currentMinute));
        
        // Add text change listeners to validate input
        startHourInput.addTextChangedListener(new TimeInputValidator(startHourInput, 0, 23));
        startMinuteInput.addTextChangedListener(new TimeInputValidator(startMinuteInput, 0, 59));
        dueHourInput.addTextChangedListener(new TimeInputValidator(dueHourInput, 0, 23));
        dueMinuteInput.addTextChangedListener(new TimeInputValidator(dueMinuteInput, 0, 59));

        if (addTaskButton == null) {
            Log.e(TAG, "Failed to find addTaskButton in layout");
            Toast.makeText(this, "Error initializing UI", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                if (tasksApiService != null) {
                    addTask();
                } else {
                    Toast.makeText(this, "Tasks API not properly initialized", 
                                 Toast.LENGTH_SHORT).show();
                }
            });
        }

        startDateCard = findViewById(R.id.startDateCard);
        dueDateCard = findViewById(R.id.dueDateCard);

        startDateCard.setOnClickListener(v -> showDatePicker("start"));
        dueDateCard.setOnClickListener(v -> showDatePicker("due"));

        addTaskButton.setOnClickListener(v -> {
            if (tasksApiService != null) {
                addTask();
            } else {
                Toast.makeText(this, "Tasks API not properly initialized", 
                             Toast.LENGTH_SHORT).show();
            }
        });
        
        // Set up calendar switch listener
        addToCalendarSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            addToCalendar = isChecked;
            
            // Request calendar permissions if needed
            if (addToCalendar && calendarApiService == null) {
                requestCalendarPermissions();
            }
        });
    }

    private void setupTaskTypeDropdown() {
        taskTypeText = findViewById(R.id.taskTypeText);
        MaterialCardView taskTypeCard = findViewById(R.id.taskTypeCard);

        String[] taskTypes = new String[]{"Home Work", "Project", "Part-time Task"};

        taskTypeCard.setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle("Select Task Type")
                   .setItems(taskTypes, (dialog, which) -> {
                       selectedTaskType = taskTypes[which];
                       taskTypeText.setText(selectedTaskType);
                   })
                   .setCancelable(true)
                   .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                   .show();
        });
    }

    private void setupPriorityChips() {
        // Set medium as default selected
        mediumPriorityChip.setChecked(true);
        
        // Set up click listeners
        lowPriorityChip.setOnClickListener(v -> {
            lowPriorityChip.setChecked(true);
            mediumPriorityChip.setChecked(false);
            highPriorityChip.setChecked(false);
            selectedPriority = "Low";
        });
        
        mediumPriorityChip.setOnClickListener(v -> {
            lowPriorityChip.setChecked(false);
            mediumPriorityChip.setChecked(true);
            highPriorityChip.setChecked(false);
            selectedPriority = "Medium";
        });
        
        highPriorityChip.setOnClickListener(v -> {
            lowPriorityChip.setChecked(false);
            mediumPriorityChip.setChecked(false);
            highPriorityChip.setChecked(true);
            selectedPriority = "High";
        });
    }

    private void showDatePicker(String dateType) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(dateType.equals("start") ? "Select start date" : "Select due date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            try {
                // Create a Date object from the selection
                Date selectedDate = new Date(selection);
                
                // Format for display using our utility class
                String displayDate = DateTimeUtils.formatToDisplayDate(selectedDate);
                
                // Store the selected date (without time)
                SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                
                if (dateType.equals("start")) {
                    selectedStartDateOnly = dateOnlyFormat.format(selectedDate);
                    TextView startDateText = findViewById(R.id.startDateText);
                    startDateText.setText(displayDate);
                    updateStartDateTime();
                } else {
                    selectedDueDateOnly = dateOnlyFormat.format(selectedDate);
                    TextView dueDateText = findViewById(R.id.dueDateText);
                    dueDateText.setText(displayDate);
                    updateDueDateTime();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error formatting date", e);
                Toast.makeText(this, "Error setting date", Toast.LENGTH_SHORT).show();
            }
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER_" + dateType.toUpperCase());
    }

    private void updateStartDateTime() {
        if (selectedStartDateOnly == null) return;
        
        try {
            int hour = Integer.parseInt(startHourInput.getText().toString());
            int minute = Integer.parseInt(startMinuteInput.getText().toString());
            
            // Combine date and time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date date = dateFormat.parse(selectedStartDateOnly);
            
            if (date != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                
                // Format for API using our utility class
                selectedStartDate = DateTimeUtils.formatToApiDate(calendar.getTime());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating start date and time", e);
        }
    }

    private void updateDueDateTime() {
        if (selectedDueDateOnly == null) return;
        
        String hourStr = dueHourInput.getText().toString();
        String minuteStr = dueMinuteInput.getText().toString();
        
        int hour = 0;
        int minute = 0;
        
        if (!hourStr.isEmpty()) {
            hour = Integer.parseInt(hourStr);
        }
        
        if (!minuteStr.isEmpty()) {
            minute = Integer.parseInt(minuteStr);
        }
        
        try {
            // Parse the date part
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); // Philippine time
            Date date = dateFormat.parse(selectedDueDateOnly);
            
            if (date != null) {
                // Set the time part
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); // Philippine time
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                
                // Convert to UTC for API
                calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
                
                // Format in API format
                SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                selectedDueDate = apiFormat.format(calendar.getTime());
                
                Log.d(TAG, "Updated due date/time: " + selectedDueDate);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating due date/time", e);
        }
    }

    private void addTask() {
        if (tasksApiService == null) {
            Toast.makeText(this, "Tasks API not properly initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = taskNameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        if (title.isEmpty()) {
            taskNameInput.setError("Title is required");
            return;
        }

        // Validate time inputs
        if (startHourInput.getError() != null || startMinuteInput.getError() != null ||
            dueHourInput.getError() != null || dueMinuteInput.getError() != null) {
            Toast.makeText(this, "Please correct the time input errors", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedStartDateOnly == null) {
            Toast.makeText(this, "Please select a start date", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDueDateOnly == null) {
            Toast.makeText(this, "Please select a due date", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update date and time combinations
        updateStartDateTime();
        updateDueDateTime();

        // Include priority in description
        String fullDescription = "Type: " + (selectedTaskType != null ? selectedTaskType : "Not specified") + 
                               "\nPriority: " + selectedPriority +
                               "\nStart Date: " + selectedStartDate + 
                               "\n\n" + description;

        // Disable button to prevent multiple submissions
        addTaskButton.setEnabled(false);
        addTaskButton.setText("Adding...");

        Log.d(TAG, "Adding task - Title: " + title + ", Due: " + selectedDueDate);

        tasksApiService.addTask(title, fullDescription, selectedDueDate, new TasksApiService.TaskCallback() {
            @Override
            public void onSuccess(Task task) {
                // If add to calendar is enabled, create a calendar event
                boolean calendarSuccess = false;
                if (addToCalendar && addToCalendarSwitch.isChecked()) {
                    try {
                        addTaskToCalendar(title, fullDescription, selectedDueDate);
                        calendarSuccess = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error adding task to calendar", e);
                        calendarSuccess = false;
                    }
                }
                
                final boolean finalCalendarSuccess = calendarSuccess;
                runOnUiThread(() -> {
                    if (addToCalendar && addToCalendarSwitch.isChecked() && !finalCalendarSuccess) {
                        Toast.makeText(AddTaskActivity.this, 
                            "Task added but failed to add to calendar", 
                            Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AddTaskActivity.this, 
                            "Task added successfully", 
                            Toast.LENGTH_SHORT).show();
                    }
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    addTaskButton.setEnabled(true);
                    addTaskButton.setText("Add Task");
                    Toast.makeText(AddTaskActivity.this, 
                        "Error adding task: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error adding task", e);
                });
            }

            @Override
            public void onAuthorizationRequired(Intent intent) {
                startActivityForResult(intent, REQUEST_AUTHORIZATION);
            }
        });
    }

    private void requestCalendarPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR},
                CALENDAR_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CALENDAR_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Initialize calendar service now that we have permission
                calendarApiService = new CalendarApiService(this);
                Toast.makeText(this, "Calendar permission granted", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied, disable the functionality
                addToCalendar = false;
                addToCalendarSwitch.setChecked(false);
                Toast.makeText(this, "Calendar permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addTaskToCalendar(String title, String description, String dueDate) {
        // Double-check calendar permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No WRITE_CALENDAR permission, skipping calendar event creation");
            return; // Skip if we don't have permission
        }
        
        try {
            // Parse due date with time
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = apiFormat.parse(dueDate);
            
            if (date != null) {
                // Create calendar event
                ContentResolver cr = getContentResolver();
                ContentValues values = new ContentValues();
                
                values.put(CalendarContract.Events.TITLE, "Task: " + title);
                values.put(CalendarContract.Events.DESCRIPTION, description);
                
                // Set start time to due date with time
                long startMillis = date.getTime();
                values.put(CalendarContract.Events.DTSTART, startMillis);
                
                // Set end time to 1 hour after due date
                long endMillis = startMillis + (60 * 60 * 1000);
                values.put(CalendarContract.Events.DTEND, endMillis);
                
                // Get default calendar ID
                long calendarId = getDefaultCalendarId();
                if (calendarId <= 0) {
                    Log.e(TAG, "Invalid calendar ID: " + calendarId + ", skipping calendar event creation");
                    return;
                }
                
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                
                // Add a reminder (15 minutes before)
                values.put(CalendarContract.Events.HAS_ALARM, 1);
                
                try {
                    Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
                    if (uri != null) {
                        // Add reminder
                        ContentValues reminderValues = new ContentValues();
                        reminderValues.put(CalendarContract.Reminders.EVENT_ID, Long.parseLong(uri.getLastPathSegment()));
                        reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                        reminderValues.put(CalendarContract.Reminders.MINUTES, 15);
                        cr.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);
                        
                        Log.d(TAG, "Task added to calendar successfully");
                    } else {
                        Log.e(TAG, "Failed to insert event, uri is null");
                    }
                } catch (SecurityException se) {
                    Log.e(TAG, "Security exception when inserting calendar event", se);
                } catch (IllegalArgumentException iae) {
                    Log.e(TAG, "Illegal argument when inserting calendar event", iae);
                } catch (Exception e) {
                    Log.e(TAG, "Error inserting calendar event", e);
                }
            } else {
                Log.e(TAG, "Failed to parse due date: " + dueDate);
            }
        } catch (ParseException pe) {
            Log.e(TAG, "Error parsing due date: " + dueDate, pe);
        } catch (Exception e) {
            Log.e(TAG, "Error adding task to calendar", e);
        }
    }

    private long getDefaultCalendarId() {
        long calendarId = -1; // Default to -1 to indicate no calendar found
        
        // Check for calendar permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No READ_CALENDAR permission, cannot get default calendar ID");
            return calendarId;
        }
        
        try {
            // First try to get the primary calendar
            String[] projection = new String[]{CalendarContract.Calendars._ID};
            String selection = CalendarContract.Calendars.IS_PRIMARY + "=1";
            Cursor cursor = getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null);
            
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID);
                    if (idIdx != -1) {
                        calendarId = cursor.getLong(idIdx);
                        Log.d(TAG, "Found primary calendar with ID: " + calendarId);
                    }
                }
                cursor.close();
            }
            
            // If no primary calendar, try to get any calendar
            if (calendarId == -1) {
                Log.d(TAG, "No primary calendar found, looking for any calendar");
                cursor = getContentResolver().query(
                        CalendarContract.Calendars.CONTENT_URI,
                        projection,
                        null,
                        null,
                        null);
                
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID);
                        if (idIdx != -1) {
                            calendarId = cursor.getLong(idIdx);
                            Log.d(TAG, "Found calendar with ID: " + calendarId);
                        }
                    }
                    cursor.close();
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Security exception when querying calendars", se);
        } catch (Exception e) {
            Log.e(TAG, "Error getting default calendar", e);
        }
        
        if (calendarId == -1) {
            Log.e(TAG, "No calendar found on device");
        }
        
        return calendarId;
    }

    private class TimeInputValidator implements TextWatcher {
        private final EditText editText;
        private final int minValue;
        private final int maxValue;
        
        public TimeInputValidator(EditText editText, int minValue, int maxValue) {
            this.editText = editText;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }
        
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        
        @Override
        public void afterTextChanged(Editable s) {
            try {
                String text = s.toString();
                if (!text.isEmpty()) {
                    int value = Integer.parseInt(text);
                    if (value < minValue || value > maxValue) {
                        editText.setError("Value must be between " + minValue + " and " + maxValue);
                    } else {
                        editText.setError(null);
                        // Update the combined date and time
                        if (editText == startHourInput || editText == startMinuteInput) {
                            updateStartDateTime();
                        } else if (editText == dueHourInput || editText == dueMinuteInput) {
                            updateDueDateTime();
                        }
                    }
                }
            } catch (NumberFormatException e) {
                editText.setError("Invalid number");
            }
        }
    }
}
