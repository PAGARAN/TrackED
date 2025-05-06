package com.example.tracked.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.R;
import com.example.tracked.models.CalendarEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalendarEventAdapter extends RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder> {
    private List<CalendarEvent> events = new ArrayList<>();
    private SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

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
        
        // Set event title
        holder.titleText.setText(event.getTitle());
        
        // Format and set time
        if (event.isAllDay()) {
            holder.timeText.setText("All day");
        } else {
            String startTime = timeFormat.format(event.getStartTime());
            String endTime = timeFormat.format(event.getEndTime());
            holder.timeText.setText(startTime + " - " + endTime);
        }
        
        // Set description if available
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            holder.descriptionText.setVisibility(View.VISIBLE);
            holder.descriptionText.setText(event.getDescription());
        } else {
            holder.descriptionText.setVisibility(View.GONE);
        }
        
        // Set source badge
        if (event.getSource() != null) {
            holder.sourceText.setVisibility(View.VISIBLE);
            holder.sourceText.setText(event.getSource());
            
            // Set different colors for different sources
            if ("Google Calendar".equals(event.getSource())) {
                holder.sourceText.setBackgroundResource(R.drawable.badge_google);
            } else {
                holder.sourceText.setBackgroundResource(R.drawable.badge_device);
            }
        } else {
            holder.sourceText.setVisibility(View.GONE);
        }
        
        // Set color indicator
        if (event.getColor() != 0) {
            holder.colorIndicator.setVisibility(View.VISIBLE);
            holder.colorIndicator.setBackgroundColor(event.getColor());
        } else {
            // Default colors based on source
            if ("Google Calendar".equals(event.getSource())) {
                holder.colorIndicator.setVisibility(View.VISIBLE);
                holder.colorIndicator.setBackgroundColor(Color.parseColor("#4285F4")); // Google blue
            } else {
                holder.colorIndicator.setVisibility(View.VISIBLE);
                holder.colorIndicator.setBackgroundColor(Color.parseColor("#03A9F4")); // App blue
            }
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView timeText;
        TextView descriptionText;
        TextView sourceText;
        View colorIndicator;
        CardView cardView;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.eventTitleText);
            timeText = itemView.findViewById(R.id.eventTimeText);
            descriptionText = itemView.findViewById(R.id.eventDescriptionText);
            sourceText = itemView.findViewById(R.id.eventSourceText);
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
            cardView = itemView.findViewById(R.id.eventCardView);
        }
    }
}
