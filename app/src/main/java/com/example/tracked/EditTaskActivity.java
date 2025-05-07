package com.example.tracked;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tracked.api.TasksApiService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.api.services.tasks.model.Task;
import com.example.tracked.utils.DateTimeUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class EditTaskActivity extends AppCompatActivity {
    private static final String TAG = "EditTaskActivity";
    private static final int REQUEST_AUTHORIZATION = 1001;
    
    private TasksApiService tasksApiService;
    private String taskId;
    private String selectedDueDate;
    private EditText taskNameInput;
    private EditText descriptionInput;
    private MaterialButton updateTaskButton;
    private MaterialCardView dueDateCard;
    private TextView dueDateText;
    private String originalTitle;
    private String originalDescription;
    private String originalDueDate;
    private EditText dueHourInput, dueMinuteInput;
    private String selectedDueDateOnly;
    
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
        
        // Check if time input fields exist before trying to use them
        dueHourInput = findViewById(R.id.dueHourInput);
        dueMinuteInput = findViewById(R.id.dueMinuteInput);
        
        ImageButton backButton = findViewById(R.id.backButton);
        
        // Add text change listeners only if the views exist
        if (dueHourInput != null && dueMinuteInput != null) {
            dueHourInput.addTextChangedListener(new TimeInputValidator(dueHourInput, 0, 23));
            dueMinuteInput.addTextChangedListener(new TimeInputValidator(dueMinuteInput, 0, 59));
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
            
            // Extract the actual description from the notes (remove metadata)
            if (originalDescription != null) {
                String cleanDescription = extractCleanDescription(originalDescription);
                descriptionInput.setText(cleanDescription);
            }
            
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
        }
        
        // Set up click listeners
        dueDateCard.setOnClickListener(v -> showDatePicker());
        
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
    
    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select due date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
                
        datePicker.addOnPositiveButtonClickListener(selection -> {
            try {
                // Create a Date object from the selection
                Date selectedDate = new Date(selection);
                
                // Format for display using our utility class (which uses Philippine time)
                String displayDate = DateTimeUtils.formatToDisplayDateTime(selectedDate);
                dueDateText.setText(displayDate);
                
                // Store the selected date (without time)
                SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                dateOnlyFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); // Set to Philippine timezone
                selectedDueDateOnly = dateOnlyFormat.format(selectedDate);
                
                // Update the combined date and time
                updateDueDateTime();
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
        
        // Preserve metadata from original description
        String fullDescription = preserveMetadata(originalDescription, description);
        
        // Disable button to prevent multiple submissions
        updateTaskButton.setEnabled(false);
        updateTaskButton.setText("Updating...");
        
        tasksApiService.updateTask(taskId, title, fullDescription, selectedDueDate, null, 
                new TasksApiService.TaskUpdateCallback() {
            @Override
            public void onSuccess(Task task) {
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
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_AUTHORIZATION) {
            if (resultCode == RESULT_OK) {
                // Re-initialize the Tasks API service with the current account
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null && account.getEmail() != null) {
                    initializeTasksApiService(account.getEmail());
                    updateTask();
                } else {
                    Toast.makeText(this, "Failed to get account after authorization", 
                                 Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Authorization required to update tasks", 
                             Toast.LENGTH_LONG).show();
            }
        }
    }
    
    // Extract clean description from the notes (remove metadata)
    private String extractCleanDescription(String notes) {
        if (notes == null) return "";
        
        // Find the position after the metadata section
        int metadataEnd = notes.indexOf("\n\n");
        if (metadataEnd != -1) {
            return notes.substring(metadataEnd + 2);
        }
        
        return notes;
    }
    
    // Preserve metadata from original description
    private String preserveMetadata(String originalNotes, String newDescription) {
        if (originalNotes == null) {
            // If there was no original description, create a basic one
            return "Type: Not specified\nPriority: Medium\nStart Date: " + selectedDueDate + "\n\n" + newDescription;
        }
        
        // Find the position after the metadata section
        int metadataEnd = originalNotes.indexOf("\n\n");
        if (metadataEnd != -1) {
            // Keep the original metadata and replace the description
            return originalNotes.substring(0, metadataEnd + 2) + newDescription;
        }
        
        // If no metadata format was found, create a new one
        return "Type: Not specified\nPriority: Medium\nStart Date: " + selectedDueDate + "\n\n" + newDescription;
    }
    
    // Add TimeInputValidator inner class
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
                        updateDueDateTime();
                    }
                }
            } catch (NumberFormatException e) {
                editText.setError("Invalid number");
            }
        }
    }
}









