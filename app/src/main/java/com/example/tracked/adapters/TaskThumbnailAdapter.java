package com.example.tracked.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.R;
import com.google.api.services.tasks.model.Task;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskThumbnailAdapter extends RecyclerView.Adapter<TaskThumbnailAdapter.TaskViewHolder> {
    private List<Task> tasks = new ArrayList<>();
    private SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd", Locale.US);
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private TaskClickListener taskClickListener;

    public interface TaskClickListener {
        void onTaskClick(Task task);
    }

    public void setTaskClickListener(TaskClickListener listener) {
        this.taskClickListener = listener;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_thumbnail, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        
        // Set title
        holder.titleView.setText(task.getTitle());
        
        // Extract task type from notes
        String taskType = "Task";
        if (task.getNotes() != null && !task.getNotes().isEmpty()) {
            Pattern typePattern = Pattern.compile("Type:\\s*([^\\n]+)");
            Matcher typeMatcher = typePattern.matcher(task.getNotes());
            if (typeMatcher.find()) {
                taskType = typeMatcher.group(1).trim();
            }
        }
        
        // Set task type
        holder.typeView.setText(taskType);
        
        // Set type indicator color based on task type
        int colorResId;
        switch (taskType.toLowerCase()) {
            case "assignment":
                colorResId = Color.parseColor("#F44336"); // Red
                break;
            case "quiz":
                colorResId = Color.parseColor("#FF9800"); // Orange
                break;
            case "exam":
                colorResId = Color.parseColor("#E91E63"); // Pink
                break;
            case "project":
                colorResId = Color.parseColor("#9C27B0"); // Purple
                break;
            case "reading":
                colorResId = Color.parseColor("#4CAF50"); // Green
                break;
            default:
                colorResId = Color.parseColor("#03A9F4"); // Blue
                break;
        }
        holder.typeIndicator.setBackgroundColor(colorResId);
        
        // Format and set due date
        if (task.getDue() != null && !task.getDue().isEmpty()) {
            try {
                Date date = apiFormat.parse(task.getDue());
                holder.dueDateView.setText(displayFormat.format(date));
                holder.dueDateView.setVisibility(View.VISIBLE);
            } catch (ParseException e) {
                holder.dueDateView.setText(task.getDue());
                holder.dueDateView.setVisibility(View.VISIBLE);
            }
        } else {
            holder.dueDateView.setVisibility(View.GONE);
        }
        
        // Set completion indicator
        boolean isCompleted = "completed".equals(task.getStatus());
        holder.completionIndicator.setVisibility(isCompleted ? View.VISIBLE : View.GONE);
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (taskClickListener != null) {
                taskClickListener.onTaskClick(task);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView titleView;
        TextView typeView;
        TextView dueDateView;
        View typeIndicator;
        ImageView completionIndicator;

        TaskViewHolder(View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.taskTitle);
            typeView = itemView.findViewById(R.id.taskType);
            dueDateView = itemView.findViewById(R.id.taskDueDate);
            typeIndicator = itemView.findViewById(R.id.taskTypeIndicator);
            completionIndicator = itemView.findViewById(R.id.taskCompletionIndicator);
        }
    }
}