package com.example.tracked.utils;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.example.tracked.services.DeadlineCheckerService;
import com.example.tracked.services.StartDateNotificationService;

public class NotificationScheduler {
    private static final String TAG = "NotificationScheduler";
    private static final int DEADLINE_JOB_ID = 1000;
    private static final int START_DATE_JOB_ID = 1001;
    
    public static void scheduleDeadlineChecker(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        
        ComponentName componentName = new ComponentName(context, DeadlineCheckerService.class);
        
        JobInfo jobInfo = new JobInfo.Builder(DEADLINE_JOB_ID, componentName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Needs internet to send emails
            .setPeriodic(24 * 60 * 60 * 1000) // Run once per day
            .setPersisted(true) // Survive reboots
            .build();
        
        int resultCode = jobScheduler.schedule(jobInfo);
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Deadline checker job scheduled successfully");
        } else {
            Log.e(TAG, "Failed to schedule deadline checker job");
        }
    }
    
    public static void scheduleStartDateNotifier(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        
        ComponentName componentName = new ComponentName(context, StartDateNotificationService.class);
        
        JobInfo jobInfo = new JobInfo.Builder(START_DATE_JOB_ID, componentName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(60 * 60 * 1000) // Run once per hour to check for starting tasks
            .setPersisted(true) // Survive reboots
            .build();
        
        int resultCode = jobScheduler.schedule(jobInfo);
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Start date notifier job scheduled successfully");
        } else {
            Log.e(TAG, "Failed to schedule start date notifier job");
        }
    }
    
    public static void cancelDeadlineChecker(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(DEADLINE_JOB_ID);
        Log.d(TAG, "Deadline checker job cancelled");
    }
    
    public static void cancelStartDateNotifier(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(START_DATE_JOB_ID);
        Log.d(TAG, "Start date notifier job cancelled");
    }
    
    public static void scheduleAllNotifications(Context context) {
        scheduleDeadlineChecker(context);
        scheduleStartDateNotifier(context);
        Log.d(TAG, "All notification jobs scheduled");
    }
    
    public static void cancelAllNotifications(Context context) {
        cancelDeadlineChecker(context);
        cancelStartDateNotifier(context);
        Log.d(TAG, "All notification jobs cancelled");
    }
}
