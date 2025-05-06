package com.example.tracked.services;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import com.example.tracked.models.CalendarEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class CalendarApiService {
    private static final String TAG = "CalendarApiService";
    private Context context;
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    public CalendarApiService(Context context) {
        this.context = context;
        apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public interface EventListCallback {
        void onSuccess(List<CalendarEvent> events);
        void onFailure(Exception e);
    }

    public void getEvents(EventListCallback callback) {
        try {
            // Get events from device calendar
            List<CalendarEvent> events = queryCalendarEvents(null);
            callback.onSuccess(events);
        } catch (Exception e) {
            Log.e(TAG, "Error getting calendar events", e);
            callback.onFailure(e);
        }
    }

    public void getEventsForDate(String date, EventListCallback callback) {
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

    private String createDateSelection(Date date) {
        // Convert date to milliseconds
        long dateMillis = date.getTime();
        
        // Create start and end of day
        long startOfDay = dateMillis - (dateMillis % (24 * 60 * 60 * 1000));
        long endOfDay = startOfDay + (24 * 60 * 60 * 1000) - 1;
        
        // Create selection string
        return "(" + CalendarContract.Events.DTSTART + " >= " + startOfDay + 
               " AND " + CalendarContract.Events.DTSTART + " <= " + endOfDay + ")" +
               " OR (" + CalendarContract.Events.DTEND + " >= " + startOfDay + 
               " AND " + CalendarContract.Events.DTEND + " <= " + endOfDay + ")" +
               " OR (" + CalendarContract.Events.DTSTART + " <= " + startOfDay + 
               " AND " + CalendarContract.Events.DTEND + " >= " + endOfDay + ")";
    }

    private List<CalendarEvent> queryCalendarEvents(String selection) {
        List<CalendarEvent> events = new ArrayList<>();
        
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
                    
                    // Set event properties
                    event.setId(cursor.getString(idIdx));
                    event.setTitle(cursor.getString(titleIdx));
                    event.setDescription(cursor.getString(descIdx));
                    
                    // Convert timestamps to ISO format
                    long startMillis = cursor.getLong(startIdx);
                    long endMillis = cursor.getLong(endIdx);
                    event.setStartTime(apiFormat.format(new Date(startMillis)));
                    event.setEndTime(apiFormat.format(new Date(endMillis)));
                    
                    // Set all-day flag
                    event.setAllDay(cursor.getInt(allDayIdx) == 1);
                    
                    // Set event type
                    event.setEventType("calendar");
                    
                    // Set color if available
                    if (!cursor.isNull(colorIdx)) {
                        int colorValue = cursor.getInt(colorIdx);
                        event.setColor(String.format("#%06X", (0xFFFFFF & colorValue)));
                    }
                    
                    events.add(event);
                }
            } finally {
                cursor.close();
            }
        }
        
        return events;
    }
}

