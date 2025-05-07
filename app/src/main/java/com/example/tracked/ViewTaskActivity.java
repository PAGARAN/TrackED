package com.example.tracked;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tracked.api.TasksApiService;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.api.services.tasks.model.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.example.tracked.utils.DateTimeUtils;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ViewTaskActivity extends AppCompatActivity {
    private static final String TAG = "ViewTaskActivity";
    
    private TextView titleTextView;
    private TextView descriptionTextView;
    private TextView dueDateTextView;
    private TextView startDateTextView;
    private TextView typeTextView;
    private TextView statusTextView;
    private Button editButton;
    private Button deleteButton;
    private Button completeButton;
    private MaterialToolbar toolbar;
    
    private String taskId;
    private String taskTitle;
    private String taskDescription;
    private String taskDueDate;
    private String taskStatus;
    
    private TasksApiService tasksApiService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_task);
        
        // Initialize views
        titleTextView = findViewById(R.id.taskTitleText);
        descriptionTextView = findViewById(R.id.taskDescriptionText);
        dueDateTextView = findViewById(R.id.taskDueDateText);
        startDateTextView = findViewById(R.id.taskStartDateText);
        typeTextView = findViewById(R.id.taskTypeText);
        statusTextView = findViewById(R.id.taskStatusText);
        editButton = findViewById(R.id.editTaskButton);
        deleteButton = findViewById(R.id.deleteTaskButton);
        completeButton = findViewById(R.id.completeTaskButton);
        toolbar = findViewById(R.id.toolbar);
        
        // Set up toolbar
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Get task details from intent
        Intent intent = getIntent();
        if (intent != null) {
            taskId = intent.getStringExtra("TASK_ID");
            taskTitle = intent.getStringExtra("TASK_TITLE");
            taskDescription = intent.getStringExtra("TASK_DESCRIPTION");
            taskDueDate = intent.getStringExtra("TASK_DUE_DATE");
            taskStatus = intent.getStringExtra("TASK_STATUS");
            
            // Set task details to views
            displayTaskDetails();
        }
        
        // Initialize Tasks API service
        String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
        
        if (userEmail != null) {
            try {
                tasksApiService = new TasksApiService(this, userEmail);
            } catch (Exception e) {
                Toast.makeText(this, "Error initializing Tasks API", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            finish();
        }
        
        // Set up button click listeners
        editButton.setOnClickListener(v -> {
            Intent editIntent = new Intent(this, EditTaskActivity.class);
            editIntent.putExtra("TASK_ID", taskId);
            editIntent.putExtra("TASK_TITLE", taskTitle);
            editIntent.putExtra("TASK_DESCRIPTION", taskDescription);
            editIntent.putExtra("TASK_DUE_DATE", taskDueDate);
            startActivityForResult(editIntent, 1001);
        });
        
        deleteButton.setOnClickListener(v -> {
            // Show confirmation dialog
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteTask();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        
        completeButton.setOnClickListener(v -> {
            if ("completed".equals(taskStatus)) {
                uncompleteTask();
            } else {
                completeTask();
            }
        });
    }
    
    private void displayTaskDetails() {
        // Set task title
        titleTextView.setText(taskTitle);
        
        // Extract metadata from notes
        String taskType = "Task";
        String startDate = null;
        String cleanDescription = taskDescription;
        
        if (taskDescription != null && !taskDescription.isEmpty()) {
            // Extract type and start date from notes metadata
            Pattern typePattern = Pattern.compile("Type:\\s*([^\\n]+)");
            Pattern startDatePattern = Pattern.compile("Start Date:\\s*([^\\n]+)");
            
            Matcher typeMatcher = typePattern.matcher(taskDescription);
            if (typeMatcher.find()) {
                taskType = typeMatcher.group(1).trim();
            }
            
            Matcher startDateMatcher = startDatePattern.matcher(taskDescription);
            if (startDateMatcher.find()) {
                startDate = startDateMatcher.group(1).trim();
            }
            
            // Extract clean description (after metadata)
            int metadataEnd = taskDescription.indexOf("\n\n");
            if (metadataEnd != -1) {
                cleanDescription = taskDescription.substring(metadataEnd + 2);
            }
        }
        
        // Set description
        descriptionTextView.setText(cleanDescription);
        
        // Set task type
        typeTextView.setText(taskType);
        
        // Set background color based on task type
        int colorResId;
        switch (taskType.toLowerCase()) {
            case "assignment":
                colorResId = android.R.color.holo_red_light;
                break;
            case "quiz":
                colorResId = android.R.color.holo_orange_light;
                break;
            case "exam":
                colorResId = android.R.color.holo_red_dark;
                break;
            case "project":
                colorResId = android.R.color.holo_purple;
                break;
            case "reading":
                colorResId = android.R.color.holo_green_light;
                break;
            default:
                colorResId = android.R.color.holo_blue_light;
                break;
        }
        GradientDrawable drawable = (GradientDrawable) typeTextView.getBackground();
        drawable.setColor(getResources().getColor(colorResId, getTheme()));
        
        // Format and set due date
        if (taskDueDate != null && !taskDueDate.isEmpty()) {
            dueDateTextView.setText(DateTimeUtils.apiDateToDisplayDateTime(taskDueDate));
        } else {
            dueDateTextView.setText("No due date");
        }
        
        // Format and set start date
        if (startDate != null && !startDate.isEmpty()) {
            startDateTextView.setText(DateTimeUtils.apiDateToDisplayDateTime(startDate));
            startDateTextView.setVisibility(View.VISIBLE);
        } else {
            startDateTextView.setVisibility(View.GONE);
        }
        
        // Set status
        statusTextView.setText("completed".equals(taskStatus) ? "Completed" : "Not Completed");
        
        // Update complete button text
        completeButton.setText("completed".equals(taskStatus) ? "Mark as Not Completed" : "Mark as Completed");
    }
    
    private void deleteTask() {
        if (tasksApiService == null) return;
        
        tasksApiService.deleteTask(taskId, new TasksApiService.TaskDeleteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ViewTaskActivity.this, "Task deleted", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ViewTaskActivity.this, "Error deleting task: " + e.getMessage(), 
                                 Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                startActivityForResult(intent, 1002);
            }
        });
    }
    
    private void completeTask() {
        if (tasksApiService == null) return;
        
        tasksApiService.completeTask(taskId, new TasksApiService.TaskUpdateCallback() {
            @Override
            public void onSuccess(Task task) {
                runOnUiThread(() -> {
                    Toast.makeText(ViewTaskActivity.this, "Task marked as completed", Toast.LENGTH_SHORT).show();
                    taskStatus = "completed";
                    displayTaskDetails();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ViewTaskActivity.this, "Error updating task: " + e.getMessage(), 
                                 Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                startActivityForResult(intent, 1003);
            }
        });
    }
    
    private void uncompleteTask() {
        if (tasksApiService == null) return;
        
        tasksApiService.uncompleteTask(taskId, new TasksApiService.TaskUpdateCallback() {
            @Override
            public void onSuccess(Task task) {
                runOnUiThread(() -> {
                    Toast.makeText(ViewTaskActivity.this, "Task marked as not completed", Toast.LENGTH_SHORT).show();
                    taskStatus = "needsAction";
                    displayTaskDetails();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ViewTaskActivity.this, "Error updating task: " + e.getMessage(), 
                                 Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                startActivityForResult(intent, 1004);
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            if (requestCode == 1001) {
                // Task was edited, refresh the details
                setResult(RESULT_OK);
                finish();
            } else if (requestCode >= 1002 && requestCode <= 1004) {
                // Authorization was handled, retry the operation
                if (requestCode == 1002) {
                    deleteTask();
                } else if (requestCode == 1003) {
                    completeTask();
                } else if (requestCode == 1004) {
                    uncompleteTask();
                }
            }
        }
    }
}



