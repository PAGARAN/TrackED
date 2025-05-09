package com.example.tracked;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.tracked.api.TasksApiService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.api.services.tasks.model.Task;
import com.example.tracked.utils.DateTimeUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class EditTaskActivity extends AppCompatActivity {
    private static final String TAG = "EditTaskActivity";
    private static final int REQUEST_AUTHORIZATION = 1001;
    
    private TasksApiService tasksApiService;
    private String taskId;
    private String selectedDueDate;
    private String selectedStartDate;
    private EditText taskNameInput;
    private EditText descriptionInput;
    private MaterialButton updateTaskButton;
    private MaterialCardView dueDateCard;
    private MaterialCardView startDateCard;
    private MaterialCardView taskTypeCard;
    private TextView dueDateText;
    private TextView startDateText;
    private TextView taskTypeText;
    private String originalTitle;
    private String originalDescription;
    private String originalDueDate;
    private String originalStartDate;
    private String originalTaskType;
    private EditText dueHourInput, dueMinuteInput;
    private EditText startHourInput, startMinuteInput;
    private String selectedDueDateOnly;
    private String selectedStartDateOnly;
    private String selectedTaskType;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_task);
        
        // Initialize views
        taskNameInput = findViewById(R.id.taskNameInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        updateTaskButton = findViewById(R.id.updateTaskButton);
        dueDateCard = findViewById(R.id.dueDateCard);
        dueDateText = findViewById(R.id.dueDateText);
        startDateCard = findViewById(R.id.startDateCard);
        startDateText = findViewById(R.id.startDateText);
        taskTypeCard = findViewById(R.id.taskTypeCard);
        taskTypeText = findViewById(R.id.taskTypeText);
        
        // Check if time input fields exist before trying to use them
        dueHourInput = findViewById(R.id.dueHourInput);
        dueMinuteInput = findViewById(R.id.dueMinuteInput);
        startHourInput = findViewById(R.id.startHourInput);
        startMinuteInput = findViewById(R.id.startMinuteInput);
        
        ImageButton backButton = findViewById(R.id.backButton);
        
        // Add text change listeners only if the views exist
        if (dueHourInput != null && dueMinuteInput != null) {
            dueHourInput.addTextChangedListener(new TimeInputValidator(dueHourInput, 0, 23));
            dueMinuteInput.addTextChangedListener(new TimeInputValidator(dueMinuteInput, 0, 59));
        }
        
        if (startHourInput != null && startMinuteInput != null) {
            startHourInput.addTextChangedListener(new TimeInputValidator(startHourInput, 0, 23));
            startMinuteInput.addTextChangedListener(new TimeInputValidator(startMinuteInput, 0, 59));
        }
        
        // Get task details from intent
        Intent intent = getIntent();
        if (intent != null) {
            taskId = intent.getStringExtra("TASK_ID");
            originalTitle = intent.getStringExtra("TASK_TITLE");
            originalDescription = intent.getStringExtra("TASK_DESCRIPTION");
            originalDueDate = intent.getStringExtra("TASK_DUE_DATE");
            
            // Set initial values
            taskNameInput.setText(originalTitle);
            
            // Extract metadata from original description
            extractMetadataFromDescription(originalDescription);
            
            // Set the clean description (without metadata)
            String cleanDescription = extractCleanDescription(originalDescription);
            descriptionInput.setText(cleanDescription);
            
            selectedDueDate = originalDueDate;
            
            // Format and display due date
            if (originalDueDate != null) {
                try {
                    // Parse the original due date using our utility method
                    Date date = DateTimeUtils.parseApiDate(originalDueDate);
                    
                    if (date != null) {
                        // Set date text using our utility class (which displays in Philippine time)
                        dueDateText.setText(DateTimeUtils.formatToDisplayDateTime(date));
                        
                        // Set time inputs in Philippine time
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(date);
                        calendar.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); // Philippine time
                        
                        int hour = calendar.get(Calendar.HOUR_OF_DAY);
                        int minute = calendar.get(Calendar.MINUTE);
                        
                        if (dueHourInput != null && dueMinuteInput != null) {
                            dueHourInput.setText(String.format(Locale.US, "%02d", hour));
                            dueMinuteInput.setText(String.format(Locale.US, "%02d", minute));
                        }
                        
                        // Store date only part in Philippine time
                        SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        dateOnlyFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
                        selectedDueDateOnly = dateOnlyFormat.format(date);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing due date", e);
                    dueDateText.setText("Select due date");
                }
            } else {
                dueDateText.setText("Select due date");
            }
            
            // Format and display start date
            if (originalStartDate != null) {
                try {
                    // Parse the original start date
                    Date date = DateTimeUtils.parseApiDate(originalStartDate);
                    
                    if (date != null) {
                        // Set date text
                        startDateText.setText(DateTimeUtils.formatToDisplayDateTime(date));
                        
                        // Set time inputs
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(date);
                        calendar.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); // Philippine time
                        
                        int hour = calendar.get(Calendar.HOUR_OF_DAY);
                        int minute = calendar.get(Calendar.MINUTE);
                        
                        if (startHourInput != null && startMinuteInput != null) {
                            startHourInput.setText(String.format(Locale.US, "%02d", hour));
                            startMinuteInput.setText(String.format(Locale.US, "%02d", minute));
                        }
                        
                        // Store date only part
                        SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        dateOnlyFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
                        selectedStartDateOnly = dateOnlyFormat.format(date);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing start date", e);
                    startDateText.setText("Select start date");
                }
            } else {
                startDateText.setText("Select start date");
            }
            
            // Set task type
            if (originalTaskType != null) {
                taskTypeText.setText(originalTaskType);
                selectedTaskType = originalTaskType;
            } else {
                taskTypeText.setText("Select task type");
            }
        }
        
        // Set up click listeners
        dueDateCard.setOnClickListener(v -> showDatePicker("due"));
        startDateCard.setOnClickListener(v -> showDatePicker("start"));
        taskTypeCard.setOnClickListener(v -> showTaskTypeDialog());
        
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        updateTaskButton.setOnClickListener(v -> updateTask());
        
        // Initialize Tasks API service
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            initializeTasksApiService(account.getEmail());
        } else {
            Toast.makeText(this, "Not signed in. Please sign in first.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initializeTasksApiService(String email) {
        try {
            tasksApiService = new TasksApiService(this, email);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            Toast.makeText(this, "Error initializing Tasks API: " + e.getMessage(), 
                         Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void showDatePicker(String dateType) {
        // Get today's date in milliseconds for minimum date constraint
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long todayInMillis = calendar.getTimeInMillis();
        
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(dateType.equals("start") ? "Select start date" : "Select due date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.from(todayInMillis))
                    .build())
                .build();
                
        datePicker.addOnPositiveButtonClickListener(selection -> {
            try {
                // Create a Date object from the selection
                Date selectedDate = new Date(selection);
                
                if (dateType.equals("start")) {
                    // Format for display using our utility class (which uses Philippine time)
                    String displayDate = DateTimeUtils.formatToDisplayDateTime(selectedDate);
                    startDateText.setText(displayDate);
                    
                    // Store the selected date (without time)
                    SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    dateOnlyFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); // Set to Philippine timezone
                    selectedStartDateOnly = dateOnlyFormat.format(selectedDate);
                    
                    // Update the combined date and time
                    updateStartDateTime();
                } else {
                    // Format for display using our utility class (which uses Philippine time)
                    String displayDate = DateTimeUtils.formatToDisplayDateTime(selectedDate);
                    dueDateText.setText(displayDate);
                    
                    // Store the selected date (without time)
                    SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    dateOnlyFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); // Set to Philippine timezone
                    selectedDueDateOnly = dateOnlyFormat.format(selectedDate);
                    
                    // Update the combined date and time
                    updateDueDateTime();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error formatting date", e);
                Toast.makeText(this, "Error setting date", Toast.LENGTH_SHORT).show();
            }
        });
        
        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }
    
    private void updateDueDateTime() {
        if (selectedDueDateOnly == null) return;
        
        try {
            int hour = Integer.parseInt(dueHourInput.getText().toString());
            int minute = Integer.parseInt(dueMinuteInput.getText().toString());
            
            // Combine date and time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date date = dateFormat.parse(selectedDueDateOnly);
            
            if (date != null) {
                // Create calendar in Philippine timezone
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
                calendar.setTime(date);
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                
                // Format for API using our utility class
                selectedDueDate = DateTimeUtils.formatToApiDate(calendar.getTime());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating due date and time", e);
        }
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
                // Create calendar in Philippine timezone
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
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
    
    private void showTaskTypeDialog() {
        String[] taskTypes = new String[]{"Home Work", "Project", "Part-time Task"};
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Select Task Type")
               .setItems(taskTypes, (dialog, which) -> {
                   selectedTaskType = taskTypes[which];
                   taskTypeText.setText(selectedTaskType);
               })
               .setCancelable(true)
               .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
               .show();
    }
    
    private void updateTask() {
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
        
        if (selectedDueDate == null) {
            Toast.makeText(this, "Please select a due date", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedStartDate == null) {
            Toast.makeText(this, "Please select a start date", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if start date or due date has already passed
        try {
            Date currentDate = new Date();
            
            // Parse the dates for comparison
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            
            if (selectedStartDate != null) {
                Date startDate = apiFormat.parse(selectedStartDate);
                if (startDate != null && startDate.before(currentDate)) {
                    Toast.makeText(this, "Start date cannot be in the past", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            if (selectedDueDate != null) {
                Date dueDate = apiFormat.parse(selectedDueDate);
                if (dueDate != null && dueDate.before(currentDate)) {
                    Toast.makeText(this, "Due date cannot be in the past", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing dates for validation", e);
        }
        
        // Create full description with metadata
        String fullDescription = "Type: " + (selectedTaskType != null ? selectedTaskType : "Not specified") + 
                               "\nStart Date: " + selectedStartDate + 
                               "\n\n" + description;
        
        // Disable button to prevent multiple submissions
        updateTaskButton.setEnabled(false);
        updateTaskButton.setText("Updating...");
        
        tasksApiService.updateTask(taskId, title, fullDescription, selectedDueDate, null, 
                new TasksApiService.TaskUpdateCallback() {
            @Override
            public void onSuccess(Task task) {
                // Update the calendar event if it exists
                updateTaskInCalendar(title, fullDescription, selectedDueDate);
                
                runOnUiThread(() -> {
                    Toast.makeText(EditTaskActivity.this, 
                        "Task updated successfully", 
                        Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    updateTaskButton.setEnabled(true);
                    updateTaskButton.setText("Update Task");
                    Toast.makeText(EditTaskActivity.this, 
                        "Error updating task: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                startActivityForResult(intent, REQUEST_AUTHORIZATION);
            }
        });
    }
    
    private void updateTaskInCalendar(String title, String description, String dueDate) {
        // Check calendar permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No WRITE_CALENDAR permission, skipping calendar event update");
            return;
        }
        
        try {
            // Parse due date with time
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = apiFormat.parse(dueDate);
            
            if (date != null) {
                ContentResolver cr = getContentResolver();
                
                // First, try to find an existing event for this task
                // We'll search for events with the original title first
                String originalTaskTitle = "Task: " + originalTitle;
                String newTaskTitle = "Task: " + title;
                
                Log.d(TAG, "Searching for calendar event with title: " + originalTaskTitle);
                
                // Try to find by original title first
                String selection = "(" + CalendarContract.Events.TITLE + " = ?)";
                String[] selectionArgs = new String[] {originalTaskTitle};
                
                Cursor cursor = cr.query(
                    CalendarContract.Events.CONTENT_URI,
                    new String[] {CalendarContract.Events._ID},
                    selection,
                    selectionArgs,
                    null
                );
                
                long eventId = -1;
                if (cursor != null && cursor.moveToFirst()) {
                    eventId = cursor.getLong(0);
                    cursor.close();
                    Log.d(TAG, "Found existing calendar event with ID: " + eventId);
                } else {
                    if (cursor != null) {
                        cursor.close();
                    }
                    
                    // If not found by original title, try with new title
                    // (in case it was already updated elsewhere)
                    selectionArgs = new String[] {newTaskTitle};
                    cursor = cr.query(
                        CalendarContract.Events.CONTENT_URI,
                        new String[] {CalendarContract.Events._ID},
                        selection,
                        selectionArgs,
                        null
                    );
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        eventId = cursor.getLong(0);
                        cursor.close();
                        Log.d(TAG, "Found existing calendar event with new title, ID: " + eventId);
                    } else if (cursor != null) {
                        cursor.close();
                    }
                }
                
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.TITLE, newTaskTitle);
                values.put(CalendarContract.Events.DESCRIPTION, description);
                
                // Set start time to due date with time
                long startMillis = date.getTime();
                values.put(CalendarContract.Events.DTSTART, startMillis);
                
                // Set end time to 1 hour after due date
                long endMillis = startMillis + (60 * 60 * 1000);
                values.put(CalendarContract.Events.DTEND, endMillis);
                
                if (eventId != -1) {
                    // Update existing event
                    Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                    int rows = cr.update(updateUri, values, null, null);
                    Log.d(TAG, "Updated " + rows + " calendar event(s)");
                } else {
                    // Create new event
                    Log.d(TAG, "No existing calendar event found, creating new one");
                    
                    // Get default calendar ID
                    long calendarId = getDefaultCalendarId();
                    if (calendarId <= 0) {
                        Log.e(TAG, "Invalid calendar ID: " + calendarId + ", skipping calendar event creation");
                        return;
                    }
                    
                    values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                    values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                    values.put(CalendarContract.Events.HAS_ALARM, 1);
                    
                    Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
                    if (uri != null) {
                        // Add reminder
                        ContentValues reminderValues = new ContentValues();
                        reminderValues.put(CalendarContract.Reminders.EVENT_ID, Long.parseLong(uri.getLastPathSegment()));
                        reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                        reminderValues.put(CalendarContract.Reminders.MINUTES, 15);
                        cr.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);
                        
                        Log.d(TAG, "Task added to calendar successfully with ID: " + uri.getLastPathSegment());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating task in calendar", e);
        }
    }
    
    private long getDefaultCalendarId() {
        long calendarId = -1;
        
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
    
    private String extractCleanDescription(String fullDescription) {
        // If the description contains metadata (like task type and start date), extract just the user-visible part
        if (fullDescription == null) return "";
        
        // Look for the double newline that separates metadata from the actual description
        int metadataEnd = fullDescription.indexOf("\n\n");
        if (metadataEnd != -1) {
            return fullDescription.substring(metadataEnd + 2);
        }
        
        return fullDescription;
    }

    private String preserveMetadata(String originalDescription, String newDescription) {
        // This method is no longer needed as we're explicitly building the metadata
        // from the selected task type and start date
        return newDescription;
    }
    
    private void extractMetadataFromDescription(String fullDescription) {
        if (fullDescription == null || fullDescription.isEmpty()) {
            return;
        }
        
        // Extract task type
        Pattern typePattern = Pattern.compile("Type:\\s*([^\\n]+)");
        Matcher typeMatcher = typePattern.matcher(fullDescription);
        if (typeMatcher.find()) {
            originalTaskType = typeMatcher.group(1).trim();
            selectedTaskType = originalTaskType;
        }
        
        // Extract start date
        Pattern startDatePattern = Pattern.compile("Start Date:\\s*([^\\n]+)");
        Matcher startDateMatcher = startDatePattern.matcher(fullDescription);
        if (startDateMatcher.find()) {
            originalStartDate = startDateMatcher.group(1).trim();
            selectedStartDate = originalStartDate;
        }
    }
    
    // Time input validator to ensure valid hour/minute values
    private class TimeInputValidator implements TextWatcher {
        private final EditText editText;
        private final int minValue;
        private final int maxValue;
        
        TimeInputValidator(EditText editText, int minValue, int maxValue) {
            this.editText = editText;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }
        
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Not used
        }
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Not used
        }
        
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
                        // Update the due date time whenever a valid time is entered
                        if (editText == dueHourInput || editText == dueMinuteInput) {
                            updateDueDateTime();
                        } else if (editText == startHourInput || editText == startMinuteInput) {
                            updateStartDateTime();
                        }
                    }
                }
            } catch (NumberFormatException e) {
                editText.setError("Invalid number");
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_AUTHORIZATION) {
            if (resultCode == RESULT_OK) {
                // Retry the operation
                updateTask();
            } else {
                Toast.makeText(this, "Authorization required to update task", Toast.LENGTH_SHORT).show();
                updateTaskButton.setEnabled(true);
                updateTaskButton.setText("Update Task");
            }
        }
    }
}









