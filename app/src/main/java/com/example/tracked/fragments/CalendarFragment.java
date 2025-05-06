package com.example.tracked.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.R;
import com.example.tracked.adapters.CalendarEventAdapter;
import com.example.tracked.models.CalendarEvent;
import com.example.tracked.services.CalendarApiService;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class CalendarFragment extends Fragment {
    private static final String TAG = "CalendarFragment";
    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 1001;
    
    private CalendarView calendarView;
    private RecyclerView eventsRecyclerView;
    private CalendarEventAdapter eventAdapter;
    private TextView noEventsText;
    private CircularProgressIndicator loadingIndicator;
    private CalendarApiService calendarApiService;
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);
        
        // Initialize views
        calendarView = view.findViewById(R.id.calendarView);
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView);
        noEventsText = view.findViewById(R.id.noEventsText);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        
        // Set up RecyclerView
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        eventAdapter = new CalendarEventAdapter();
        eventsRecyclerView.setAdapter(eventAdapter);
        
        // Set up calendar
        apiFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Check for calendar permissions
        if (hasCalendarPermissions()) {
            initializeCalendar();
        } else {
            requestCalendarPermissions();
        }
    }
    
    private boolean hasCalendarPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestCalendarPermissions() {
        ActivityCompat.requestPermissions(
                requireActivity(),
                new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR},
                CALENDAR_PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CALENDAR_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCalendar();
            } else {
                Toast.makeText(getContext(), "Calendar permission denied", Toast.LENGTH_SHORT).show();
                noEventsText.setText("Calendar permission required to view events");
                noEventsText.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private void initializeCalendar() {
        // Initialize calendar service
        calendarApiService = new CalendarApiService(requireContext());
        
        // Set up calendar date change listener
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            // Create date from selected calendar day
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            Date selectedDate = calendar.getTime();
            
            // Load events for the selected date
            loadEventsForDate(selectedDate);
        });
        
        // Load events for today
        loadEventsForDate(new Date());
    }
    
    private void loadEventsForDate(Date date) {
        // Show loading indicator
        loadingIndicator.setVisibility(View.VISIBLE);
        noEventsText.setVisibility(View.GONE);
        eventsRecyclerView.setVisibility(View.GONE);
        
        try {
            // Format date for API
            String formattedDate = apiFormat.format(date);
            
            // Get events for the date
            calendarApiService.getEventsForDate(formattedDate, new CalendarApiService.EventListCallback() {
                @Override
                public void onSuccess(List<CalendarEvent> events) {
                    requireActivity().runOnUiThread(() -> {
                        loadingIndicator.setVisibility(View.GONE);
                        
                        if (events.isEmpty()) {
                            noEventsText.setVisibility(View.VISIBLE);
                            eventsRecyclerView.setVisibility(View.GONE);
                        } else {
                            noEventsText.setVisibility(View.GONE);
                            eventsRecyclerView.setVisibility(View.VISIBLE);
                            eventAdapter.setEvents(events);
                        }
                    });
                }
                
                @Override
                public void onFailure(Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        loadingIndicator.setVisibility(View.GONE);
                        noEventsText.setText("Error loading events: " + e.getMessage());
                        noEventsText.setVisibility(View.VISIBLE);
                        eventsRecyclerView.setVisibility(View.GONE);
                        Log.e(TAG, "Error loading events", e);
                    });
                }
            });
        } catch (Exception e) {
            loadingIndicator.setVisibility(View.GONE);
            noEventsText.setText("Error loading events: " + e.getMessage());
            noEventsText.setVisibility(View.VISIBLE);
            eventsRecyclerView.setVisibility(View.GONE);
            Log.e(TAG, "Error formatting date", e);
        }
    }
}