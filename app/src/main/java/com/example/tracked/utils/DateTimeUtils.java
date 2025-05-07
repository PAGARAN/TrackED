package com.example.tracked.utils;

import android.util.Log;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeUtils {
    // Philippine timezone
    private static final TimeZone PHILIPPINE_TIMEZONE = TimeZone.getTimeZone("Asia/Manila");
    
    // API format (UTC)
    private static final SimpleDateFormat API_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    
    // Display formats (Philippine time)
    private static final SimpleDateFormat DISPLAY_DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private static final SimpleDateFormat DISPLAY_DATE_TIME_FORMAT = new SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.US);
    private static final SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("MMM dd", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a", Locale.US);
    
    static {
        API_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        DISPLAY_DATE_FORMAT.setTimeZone(PHILIPPINE_TIMEZONE);
        DISPLAY_DATE_TIME_FORMAT.setTimeZone(PHILIPPINE_TIMEZONE);
        SHORT_DATE_FORMAT.setTimeZone(PHILIPPINE_TIMEZONE);
        TIME_FORMAT.setTimeZone(PHILIPPINE_TIMEZONE);
    }
    
    /**
     * Converts a date string from API format to a Date object
     */
    public static Date parseApiDate(String dateString) throws ParseException {
        if (dateString == null) return null;
        
        Log.d("DateTimeUtils", "Parsing date string: " + dateString);
        
        // Try multiple date formats to handle different API responses
        String[] possibleFormats = {
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",  // Format with milliseconds
            "yyyy-MM-dd'T'HH:mm:ss'Z'",      // Format without milliseconds
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO 8601 with timezone
            "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO 8601 without milliseconds
            "yyyy-MM-dd"                     // Date only
        };
        
        for (String format : possibleFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsedDate = sdf.parse(dateString);
                Log.d("DateTimeUtils", "Successfully parsed with format: " + format + ", result: " + parsedDate);
                return parsedDate;
            } catch (ParseException e) {
                // Try next format
                Log.d("DateTimeUtils", "Failed to parse with format: " + format);
            }
        }
        
        // If all formats fail, throw exception
        Log.e("DateTimeUtils", "Failed to parse date with all formats: " + dateString);
        throw new ParseException("Unparseable date: " + dateString, 0);
    }
    
    /**
     * Formats a Date object to API format string
     */
    public static String formatToApiDate(Date date) {
        return API_FORMAT.format(date);
    }
    
    /**
     * Formats a Date object to display date format (Philippine time)
     */
    public static String formatToDisplayDate(Date date) {
        return DISPLAY_DATE_FORMAT.format(date);
    }
    
    /**
     * Formats a Date object to display date and time format (Philippine time)
     */
    public static String formatToDisplayDateTime(Date date) {
        return DISPLAY_DATE_TIME_FORMAT.format(date);
    }
    
    /**
     * Formats a Date object to short date format (Philippine time)
     */
    public static String formatToShortDate(Date date) {
        return SHORT_DATE_FORMAT.format(date);
    }
    
    /**
     * Formats a Date object to time format (Philippine time)
     */
    public static String formatToTime(Date date) {
        return TIME_FORMAT.format(date);
    }
    
    /**
     * Parses API date string and formats to display date (Philippine time)
     */
    public static String apiDateToDisplayDate(String apiDateString) {
        try {
            Date date = parseApiDate(apiDateString);
            return formatToDisplayDate(date);
        } catch (ParseException e) {
            return apiDateString; // Return original if parsing fails
        }
    }
    
    /**
     * Parses API date string and formats to display date and time (Philippine time)
     */
    public static String apiDateToDisplayDateTime(String apiDateString) {
        try {
            Date date = parseApiDate(apiDateString);
            return formatToDisplayDateTime(date);
        } catch (ParseException e) {
            return apiDateString; // Return original if parsing fails
        }
    }
}



