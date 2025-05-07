package com.example.tracked.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.example.tracked.DashboardActivity;
import com.example.tracked.R;
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

public class StartDateNotificationService extends JobService {
    private static final String TAG = "StartDateNotifService";
    private static final String PREFS_NAME = "TrackedPrefs";
    private static final String KEY_LAST_START_NOTIFICATION_TIME = "last_start_notification_time";
    private static final String CHANNEL_ID = "start_date_channel";
    private static final int NOTIFICATION_ID = 2000;

    private boolean jobCancelled = false;
    private TasksApiService tasksApiService;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Start date notification job started");

        // Create notification channel for Android O and above
        createNotificationChannel();

        // Get user email from shared preferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String userEmail = prefs.getString("user_email", null);

        if (userEmail == null) {
            Log.e(TAG, "User email not found, cannot check start dates");
            return false;
        }

        // Initialize Tasks API service
        try {
            tasksApiService = new TasksApiService(this, userEmail);
            checkStartDates(params, userEmail);
            return true; // Job is still running in background
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TasksApiService", e);
            return false; // Job failed
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Start date notification job stopped");
        jobCancelled = true;
        return true; // Reschedule job if it fails
    }

    private void checkStartDates(JobParameters params, String userEmail) {
        new Thread(() -> {
            if (jobCancelled) return;

            // Check if we've sent notifications recently (limit to once per hour)
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastNotificationTime = prefs.getLong(KEY_LAST_START_NOTIFICATION_TIME, 0);
            long currentTime = System.currentTimeMillis();

            // Only send notifications once per hour
            if (currentTime - lastNotificationTime < 60 * 60 * 1000) {
                Log.d(TAG, "Skipping start date check - already checked within the last hour");
                jobFinished(params, false);
                return;
            }

            Log.d(TAG, "Checking for tasks with approaching start dates for user: " + userEmail);

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

                    Calendar calendar = Calendar.getInstance();
                    Date now = calendar.getTime();

                    // Add a small buffer (15 minutes) to catch tasks that are about to start
                    calendar.add(Calendar.MINUTE, 15);
                    Date nearFuture = calendar.getTime();

                    boolean sentAnyNotification = false;

                    for (Task task : taskList) {
                        if ("completed".equals(task.getStatus())) {
                            continue; // Skip completed tasks
                        }

                        // Extract start date from notes
                        String startDate = null;
                        if (task.getNotes() != null) {
                            Pattern startDatePattern = Pattern.compile("Start Date:\\s*([^\\n]+)");
                            Matcher startDateMatcher = startDatePattern.matcher(task.getNotes());
                            if (startDateMatcher.find()) {
                                startDate = startDateMatcher.group(1).trim();
                            }
                        }

                        if (startDate != null && !startDate.isEmpty()) {
                            try {
                                Date taskStartDate = apiFormat.parse(startDate);

                                // Check if task is starting now or in the next 15 minutes
                                if (taskStartDate != null &&
                                    taskStartDate.after(now) &&
                                    taskStartDate.before(nearFuture)) {

                                    Log.d(TAG, "Task starting soon: " + task.getTitle());

                                    // Extract task type from notes
                                    String taskType = "Task";
                                    if (task.getNotes() != null) {
                                        Pattern typePattern = Pattern.compile("Type:\\s*([^\\n]+)");
                                        Matcher typeMatcher = typePattern.matcher(task.getNotes());
                                        if (typeMatcher.find()) {
                                            taskType = typeMatcher.group(1).trim();
                                        }
                                    }

                                    // Send notification
                                    sendStartDateNotification(task.getTitle(), taskType);

                                    sentAnyNotification = true;
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing start date: " + startDate, e);
                            }
                        }
                    }

                    if (sentAnyNotification) {
                        // Update last notification time
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putLong(KEY_LAST_START_NOTIFICATION_TIME, currentTime);
                        editor.apply();
                        Log.d(TAG, "Updated last start date notification time");
                    }

                    jobFinished(params, false);
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Failed to fetch tasks for start date checking", e);
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

    private void sendStartDateNotification(String taskTitle, String taskType) {
        // Create an intent to open the app when notification is tapped
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_today) // Using existing calendar icon
                .setContentTitle("Task Starting Now")
                .setContentText(taskTitle + " (" + taskType + ") is starting now")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Your task \"" + taskTitle + "\" (" + taskType + ") is scheduled to start now."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Show the notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Start date notification sent for: " + taskTitle);
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Start Date Notifications";
            String description = "Notifications for when tasks are scheduled to start";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}

