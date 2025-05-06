package com.example.tracked.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.R;
import com.example.tracked.models.CalendarEvent;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarEventAdapter extends RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder> {
    private List<CalendarEvent> events = new ArrayList<>();
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private SimpleDateFormat displayFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public void setEvents(List<CalendarEvent> events) {
        this.events = events;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        CalendarEvent event = events.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private View colorIndicator;
        private TextView timeText;
        private TextView titleText;
        private TextView descriptionText;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
            timeText = itemView.findViewById(R.id.timeText);
            titleText = itemView.findViewById(R.id.titleText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
        }

        public void bind(CalendarEvent event) {
            titleText.setText(event.getTitle());
            
            if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                descriptionText.setVisibility(View.VISIBLE);
                descriptionText.setText(event.getDescription());
            } else {
                descriptionText.setVisibility(View.GONE);
            }
            
            // Set time
            if (event.isAllDay()) {
                timeText.setText("All day");
            } else {
                try {
                    Date startDate = apiFormat.parse(event.getStartTime());
                    if (startDate != null) {
                        timeText.setText(displayFormat.format(startDate));
                    } else {
                        timeText.setText("--:--");
                    }
                } catch (ParseException e) {
                    timeText.setText("--:--");
                }
            }
            
            // Set color indicator
            int color;
            if ("task".equals(event.getEventType())) {
                color = Color.parseColor("#FF5722"); // Orange for tasks
            } else if (event.getColor() != null && !event.getColor().isEmpty()) {
                try {
                    color = Color.parseColor(event.getColor());
                } catch (IllegalArgumentException e) {
                    color = Color.parseColor("#2196F3"); // Default blue
                }
            } else {
                color = Color.parseColor("#2196F3"); // Default blue
            }
            
            colorIndicator.setBackgroundColor(color);
        }
    }
}