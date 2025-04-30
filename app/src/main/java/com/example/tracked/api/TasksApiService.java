package com.example.tracked.api;

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

public class TasksApiService {
    private static final String TAG = "TasksApiService";
    private static final int REQUEST_AUTHORIZATION = 1002;
    
    private final Context context;
    private final Tasks tasksService;
    private final ExecutorService executor;

    public interface TaskCallback {
        void onSuccess(Task task);
        void onFailure(Exception e);
        void onAuthorizationRequired(Intent intent);
    }

    public interface TaskListCallback {
        void onSuccess(List<Task> tasks);
        void onFailure(Exception e);
    }

    public TasksApiService(Context context, String accountName) throws Exception {
        this.context = context;
        try {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    Collections.singleton(TasksScopes.TASKS)
            ).setSelectedAccountName(accountName);

            // Build the Tasks service
            tasksService = new Tasks.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName("TrackED")
                    .build();

            executor = Executors.newSingleThreadExecutor();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            throw new Exception("Failed to initialize Tasks API: " + e.getMessage());
        }
    }

    public void addTask(String title, String description, String dueDate, TaskCallback callback) {
        executor.execute(() -> {
            try {
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
            TaskList taskList = tasksService.tasklists()
                .list()
                .execute()
                .getItems()
                .stream()
                .filter(list -> "TrackED".equals(list.getTitle()))
                .findFirst()
                .orElse(null);

            if (taskList == null) {
                TaskList newList = new TaskList();
                newList.setTitle("TrackED");
                taskList = tasksService.tasklists().insert(newList).execute();
            }

            return taskList;
        } catch (UserRecoverableAuthIOException e) {
            throw e; // Propagate auth exceptions to be handled in getTasks
        } catch (Exception e) {
            Log.e(TAG, "Error in getOrCreateTaskList", e);
            throw new IOException("Failed to get or create task list: " + e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void getTasks(TaskListCallback callback) {
        executor.execute(() -> {
            try {
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
            } catch (Exception e) {
                Log.e(TAG, "Error getting tasks", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure(e));
            }
        });
    }
}





