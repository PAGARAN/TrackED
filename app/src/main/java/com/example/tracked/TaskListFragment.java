package com.example.tracked;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.api.TasksApiService;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.services.tasks.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskListFragment extends Fragment implements TaskDetailAdapter.TaskActionListener {

    private RecyclerView taskListRecyclerView;
    private TaskDetailAdapter taskAdapter;
    private MaterialCardView completedTab, todoTab, archiveTab;
    private TasksApiService tasksApiService;
    private EditText searchInput;
    private ImageButton searchButton;
    private ProgressBar progressBar;
    
    // Task filter types
    private enum TaskFilter {
        COMPLETED,
        TODO,
        ARCHIVE
    }
    
    private TaskFilter currentFilter = TaskFilter.TODO;

    public TaskListFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_task_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        taskListRecyclerView = view.findViewById(R.id.taskListRecyclerView);
        completedTab = view.findViewById(R.id.completedTab);
        todoTab = view.findViewById(R.id.todoTab);
        archiveTab = view.findViewById(R.id.archiveTab);
        FloatingActionButton fabAddTask = view.findViewById(R.id.fabAddTask);
        searchInput = view.findViewById(R.id.searchInput);
        searchButton = view.findViewById(R.id.searchButton);
        progressBar = view.findViewById(R.id.progressBar);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        
        // Set up menu button
        menuButton.setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                DashboardActivity activity = (DashboardActivity) getActivity();
                activity.openDrawer();
            }
        });
        
        // Set up RecyclerView
        taskListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        taskAdapter = new TaskDetailAdapter();
        taskAdapter.setTaskActionListener(this); // Set this fragment as the action listener
        taskListRecyclerView.setAdapter(taskAdapter);
        
        // Set up search functionality
        searchButton.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                searchTasks(query);
            } else {
                loadTasks(); // Load all tasks if search is empty
            }
        });
        
        // Set up tab click listeners
        completedTab.setOnClickListener(v -> {
            setActiveFilter(TaskFilter.COMPLETED);
            loadTasks();
        });
        
        todoTab.setOnClickListener(v -> {
            setActiveFilter(TaskFilter.TODO);
            loadTasks();
        });
        
        archiveTab.setOnClickListener(v -> {
            setActiveFilter(TaskFilter.ARCHIVE);
            loadTasks();
        });
        
        // Set up FAB click listener
        fabAddTask.setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).startAddTaskActivity();
            }
        });
        
        // Initialize tasks service if available from activity
        if (getActivity() instanceof DashboardActivity) {
            tasksApiService = ((DashboardActivity) getActivity()).getTasksApiService();
            loadTasks();
        }
        
        // Set initial active filter
        setActiveFilter(TaskFilter.TODO);
    }
    
    private void setActiveFilter(TaskFilter filter) {
        currentFilter = filter;
        
        // Reset all tabs to inactive state
        completedTab.setCardBackgroundColor(getResources().getColor(android.R.color.white));
        todoTab.setCardBackgroundColor(getResources().getColor(android.R.color.white));
        archiveTab.setCardBackgroundColor(getResources().getColor(android.R.color.white));
        
        // Find TextViews using their IDs with fully qualified class name
        android.widget.TextView completedText = completedTab.findViewById(R.id.completedTabText);
        android.widget.TextView todoText = todoTab.findViewById(R.id.todoTabText);
        android.widget.TextView archiveText = archiveTab.findViewById(R.id.archiveTabText);
        
        // Reset all text colors
        completedText.setTextColor(getResources().getColor(android.R.color.black));
        todoText.setTextColor(getResources().getColor(android.R.color.black));
        archiveText.setTextColor(getResources().getColor(android.R.color.black));
        
        // Set active tab
        switch (filter) {
            case COMPLETED:
                completedTab.setCardBackgroundColor(getResources().getColor(android.R.color.black));
                completedText.setTextColor(getResources().getColor(android.R.color.white));
                break;
            case TODO:
                todoTab.setCardBackgroundColor(getResources().getColor(android.R.color.black));
                todoText.setTextColor(getResources().getColor(android.R.color.white));
                break;
            case ARCHIVE:
                archiveTab.setCardBackgroundColor(getResources().getColor(android.R.color.black));
                archiveText.setTextColor(getResources().getColor(android.R.color.white));
                break;
        }
    }
    
    private void loadTasks() {
        if (tasksApiService == null) {
            Toast.makeText(getContext(), "Tasks service not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tasksApiService.getTasks(new TasksApiService.TaskListCallback() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    List<Task> filteredTasks = filterTasks(tasks);
                    taskAdapter.setTasks(filteredTasks);
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Error loading tasks: " + e.getMessage(), 
                                  Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private List<Task> filterTasks(List<Task> tasks) {
        List<Task> filteredTasks = new ArrayList<>();
        
        for (Task task : tasks) {
            switch (currentFilter) {
                case COMPLETED:
                    if ("completed".equals(task.getStatus())) {
                        filteredTasks.add(task);
                    }
                    break;
                case TODO:
                    if ("needsAction".equals(task.getStatus())) {
                        filteredTasks.add(task);
                    }
                    break;
                case ARCHIVE:
                    // For demo purposes, we'll consider tasks with "hidden" property as archived
                    // In a real app, you might have a different way to identify archived tasks
                    if (task.getHidden() != null && task.getHidden()) {
                        filteredTasks.add(task);
                    }
                    break;
            }
        }
        
        return filteredTasks;
    }
    
    public void refreshTasks() {
        loadTasks();
    }
    
    // Implement TaskActionListener methods
    @Override
    public void onEditTask(Task task) {
        if (getActivity() instanceof DashboardActivity) {
            Intent intent = new Intent(getActivity(), EditTaskActivity.class);
            intent.putExtra("TASK_ID", task.getId());
            intent.putExtra("TASK_TITLE", task.getTitle());
            intent.putExtra("TASK_DESCRIPTION", task.getNotes());
            intent.putExtra("TASK_DUE_DATE", task.getDue());
            getActivity().startActivityForResult(intent, DashboardActivity.EDIT_TASK_REQUEST_CODE);
        }
    }
    
    @Override
    public void onDeleteTask(Task task) {
        if (getContext() == null) return;
        
        // Show confirmation dialog
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete this task?")
            .setPositiveButton("Delete", (dialog, which) -> {
                deleteTask(task.getId());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    @Override
    public void onCompleteTask(Task task) {
        if (tasksApiService == null) return;
        
        showProgress(true);
        tasksApiService.completeTask(task.getId(), new TasksApiService.TaskUpdateCallback() {
            @Override
            public void onSuccess(Task task) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Task marked as completed", Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Error updating task: " + e.getMessage(), 
                                  Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                if (getActivity() == null) return;
                
                getActivity().startActivityForResult(intent, DashboardActivity.REQUEST_AUTHORIZATION);
            }
        });
    }
    
    @Override
    public void onUncompleteTask(Task task) {
        if (tasksApiService == null) return;
        
        showProgress(true);
        tasksApiService.uncompleteTask(task.getId(), new TasksApiService.TaskUpdateCallback() {
            @Override
            public void onSuccess(Task task) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Task marked as not completed", Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Error updating task: " + e.getMessage(), 
                                  Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                if (getActivity() == null) return;
                
                getActivity().startActivityForResult(intent, DashboardActivity.REQUEST_AUTHORIZATION);
            }
        });
    }
    
    // Delete a task
    private void deleteTask(String taskId) {
        if (tasksApiService == null) return;
        
        showProgress(true);
        tasksApiService.deleteTask(taskId, new TasksApiService.TaskDeleteCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Task deleted successfully", Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Error deleting task: " + e.getMessage(), 
                                  Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                if (getActivity() == null) return;
                
                getActivity().startActivityForResult(intent, DashboardActivity.REQUEST_AUTHORIZATION);
            }
        });
    }
    
    // Search for tasks
    private void searchTasks(String query) {
        if (tasksApiService == null) return;
        
        showProgress(true);
        tasksApiService.searchTasks(query, new TasksApiService.TaskSearchCallback() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    taskAdapter.setTasks(tasks);
                    if (tasks.isEmpty()) {
                        Toast.makeText(getContext(), "No tasks found matching your search", 
                                      Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Error searching tasks: " + e.getMessage(), 
                                  Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthorizationRequired(Intent intent) {
                if (getActivity() == null) return;
                
                getActivity().startActivityForResult(intent, DashboardActivity.REQUEST_AUTHORIZATION);
            }
        });
    }
    
    // Show/hide progress indicator
    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}












