package com.example.tracked.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailService {
    private static final String TAG = "EmailService";
    private static final String PREFS_NAME = "TrackedPrefs";
    private static final String KEY_USER_EMAIL = "user_email";
    
    // Email configuration for the app's sending account
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String SENDER_EMAIL = "tracked.app.notifications@gmail.com"; // App email for sending notifications
    private static final String SENDER_PASSWORD = "abcd efgh ijkl mnop"; // Replace with your actual app password
    
    private Context context;
    
    public EmailService(Context context) {
        this.context = context;
    }
    
    /**
     * Sends a deadline reminder email to the user's Google account email
     */
    public void sendDeadlineReminder(String taskTitle, String dueDate, String taskType) {
        // Get the user's email from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String userEmail = prefs.getString(KEY_USER_EMAIL, null);
        
        if (userEmail == null || userEmail.isEmpty()) {
            Log.e(TAG, "Cannot send email: User email not found in preferences");
            return;
        }
        
        Log.d(TAG, "Sending deadline reminder to user email: " + userEmail);
        new SendEmailTask(userEmail, taskTitle, dueDate, taskType).execute();
    }
    
    private class SendEmailTask extends AsyncTask<Void, Void, Boolean> {
        private String recipientEmail;
        private String taskTitle;
        private String dueDate;
        private String taskType;
        
        public SendEmailTask(String recipientEmail, String taskTitle, String dueDate, String taskType) {
            this.recipientEmail = recipientEmail;
            this.taskTitle = taskTitle;
            this.dueDate = dueDate;
            this.taskType = taskType;
        }
        
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                Log.d(TAG, "Preparing to send email to: " + recipientEmail);
                
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", SMTP_HOST);
                props.put("mail.smtp.port", SMTP_PORT);
                
                Session session = Session.getInstance(props,
                    new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                        }
                    });
                
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SENDER_EMAIL));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                message.setSubject("TrackED Reminder: " + taskTitle + " is due soon!");
                
                // Create HTML content for the email
                String htmlContent = 
                    "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;'>" +
                    "<div style='background-color: #03A9F4; padding: 15px; border-radius: 8px 8px 0 0;'>" +
                    "<h1 style='color: white; margin: 0;'>Task Deadline Reminder</h1>" +
                    "</div>" +
                    "<div style='padding: 20px;'>" +
                    "<p>Hello,</p>" +
                    "<p>This is a friendly reminder that your task is approaching its deadline:</p>" +
                    "<div style='background-color: #f5f5f5; padding: 15px; border-radius: 8px; margin: 15px 0;'>" +
                    "<h2 style='margin-top: 0; color: #333;'>" + taskTitle + "</h2>" +
                    "<p><strong>Type:</strong> <span style='background-color: #03A9F4; color: white; padding: 3px 8px; border-radius: 12px; font-size: 14px;'>" + 
                    taskType + "</span></p>" +
                    "<p><strong>Due Date:</strong> " + dueDate + "</p>" +
                    "</div>" +
                    "<p>Please make sure to complete this task before the deadline.</p>" +
                    "<p>Thank you for using TrackED!</p>" +
                    "</div>" +
                    "<div style='background-color: #f5f5f5; padding: 15px; border-radius: 0 0 8px 8px; text-align: center; font-size: 12px; color: #666;'>" +
                    "This is an automated message from TrackED. Please do not reply to this email." +
                    "</div>" +
                    "</div>";
                
                message.setContent(htmlContent, "text/html; charset=utf-8");
                
                Transport.send(message);
                Log.d(TAG, "Email sent successfully to " + recipientEmail);
                return true;
            } catch (MessagingException e) {
                Log.e(TAG, "Failed to send email: " + e.getMessage(), e);
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Log.d(TAG, "Email reminder sent successfully");
            } else {
                Log.e(TAG, "Failed to send email reminder");
            }
        }
    }
}

