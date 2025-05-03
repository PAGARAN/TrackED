package com.example.tracked;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.api.TasksApiService;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.services.tasks.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskListFragment extends Fragment {

    private RecyclerView taskListRecyclerView;
    private TaskDetailAdapter taskAdapter;
    private MaterialCardView completedTab, todoTab, archiveTab;
    private TasksApiService tasksApiService;
    
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
        taskListRecyclerView.setAdapter(taskAdapter);
        
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
}










