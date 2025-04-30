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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.text.ParseException;

import com.example.tracked.api.TasksApiService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.Task;

public class AddTaskActivity extends AppCompatActivity {
    private static final String TAG = "AddTaskActivity";
    private static final int REQUEST_AUTHORIZATION = 1001;
    private static final int RC_SIGN_IN = 1002;

    private GoogleSignInClient mGoogleSignInClient;
    private TasksApiService tasksApiService;
    private String selectedStartDate;
    private String selectedDueDate;
    private TextView taskTypeText;
    private String selectedTaskType;
    private MaterialCardView startDateCard;
    private MaterialCardView dueDateCard;
    
    // Add these field declarations
    private EditText taskNameInput;
    private EditText descriptionInput;
    private MaterialButton addTaskButton;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        // Initialize Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(TasksScopes.TASKS))
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize views and other setup
        initializeViews();
        setupTaskTypeDropdown();

        // Check for existing Google Sign In account
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            initializeTasksApiService(account.getEmail());
        } else {
            signIn();
        }
    }

    private void initializeTasksApiService(String email) {
        try {
            tasksApiService = new TasksApiService(this, email);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            Toast.makeText(this, "Error initializing Tasks API: " + e.getMessage(), 
                         Toast.LENGTH_LONG).show();
        }
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            com.google.android.gms.tasks.Task<GoogleSignInAccount> task = 
                GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        } else if (requestCode == REQUEST_AUTHORIZATION) {
            if (resultCode == RESULT_OK) {
                addTask();
            } else {
                Toast.makeText(this, "Authorization required to add tasks", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleSignInResult(com.google.android.gms.tasks.Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getEmail() != null) {
                initializeTasksApiService(account.getEmail());
            }
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            Toast.makeText(this, "Google Sign In failed", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeViews() {
        taskNameInput = findViewById(R.id.taskNameInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        addTaskButton = findViewById(R.id.addTaskButton);
        ImageButton backButton = findViewById(R.id.backButton);

        if (addTaskButton == null) {
            Log.e(TAG, "Failed to find addTaskButton in layout");
            Toast.makeText(this, "Error initializing UI", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
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

    private void showDatePicker(String dateType) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(dateType.equals("start") ? "Select start date" : "Select due date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            try {
                // Format the date for Google Tasks API (RFC 3339)
                SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                
                // Format for display
                SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                String displayDate = displayFormat.format(new Date(selection));

                if (dateType.equals("start")) {
                    selectedStartDate = apiFormat.format(new Date(selection));
                    TextView startDateText = findViewById(R.id.startDateText);
                    startDateText.setText(displayDate);
                } else {
                    selectedDueDate = apiFormat.format(new Date(selection));
                    TextView dueDateText = findViewById(R.id.dueDateText);
                    dueDateText.setText(displayDate);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error formatting date", e);
                Toast.makeText(this, "Error setting date", Toast.LENGTH_SHORT).show();
            }
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER_" + dateType.toUpperCase());
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

        if (selectedStartDate == null) {
            Toast.makeText(this, "Please select a start date", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDueDate == null) {
            Toast.makeText(this, "Please select a due date", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        addTaskButton.setEnabled(false);
        addTaskButton.setText("Adding...");

        // Include task type in description
        String fullDescription = "Type: " + (selectedTaskType != null ? selectedTaskType : "Not specified") + 
                               "\nStart Date: " + selectedStartDate + 
                               "\n\n" + description;

        Log.d("AddTaskActivity", "Adding task - Title: " + title + ", Due: " + selectedDueDate);

        tasksApiService.addTask(title, fullDescription, selectedDueDate, new TasksApiService.TaskCallback() {
            @Override
            public void onSuccess(Task task) {
                runOnUiThread(() -> {
                    Toast.makeText(AddTaskActivity.this, "Task added successfully", Toast.LENGTH_SHORT).show();
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
                });
            }

            @Override
            public void onAuthorizationRequired(Intent intent) {
                startActivityForResult(intent, REQUEST_AUTHORIZATION);
            }
        });
    }
}
