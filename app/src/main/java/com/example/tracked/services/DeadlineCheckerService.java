package com.example.tracked.services;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.tracked.api.TasksApiService;
import com.google.api.services.tasks.model.Task;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeadlineCheckerService extends JobService {
    private static final String TAG = "DeadlineCheckerService";
    private static final String PREFS_NAME = "TrackedPrefs";
    private static final String KEY_LAST_NOTIFICATION_TIME = "last_notification_time";

    private boolean jobCancelled = false;
    private TasksApiService tasksApiService;
    private EmailService emailService;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Deadline checker job started");
        emailService = new EmailService(this);

        // Get user email from shared preferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String userEmail = prefs.getString("user_email", null);

        if (userEmail == null) {
            Log.e(TAG, "User email not found, cannot check deadlines");
            return false;
        }

        // Initialize Tasks API service
        try {
            tasksApiService = new TasksApiService(this, userEmail);
            checkDeadlines(params, userEmail);
            return true; // Job is still running in background
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            return false; // Job failed
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Deadline checker job stopped");
        jobCancelled = true;
        return true; // Reschedule job if it fails
    }

    private void checkDeadlines(JobParameters params, String userEmail) {
        new Thread(() -> {
            if (jobCancelled) return;

            // Check if we've sent notifications recently (limit to once per day)
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastNotificationTime = prefs.getLong(KEY_LAST_NOTIFICATION_TIME, 0);
            long currentTime = System.currentTimeMillis();

            // Save the user's email for the EmailService to use
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("user_email", userEmail);
            editor.apply();

            // Only send notifications once per day
            if (currentTime - lastNotificationTime < 24 * 60 * 60 * 1000) {
                Log.d(TAG, "Skipping notification check - already sent today");
                jobFinished(params, false);
                return;
            }

            Log.d(TAG, "Checking for tasks with approaching deadlines for user: " + userEmail);

            tasksApiService.getTasks(new TasksApiService.TaskListCallback() {
                @Override
                public void onSuccess(List<Task> taskList) {
                    if (jobCancelled) return;

                    Log.d(TAG, "Retrieved " + (taskList != null ? taskList.size() : 0) + " tasks");

                    if (taskList == null || taskList.isEmpty()) {
                        Log.d(TAG, "No tasks found to check");
                        jobFinished(params, false);
                        return;
                    }

                    SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                    SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);

                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DAY_OF_YEAR, 2); // Check for tasks due in the next 2 days
                    Date twoDaysFromNow = calendar.getTime();

                    Log.d(TAG, "Checking for tasks due before: " + displayFormat.format(twoDaysFromNow));

                    boolean sentAnyNotification = false;

                    for (Task task : taskList) {
                        if ("completed".equals(task.getStatus())) {
                            Log.d(TAG, "Skipping completed task: " + task.getTitle());
                            continue; // Skip completed tasks
                        }

                        String dueDate = task.getDue();
                        if (dueDate != null && !dueDate.isEmpty()) {
                            try {
                                Log.d(TAG, "Task: " + task.getTitle() + ", Due: " + dueDate);
                                Date taskDueDate = apiFormat.parse(dueDate);

                                // Check if task is due within the next 2 days
                                if (taskDueDate != null && taskDueDate.before(twoDaysFromNow)) {
                                    Log.d(TAG, "Task due soon: " + task.getTitle() + " on " + displayFormat.format(taskDueDate));

                                    // Extract task type from notes
                                    String taskType = "Task";
                                    if (task.getNotes() != null) {
                                        Pattern typePattern = Pattern.compile("Type:\\s*([^\\n]+)");
                                        Matcher typeMatcher = typePattern.matcher(task.getNotes());
                                        if (typeMatcher.find()) {
                                            taskType = typeMatcher.group(1).trim();
                                        }
                                    }

                                    Log.d(TAG, "Sending email reminder for task: " + task.getTitle());

                                    // Send email notification - now we don't pass the userEmail parameter
                                    // as the EmailService will get it from SharedPreferences
                                    emailService.sendDeadlineReminder(
                                        task.getTitle(),
                                        displayFormat.format(taskDueDate),
                                        taskType
                                    );

                                    sentAnyNotification = true;
                                    Log.d(TAG, "Sent reminder for task: " + task.getTitle());
                                } else {
                                    Log.d(TAG, "Task not due soon: " + task.getTitle());
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing due date: " + dueDate, e);
                            }
                        } else {
                            Log.d(TAG, "Task has no due date: " + task.getTitle());
                        }
                    }

                    if (sentAnyNotification) {
                        // Update last notification time
                        editor.putLong(KEY_LAST_NOTIFICATION_TIME, currentTime);
                        editor.apply();
                        Log.d(TAG, "Updated last notification time");
                    } else {
                        Log.d(TAG, "No notifications sent - no tasks due soon");
                    }

                    jobFinished(params, false);
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Failed to fetch tasks for deadline checking", e);
                    jobFinished(params, true); // Retry later
                }

                @Override
                public void onAuthorizationRequired(Intent intent) {
                    Log.d(TAG, "Authorization required for Tasks API");
                    // Since this is a background service, we can't directly start an activity
                    // We'll just log the error and retry later
                    jobFinished(params, true);
                }
            });
        }).start();
    }
}



