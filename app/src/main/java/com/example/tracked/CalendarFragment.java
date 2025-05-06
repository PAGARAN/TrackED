package com.example.tracked;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.adapters.CalendarEventAdapter;
import com.example.tracked.models.CalendarEvent;
import com.example.tracked.services.CalendarApiService;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;

import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;

public class CalendarFragment extends Fragment implements OnDateSelectedListener {

    private MaterialCalendarView calendarView;
    private TextView monthYearText;
    private RecyclerView eventsRecyclerView;
    private TextView noEventsText;
    private CalendarApiService calendarApiService;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        // Initialize views
        calendarView = view.findViewById(R.id.calendarView);
        monthYearText = view.findViewById(R.id.monthYearText);
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView);
        noEventsText = view.findViewById(R.id.noEventsText);

        // Setup calendar
        calendarView.setOnDateChangedListener(this);

        // Set current month/year
        updateMonthYearText(CalendarDay.today());

        // Setup RecyclerView
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

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
            calendarView.setSelectedDate(CalendarDay.today());
            loadEventsForSelectedDate(CalendarDay.today());
        });

        // Load events for today
        loadEventsForSelectedDate(CalendarDay.today());

        return view;
    }

    private void updateMonthYearText(CalendarDay day) {
        // Convert from LocalDate to Date
        LocalDate localDate = day.getDate();

        // Convert ThreeTenABP LocalDate to java.util.Date
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, localDate.getYear());
        calendar.set(Calendar.MONTH, localDate.getMonthValue() - 1); // Calendar months are 0-based
        calendar.set(Calendar.DAY_OF_MONTH, localDate.getDayOfMonth());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Date date = calendar.getTime();
        monthYearText.setText(dateFormat.format(date));
    }

    private void loadEventsForSelectedDate(CalendarDay day) {
        updateMonthYearText(day);

        // Convert LocalDate to Date for API date format
        LocalDate localDate = day.getDate();

        // Convert ThreeTenABP LocalDate to java.util.Date
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, localDate.getYear());
        calendar.set(Calendar.MONTH, localDate.getMonthValue() - 1); // Calendar months are 0-based
        calendar.set(Calendar.DAY_OF_MONTH, localDate.getDayOfMonth());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Date date = calendar.getTime();

        // Format the date for API
        String dateString = apiFormat.format(date);

        // Load events for the selected date
        calendarApiService.getEventsForDate(dateString, new CalendarApiService.EventListCallback() {
            @Override
            public void onSuccess(List<CalendarEvent> events) {
                if (events.isEmpty()) {
                    noEventsText.setVisibility(View.VISIBLE);
                    eventsRecyclerView.setVisibility(View.GONE);
                } else {
                    noEventsText.setVisibility(View.GONE);
                    eventsRecyclerView.setVisibility(View.VISIBLE);
                    // Set adapter with events
                    CalendarEventAdapter adapter = new CalendarEventAdapter();
                    adapter.setEvents(events);
                    eventsRecyclerView.setAdapter(adapter);
                }
            }

            @Override
            public void onFailure(Exception e) {
                noEventsText.setText("Error loading events");
                noEventsText.setVisibility(View.VISIBLE);
                eventsRecyclerView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
        if (selected) {
            loadEventsForSelectedDate(date);
        }
    }
}
