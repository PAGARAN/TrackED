package com.example.tracked;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

import com.example.tracked.adapters.CalendarEventAdapter;
import com.example.tracked.models.CalendarEvent;
import com.example.tracked.services.CalendarApiService;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {
    private static final int REQUEST_AUTHORIZATION = 1002;
    private static final int RC_SIGN_IN_WITH_CALENDAR = 1003;
    private static final int CALENDAR_PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "CalendarFragment";
    
    private CalendarView calendarView;
    private TextView monthYearText;
    private RecyclerView eventsRecyclerView;
    private TextView noEventsText;
    private CircularProgressIndicator loadingIndicator;
    private CalendarApiService calendarApiService;
    private CalendarEventAdapter eventAdapter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
    private Date lastSelectedDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        // Initialize views
        calendarView = view.findViewById(R.id.calendarView);
        monthYearText = view.findViewById(R.id.monthYearText);
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView);
        noEventsText = view.findViewById(R.id.noEventsText);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);

        // Setup calendar
        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            Date selectedDate = calendar.getTime();
            updateMonthYearText(selectedDate);
            loadEventsForDate(selectedDate);
        });

        // Set current month/year
        updateMonthYearText(new Date());

        // Setup RecyclerView
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        eventAdapter = new CalendarEventAdapter();
        eventsRecyclerView.setAdapter(eventAdapter);

        // Initialize calendar service
        calendarApiService = new CalendarApiService(getContext());

        // Setup menu button
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        menuButton.setOnClickListener(v -> {
            DrawerLayout drawerLayout = requireActivity().findViewById(R.id.drawer_layout);
            drawerLayout.openDrawer(GravityCompat.START);
        });

        // Setup today button
        view.findViewById(R.id.todayButton).setOnClickListener(v -> {
            calendarView.setDate(System.currentTimeMillis());
            updateMonthYearText(new Date());
            loadEventsForDate(new Date());
        });

        // Load events for today
        loadEventsForDate(new Date());

        return view;
    }

    private void updateMonthYearText(Date date) {
        monthYearText.setText(dateFormat.format(date));
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
                            noEventsText.setText("No tasks scheduled for this day");
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
                    // Handle failure... (code for handling failure)
                }
            });
        } catch (Exception e) {
            // Handle exception... (code for handling exception)
        }
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
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_AUTHORIZATION) {
            if (calendarApiService != null) {
                // Let the service handle the authorization result
                calendarApiService.handleAuthorizationResult(resultCode);
            }
            
            if (resultCode != Activity.RESULT_OK) {
                // User denied permission
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        noEventsText.setText("Calendar access denied. Using device calendar only.");
                        noEventsText.setVisibility(View.VISIBLE);
                    });
                }
            }
        } else if (requestCode == RC_SIGN_IN_WITH_CALENDAR) {
            if (resultCode == Activity.RESULT_OK) {
                // User signed in with Calendar scope
                if (calendarApiService != null) {
                    // Re-initialize the service
                    calendarApiService = new CalendarApiService(getContext());
                    
                    // Reload events
                    if (lastSelectedDate != null) {
                        loadEventsForDate(lastSelectedDate);
                    } else {
                        loadEventsForDate(new Date());
                    }
                }
            }
        }
    }
}




