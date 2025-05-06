package com.example.tracked.services;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import com.example.tracked.models.CalendarEvent;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CalendarApiService {
    private static final String TAG = "CalendarApiService";
    private static final int REQUEST_AUTHORIZATION = 1002;
    private Context context;
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private Calendar googleCalendarService;
    private Executor executor = Executors.newSingleThreadExecutor();
    private boolean useGoogleCalendar = false;
    private EventListCallback pendingCallback;
    private String pendingDateString;

    public CalendarApiService(Context context) {
        this.context = context;
        apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        // Try to initialize Google Calendar service
        initGoogleCalendarService();
    }

    private void initGoogleCalendarService() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account != null) {
            try {
                Log.d(TAG, "Initializing Google Calendar service with account: " + account.getEmail());
                
                // Check if we have the Calendar scope
                boolean hasCalendarScope = false;
                if (account.getGrantedScopes() != null) {
                    for (Scope scope : account.getGrantedScopes()) {
                        Log.d(TAG, "Account has scope: " + scope.toString());
                        if (scope.toString().contains("calendar")) {
                            hasCalendarScope = true;
                            break;
                        }
                    }
                }
                
                if (!hasCalendarScope) {
                    Log.w(TAG, "Account does not have Calendar scope, need to request it");
                    useGoogleCalendar = false;
                    return;
                }
                
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singletonList(CalendarScopes.CALENDAR_READONLY));
                credential.setSelectedAccount(account.getAccount());

                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                googleCalendarService = new Calendar.Builder(transport, jsonFactory, credential)
                        .setApplicationName("TrackED")
                        .build();
                
                useGoogleCalendar = true;
                Log.d(TAG, "Google Calendar service initialized for: " + account.getEmail());
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Google Calendar service", e);
                useGoogleCalendar = false;
            }
        } else {
            Log.d(TAG, "No Google account available, using device calendar only");
            useGoogleCalendar = false;
        }
    }

    public interface EventListCallback {
        void onSuccess(List<CalendarEvent> events);
        void onFailure(Exception e);
    }

    public void getEvents(EventListCallback callback) {
        if (useGoogleCalendar) {
            getGoogleCalendarEvents(null, callback);
        } else {
            try {
                // Get events from device calendar
                List<CalendarEvent> events = queryCalendarEvents(null);
                callback.onSuccess(events);
            } catch (Exception e) {
                Log.e(TAG, "Error getting calendar events", e);
                callback.onFailure(e);
            }
        }
    }

    public void getEventsForDate(String date, EventListCallback callback) {
        if (useGoogleCalendar) {
            getGoogleCalendarEventsForDate(date, callback);
        } else {
            // Check for calendar read permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
                    != PackageManager.PERMISSION_GRANTED) {
                callback.onFailure(new SecurityException("READ_CALENDAR permission not granted"));
                return;
            }
            
            try {
                // Parse the date string to get start and end time
                Date targetDate = apiFormat.parse(date);
                if (targetDate == null) {
                    throw new IllegalArgumentException("Invalid date format");
                }
                
                // Create selection for the specific date
                String selection = createDateSelection(targetDate);
                
                // Query events with the date selection
                List<CalendarEvent> events = queryCalendarEvents(selection);
                callback.onSuccess(events);
            } catch (Exception e) {
                Log.e(TAG, "Error getting calendar events for date", e);
                callback.onFailure(e);
            }
        }
    }

    private void getGoogleCalendarEvents(String timeMin, EventListCallback callback) {
        executor.execute(() -> {
            try {
                if (googleCalendarService == null) {
                    throw new IllegalStateException("Google Calendar service not initialized");
                }

                Calendar.Events.List request = googleCalendarService.events().list("primary");
                
                if (timeMin != null) {
                    request.setTimeMin(new DateTime(timeMin));
                }
                
                request.setMaxResults(50);
                request.setOrderBy("startTime");
                request.setSingleEvents(true);
                
                Events events = request.execute();
                List<Event> items = events.getItems();
                
                List<CalendarEvent> calendarEvents = new ArrayList<>();
                for (Event event : items) {
                    CalendarEvent calendarEvent = convertGoogleEventToCalendarEvent(event);
                    if (calendarEvent != null) {
                        calendarEvents.add(calendarEvent);
                    }
                }
                
                callback.onSuccess(calendarEvents);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching Google Calendar events", e);
                callback.onFailure(e);
            }
        });
    }

    private void getGoogleCalendarEventsForDate(String dateString, EventListCallback callback) {
        // Store callback and date for potential retry after authorization
        this.pendingCallback = callback;
        this.pendingDateString = dateString;
        
        executor.execute(() -> {
            try {
                if (googleCalendarService == null) {
                    throw new IllegalStateException("Google Calendar service not initialized");
                }
                
                // Parse the date string
                Date date = apiFormat.parse(dateString);
                if (date == null) {
                    throw new IllegalArgumentException("Invalid date format");
                }
                
                // Create start and end DateTime objects for the day
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                DateTime startDateTime = new DateTime(cal.getTime());
                
                cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
                cal.set(java.util.Calendar.MINUTE, 59);
                cal.set(java.util.Calendar.SECOND, 59);
                DateTime endDateTime = new DateTime(cal.getTime());
                
                // Create request
                Calendar.Events.List request = googleCalendarService.events().list("primary");
                request.setTimeMin(startDateTime);
                request.setTimeMax(endDateTime);
                request.setOrderBy("startTime");
                request.setSingleEvents(true);
                
                Events events = request.execute();
                List<Event> items = events.getItems();
                
                List<CalendarEvent> calendarEvents = new ArrayList<>();
                for (Event event : items) {
                    CalendarEvent calendarEvent = convertGoogleEventToCalendarEvent(event);
                    if (calendarEvent != null) {
                        calendarEvents.add(calendarEvent);
                    }
                }
                
                callback.onSuccess(calendarEvents);
            } catch (UserRecoverableAuthIOException e) {
                Log.e(TAG, "Authorization required for Google Calendar", e);
                // Handle the authorization request
                if (context instanceof Activity) {
                    Activity activity = (Activity) context;
                    activity.runOnUiThread(() -> {
                        try {
                            activity.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error starting authorization activity", ex);
                            callback.onFailure(new Exception("Failed to request authorization: " + ex.getMessage()));
                        }
                    });
                } else {
                    callback.onFailure(new Exception("Calendar authorization required but context is not an Activity"));
                }
            } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                Log.e(TAG, "Google API error", e);
                
                // Check if this is a "API not enabled" error
                if (e.getStatusCode() == 403 && e.getDetails() != null && 
                    e.getDetails().getMessage() != null && 
                    e.getDetails().getMessage().contains("has not been used in project") && 
                    e.getDetails().getMessage().contains("or it is disabled")) {
                    
                    // Fall back to device calendar
                    useGoogleCalendar = false;
                    
                    try {
                        // Parse the date string to get start and end time
                        Date targetDate = apiFormat.parse(dateString);
                        if (targetDate == null) {
                            throw new IllegalArgumentException("Invalid date format");
                        }
                        
                        // Create selection for the specific date
                        String selection = createDateSelection(targetDate);
                        
                        // Query events with the date selection
                        List<CalendarEvent> events = queryCalendarEvents(selection);
                        callback.onSuccess(events);
                    } catch (Exception ex) {
                        callback.onFailure(new Exception("Google Calendar API is not enabled. Using device calendar failed: " + ex.getMessage()));
                    }
                } else {
                    callback.onFailure(new Exception("Google Calendar API error: " + e.getMessage()));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching Google Calendar events for date", e);
                callback.onFailure(e);
            }
        });
    }
    
    // Call this method from the activity's onActivityResult
    public void handleAuthorizationResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            // Re-initialize the service
            initGoogleCalendarService();
            
            // Retry the pending request if we have one
            if (pendingCallback != null && pendingDateString != null) {
                String dateString = pendingDateString;
                EventListCallback callback = pendingCallback;
                
                // Clear pending data
                pendingCallback = null;
                pendingDateString = null;
                
                // Retry the request
                getEventsForDate(dateString, callback);
            }
        } else {
            // Authorization denied, fall back to device calendar
            useGoogleCalendar = false;
            
            if (pendingCallback != null && pendingDateString != null) {
                try {
                    // Parse the date string to get start and end time
                    Date targetDate = apiFormat.parse(pendingDateString);
                    if (targetDate == null) {
                        throw new IllegalArgumentException("Invalid date format");
                    }
                    
                    // Create selection for the specific date
                    String selection = createDateSelection(targetDate);
                    
                    // Query events with the date selection
                    List<CalendarEvent> events = queryCalendarEvents(selection);
                    pendingCallback.onSuccess(events);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting calendar events for date after auth denied", e);
                    pendingCallback.onFailure(e);
                }
                
                // Clear pending data
                pendingCallback = null;
                pendingDateString = null;
            }
        }
    }

    private CalendarEvent convertGoogleEventToCalendarEvent(Event event) {
        try {
            String title = event.getSummary();
            String description = event.getDescription();
            
            // Handle start time
            DateTime startDateTime = null;
            if (event.getStart().getDateTime() != null) {
                startDateTime = event.getStart().getDateTime();
            } else if (event.getStart().getDate() != null) {
                // All-day event
                startDateTime = new DateTime(event.getStart().getDate().getValue());
            }
            
            if (startDateTime == null) {
                return null;
            }
            
            // Handle end time
            DateTime endDateTime = null;
            if (event.getEnd().getDateTime() != null) {
                endDateTime = event.getEnd().getDateTime();
            } else if (event.getEnd().getDate() != null) {
                // All-day event
                endDateTime = new DateTime(event.getEnd().getDate().getValue());
            }
            
            if (endDateTime == null) {
                return null;
            }
            
            // Create calendar event
            CalendarEvent calendarEvent = new CalendarEvent();
            calendarEvent.setId(event.getId());
            calendarEvent.setTitle(title != null ? title : "No Title");
            calendarEvent.setDescription(description != null ? description : "");
            calendarEvent.setStartTime(new Date(startDateTime.getValue()));
            calendarEvent.setEndTime(new Date(endDateTime.getValue()));
            calendarEvent.setAllDay(event.getStart().getDate() != null);
            calendarEvent.setColor(event.getColorId() != null ? 
                    Integer.parseInt(event.getColorId()) : 0);
            calendarEvent.setSource("Google Calendar");
            
            return calendarEvent;
        } catch (Exception e) {
            Log.e(TAG, "Error converting Google event", e);
            return null;
        }
    }

    private String createDateSelection(Date date) {
        // Create a calendar instance for the target date
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        long startMillis = calendar.getTimeInMillis();
        
        // Set end time to end of day
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
        calendar.set(java.util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        long endMillis = calendar.getTimeInMillis();
        
        // Create selection string for the date range
        return "(" + CalendarContract.Events.DTSTART + " >= " + startMillis + " AND " 
             + CalendarContract.Events.DTSTART + " <= " + endMillis + ") OR ("
             + CalendarContract.Events.DTEND + " >= " + startMillis + " AND " 
             + CalendarContract.Events.DTEND + " <= " + endMillis + ") OR ("
             + CalendarContract.Events.DTSTART + " <= " + startMillis + " AND " 
             + CalendarContract.Events.DTEND + " >= " + endMillis + ")";
    }

    private List<CalendarEvent> queryCalendarEvents(String selection) {
        List<CalendarEvent> events = new ArrayList<>();
        
        // Check for calendar read permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No READ_CALENDAR permission");
            return events;
        }
        
        // Define the projection (columns to fetch)
        String[] projection = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_COLOR,
                CalendarContract.Events.CALENDAR_ID
        };
        
        // Get the content URI
        Uri uri = CalendarContract.Events.CONTENT_URI;
        
        // Query the calendar
        Cursor cursor = context.getContentResolver().query(
                uri,
                projection,
                selection,
                null,
                CalendarContract.Events.DTSTART + " ASC"
        );
        
        if (cursor != null) {
            try {
                // Get column indices
                int idIdx = cursor.getColumnIndex(CalendarContract.Events._ID);
                int titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE);
                int descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION);
                int startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART);
                int endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND);
                int allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY);
                int colorIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR);
                
                // Iterate through results
                while (cursor.moveToNext()) {
                    CalendarEvent event = new CalendarEvent();
                    
                    if (idIdx != -1) event.setId(cursor.getString(idIdx));
                    if (titleIdx != -1) event.setTitle(cursor.getString(titleIdx));
                    if (descIdx != -1) event.setDescription(cursor.getString(descIdx));
                    if (startIdx != -1) event.setStartTime(new Date(cursor.getLong(startIdx)));
                    if (endIdx != -1) event.setEndTime(new Date(cursor.getLong(endIdx)));
                    if (allDayIdx != -1) event.setAllDay(cursor.getInt(allDayIdx) == 1);
                    if (colorIdx != -1) event.setColor(cursor.getInt(colorIdx));
                    event.setSource("Device Calendar");
                    
                    events.add(event);
                }
            } finally {
                cursor.close();
            }
        }
        
        return events;
    }

    public void requestCalendarScope(Activity activity) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account != null) {
            // Build the sign-in request with the Calendar scope
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(new Scope(CalendarScopes.CALENDAR_READONLY))
                    .build();
            
            // Create the sign-in client
            GoogleSignInClient signInClient = GoogleSignIn.getClient(activity, gso);
            
            // Start the sign-in flow
            Intent signInIntent = signInClient.getSignInIntent();
            activity.startActivityForResult(signInIntent, 1003); // Use a different request code
        }
    }
}






