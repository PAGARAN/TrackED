package com.example.tracked.api;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.Task;
import com.google.api.services.tasks.model.TaskList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TasksApiService {
    private static final String TAG = "TasksApiService";
    private static final int REQUEST_AUTHORIZATION = 1002;
    
    private final Context context;
    private Tasks tasksService;
    private final ExecutorService executor;
    private final String accountName;
    private GoogleAccountCredential credential;

    public interface TaskCallback {
        void onSuccess(Task task);
        void onFailure(Exception e);
        void onAuthorizationRequired(Intent intent);
    }

    public interface TaskListCallback {
        void onSuccess(List<Task> tasks);
        void onFailure(Exception e);
    }

    // Interface for task update callback
    public interface TaskUpdateCallback {
        void onSuccess(Task task);
        void onFailure(Exception e);
        void onAuthorizationRequired(Intent intent);
    }
    
    // Interface for task deletion callback
    public interface TaskDeleteCallback {
        void onSuccess();
        void onFailure(Exception e);
        void onAuthorizationRequired(Intent intent);
    }
    
    // Interface for task search callback
    public interface TaskSearchCallback {
        void onSuccess(List<Task> tasks);
        void onFailure(Exception e);
        void onAuthorizationRequired(Intent intent);
    }

    public TasksApiService(Context context, String accountName) throws Exception {
        this.context = context;
        
        // Validate account name to prevent null pointer exceptions
        if (accountName == null || accountName.isEmpty()) {
            Log.e(TAG, "Account name is null or empty");
            throw new IllegalArgumentException("Account name cannot be null or empty");
        }
        
        this.accountName = accountName;
        this.executor = Executors.newSingleThreadExecutor();
        
        try {
            // Initialize credential
            initializeCredential();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            throw new Exception("Failed to initialize Tasks API: " + e.getMessage());
        }
    }
    
    private void initializeCredential() throws Exception {
        // Create credential with explicit account name
        credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(TasksScopes.TASKS)
        );
        
        // Log the account name being used
        Log.d(TAG, "Setting credential with account name: " + accountName);
        credential.setSelectedAccountName(accountName);
        
        // Verify the account exists on the device
        android.accounts.AccountManager accountManager = android.accounts.AccountManager.get(context);
        Account[] accounts = accountManager.getAccountsByType("com.google");
        boolean accountExists = false;
        
        for (Account account : accounts) {
            Log.d(TAG, "Found account: " + account.name);
            if (accountName.equals(account.name)) {
                accountExists = true;
                break;
            }
        }
        
        if (!accountExists) {
            Log.e(TAG, "Account " + accountName + " not found on device");
            throw new Exception("Account not found on device. Please sign in again.");
        }
        
        // Build the Tasks service
        tasksService = new Tasks.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("TrackED")
                .build();
    }

    public void addTask(String title, String description, String dueDate, TaskCallback callback) {
        executor.execute(() -> {
            try {
                // Verify credential is still valid
                if (credential == null || credential.getSelectedAccountName() == null) {
                    throw new IOException("Invalid credential. Please sign in again.");
                }
                
                // Get or create default task list with better error handling
                TaskList defaultList = getOrCreateTaskList();
                if (defaultList == null) {
                    throw new IOException("Failed to get or create task list");
                }

                // Create new task
                Task task = new Task()
                        .setTitle(title)
                        .setNotes(description)
                        .setDue(dueDate)
                        .setStatus("needsAction");

                // Insert the task
                Task createdTask = tasksService.tasks()
                        .insert(defaultList.getId(), task)
                        .execute();

                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(createdTask));
            } catch (UserRecoverableAuthIOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onAuthorizationRequired(e.getIntent()));
            } catch (IOException e) {
                Log.e(TAG, "Error adding task", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onFailure(new Exception("Unexpected error: " + e.getMessage())));
            }
        });
    }

    private TaskList getOrCreateTaskList() throws IOException {
        try {
            // Verify credential is still valid
            if (credential == null || credential.getSelectedAccountName() == null) {
                throw new IOException("Invalid credential. Please sign in again.");
            }
            
            // Log the account name being used
            Log.d(TAG, "Getting task list for account: " + credential.getSelectedAccountName());
            
            // Get all task lists
            com.google.api.services.tasks.model.TaskLists taskLists = tasksService.tasklists().list().execute();
            
            // Check if the response contains items
            if (taskLists == null || taskLists.getItems() == null) {
                Log.d(TAG, "No task lists found, creating a new one");
                TaskList newList = new TaskList();
                newList.setTitle("TrackED");
                return tasksService.tasklists().insert(newList).execute();
            }
            
            // Look for the TrackED task list
            TaskList taskList = taskLists.getItems()
                .stream()
                .filter(list -> "TrackED".equals(list.getTitle()))
                .findFirst()
                .orElse(null);

            if (taskList == null) {
                Log.d(TAG, "TrackED task list not found, creating a new one");
                TaskList newList = new TaskList();
                newList.setTitle("TrackED");
                taskList = tasksService.tasklists().insert(newList).execute();
            }

            return taskList;
        } catch (UserRecoverableAuthIOException e) {
            Log.e(TAG, "Authorization required", e);
            throw e; // Propagate auth exceptions to be handled in calling methods
        } catch (Exception e) {
            Log.e(TAG, "Error in getOrCreateTaskList", e);
            throw new IOException("Failed to get or create task list: " + e.getMessage(), e);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void getTasks(TaskListCallback callback) {
        executor.execute(() -> {
            try {
                // Verify credential is still valid
                if (credential == null || credential.getSelectedAccountName() == null) {
                    throw new IOException("Invalid credential. Please sign in again.");
                }
                
                Log.d(TAG, "Attempting to fetch tasks...");
                TaskList defaultList = getOrCreateTaskList();
                if (defaultList == null) {
                    Log.e(TAG, "Failed to get or create task list");
                    throw new IOException("Failed to get or create task list");
                }
                Log.d(TAG, "Got task list with ID: " + defaultList.getId());

                List<Task> tasks = tasksService.tasks()
                        .list(defaultList.getId())
                        .execute()
                        .getItems();

                if (tasks == null) {
                    Log.d(TAG, "No tasks found, creating empty list");
                    tasks = new ArrayList<>();
                } else {
                    Log.d(TAG, "Found " + tasks.size() + " tasks");
                }

                final List<Task> finalTasks = tasks;
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(finalTasks));
            } catch (UserRecoverableAuthIOException e) {
                Log.e(TAG, "Authorization required for Tasks API", e);
                if (context instanceof Activity) {
                    ((Activity) context).startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                }
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onFailure(new Exception("Authorization required")));
            } catch (Exception e) {
                Log.e(TAG, "Error getting tasks", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            }
        });
    }

    // Update an existing task
    public void updateTask(String taskId, String title, String description, String dueDate, String status, TaskUpdateCallback callback) {
        executor.execute(() -> {
            try {
                // Verify credential is still valid
                if (credential == null || credential.getSelectedAccountName() == null) {
                    throw new IOException("Invalid credential. Please sign in again.");
                }
                
                // Get task list
                TaskList defaultList = getOrCreateTaskList();
                if (defaultList == null) {
                    throw new IOException("Failed to get or create task list");
                }
                
                // Get the existing task
                Task existingTask = tasksService.tasks().get(defaultList.getId(), taskId).execute();
                if (existingTask == null) {
                    throw new IOException("Task not found");
                }
                
                // Update task fields
                if (title != null) existingTask.setTitle(title);
                if (description != null) existingTask.setNotes(description);
                if (dueDate != null) existingTask.setDue(dueDate);
                if (status != null) existingTask.setStatus(status);
                
                // Update the task
                Task updatedTask = tasksService.tasks()
                        .update(defaultList.getId(), taskId, existingTask)
                        .execute();
                
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(updatedTask));
            } catch (UserRecoverableAuthIOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onAuthorizationRequired(e.getIntent()));
            } catch (IOException e) {
                Log.e(TAG, "Error updating task", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onFailure(new Exception("Unexpected error: " + e.getMessage())));
            }
        });
    }
    
    // Delete a task
    public void deleteTask(String taskId, TaskDeleteCallback callback) {
        executor.execute(() -> {
            try {
                // Verify credential is still valid
                if (credential == null || credential.getSelectedAccountName() == null) {
                    throw new IOException("Invalid credential. Please sign in again.");
                }
                
                // Get task list
                TaskList defaultList = getOrCreateTaskList();
                if (defaultList == null) {
                    throw new IOException("Failed to get or create task list");
                }
                
                // Delete the task
                tasksService.tasks().delete(defaultList.getId(), taskId).execute();
                
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess());
            } catch (UserRecoverableAuthIOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onAuthorizationRequired(e.getIntent()));
            } catch (IOException e) {
                Log.e(TAG, "Error deleting task", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onFailure(new Exception("Unexpected error: " + e.getMessage())));
            }
        });
    }
    
    // Search for tasks by title or description
    public void searchTasks(String query, TaskSearchCallback callback) {
        executor.execute(() -> {
            try {
                // Verify credential is still valid
                if (credential == null || credential.getSelectedAccountName() == null) {
                    throw new IOException("Invalid credential. Please sign in again.");
                }
                
                // Get task list
                TaskList defaultList = getOrCreateTaskList();
                if (defaultList == null) {
                    throw new IOException("Failed to get or create task list");
                }
                
                // Get all tasks
                List<Task> tasks = tasksService.tasks()
                        .list(defaultList.getId())
                        .execute()
                        .getItems();
                
                if (tasks == null) {
                    tasks = new ArrayList<>();
                }
                
                // Filter tasks by query
                String queryLower = query.toLowerCase();
                List<Task> filteredTasks = tasks.stream()
                        .filter(task -> 
                            (task.getTitle() != null && task.getTitle().toLowerCase().contains(queryLower)) ||
                            (task.getNotes() != null && task.getNotes().toLowerCase().contains(queryLower)))
                        .collect(Collectors.toList());
                
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(filteredTasks));
            } catch (UserRecoverableAuthIOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onAuthorizationRequired(e.getIntent()));
            } catch (IOException e) {
                Log.e(TAG, "Error searching tasks", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onFailure(new Exception("Unexpected error: " + e.getMessage())));
            }
        });
    }
    
    // Mark a task as completed
    public void completeTask(String taskId, TaskUpdateCallback callback) {
        updateTask(taskId, null, null, null, "completed", callback);
    }
    
    // Mark a task as not completed
    public void uncompleteTask(String taskId, TaskUpdateCallback callback) {
        updateTask(taskId, null, null, null, "needsAction", callback);
    }
}














