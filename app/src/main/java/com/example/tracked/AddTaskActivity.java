package com.example.tracked;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AddTaskActivity extends AppCompatActivity {
    private EditText taskNameInput;
    private EditText descriptionInput;
    private Button addTaskButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        // Initialize views
        taskNameInput = findViewById(R.id.taskNameInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        addTaskButton = findViewById(R.id.addTaskButton);
        ImageButton backButton = findViewById(R.id.backButton);

        // Set up back button
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Set up add task button
        if (addTaskButton != null) {
            addTaskButton.setOnClickListener(v -> {
                try {
                    String title = taskNameInput.getText().toString().trim();
                    String description = descriptionInput.getText().toString().trim();
                    
                    if (!title.isEmpty()) {
                        // Add your task creation logic here
                        Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        taskNameInput.setError("Title is required");
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
