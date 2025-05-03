package com.example.tracked;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.api.services.tasks.model.Task;
import java.util.ArrayList;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;    
// Remove the TabLayout import
// import com.google.android.material.tabs.TabLayout;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> tasks = new ArrayList<>();
    private int layoutResId;
    
    // Default constructor uses the detailed layout
    public TaskAdapter() {
        this.layoutResId = R.layout.item_task;
    }
    
    // Constructor with layout resource ID
    public TaskAdapter(int layoutResId) {
        this.layoutResId = layoutResId;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutResId, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(tasks.get(position));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView titleView;
        TextView descriptionView;
        TextView dueDateView;

        TaskViewHolder(View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.taskTitle);
            
            // These views might not exist in the overview layout
            descriptionView = itemView.findViewById(R.id.taskDescription);
            dueDateView = itemView.findViewById(R.id.taskDueDate);
        }

        void bind(Task task) {
            titleView.setText(task.getTitle());
            
            if (descriptionView != null) {
                // Use getNotes() instead of getDescription()
                descriptionView.setText(task.getNotes());
            }
            
            if (dueDateView != null && task.getDue() != null) {
                dueDateView.setText(task.getDue());
            }
        }
    }
}



