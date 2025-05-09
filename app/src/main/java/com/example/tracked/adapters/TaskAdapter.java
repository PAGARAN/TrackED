package com.example.tracked.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tracked.R;
import com.google.api.services.tasks.model.Task;
import java.util.ArrayList;
import java.util.List;
import com.example.tracked.utils.DateTimeUtils;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> tasks = new ArrayList<>();
    private int layoutResId = R.layout.item_task_overview; // Default layout
    private TaskClickListener taskClickListener;
    private TaskActionListener taskActionListener;

    public interface TaskClickListener {
        void onTaskClick(Task task);
    }

    public interface TaskActionListener {
        void onTaskStatusChanged(Task task, boolean isCompleted);
    }

    // Default constructor
    public TaskAdapter() {
        this(R.layout.item_task_overview);
    }

    // Constructor with layout resource ID
    public TaskAdapter(int layoutResId) {
        this.layoutResId = layoutResId;
    }

    public void setTaskClickListener(TaskClickListener listener) {
        this.taskClickListener = listener;
    }

    public void setTaskActionListener(TaskActionListener listener) {
        this.taskActionListener = listener;
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
        Task task = tasks.get(position);
        
        // Set title
        if (holder.titleView != null) {
            holder.titleView.setText(task.getTitle());
        }
        
        // Handle task type (for thumbnail view)
        if (holder.typeView != null) {
            String taskType = "Task";
            if (task.getNotes() != null && !task.getNotes().isEmpty()) {
                Pattern typePattern = Pattern.compile("Type:\\s*([^\\n]+)");
                Matcher typeMatcher = typePattern.matcher(task.getNotes());
                if (typeMatcher.find()) {
                    taskType = typeMatcher.group(1).trim();
                }
            }
            holder.typeView.setText(taskType);
            
            // Set type indicator color if it exists
            if (holder.typeIndicator != null) {
                int color;
                switch (taskType.toLowerCase()) {
                    case "assignment":
                        color = Color.parseColor("#F44336"); // Red
                        break;
                    case "quiz":
                        color = Color.parseColor("#FF9800"); // Orange
                        break;
                    case "exam":
                        color = Color.parseColor("#E91E63"); // Pink
                        break;
                    case "project":
                        color = Color.parseColor("#9C27B0"); // Purple
                        break;
                    case "reading":
                        color = Color.parseColor("#4CAF50"); // Green
                        break;
                    default:
                        color = Color.parseColor("#03A9F4"); // Blue
                        break;
                }
                holder.typeIndicator.setBackgroundColor(color);
            }
        }
        
        // Format and set due date
        if (holder.dueDateView != null && task.getDue() != null && !task.getDue().isEmpty()) {
            if (layoutResId == R.layout.item_task_thumbnail) {
                holder.dueDateView.setText(DateTimeUtils.apiDateToDisplayDate(task.getDue()));
            } else {
                holder.dueDateView.setText(DateTimeUtils.apiDateToDisplayDateTime(task.getDue()));
            }
            holder.dueDateView.setVisibility(View.VISIBLE);
        } else if (holder.dueDateView != null) {
            holder.dueDateView.setVisibility(View.GONE);
        }
        
        // Set description (for overview layout)
        if (holder.descriptionView != null && task.getNotes() != null) {
            holder.descriptionView.setText(task.getNotes());
        }
        
        // Set checkbox state (for overview layout)
        if (holder.checkBox != null) {
            holder.checkBox.setOnCheckedChangeListener(null); // Clear previous listener
            holder.checkBox.setChecked(task.getStatus() != null && task.getStatus().equals("completed"));
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (taskActionListener != null) {
                    taskActionListener.onTaskStatusChanged(task, isChecked);
                }
            });
        }
        
        // Set completion indicator (for thumbnail layout)
        if (holder.completionIndicator != null) {
            boolean isCompleted = "completed".equals(task.getStatus());
            holder.completionIndicator.setVisibility(isCompleted ? View.VISIBLE : View.GONE);
        }
        
        // Remove priority indicator handling
        /*
        if (holder.priorityIndicator != null) {
            // Default to blue
            int color = Color.parseColor("#03A9F4");
            
            // Check if task has priority information
            if (task.getNotes() != null) {
                String notes = task.getNotes().toLowerCase();
                if (notes.contains("high priority") || notes.contains("urgent")) {
                    color = Color.parseColor("#F44336"); // Red for high priority
                } else if (notes.contains("medium priority")) {
                    color = Color.parseColor("#FF9800"); // Orange for medium priority
                } else if (notes.contains("low priority")) {
                    color = Color.parseColor("#4CAF50"); // Green for low priority
                }
            }
            
            holder.priorityIndicator.setBackgroundColor(color);
        }
        */
        
        // Set click listener for the entire item
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
        TextView descriptionView;
        TextView dueDateView;
        TextView typeView;
        CheckBox checkBox;
        // Remove priority indicator
        // View priorityIndicator;
        View typeIndicator;
        ImageView completionIndicator;

        TaskViewHolder(View itemView) {
            super(itemView);
            // Find views that might exist in any layout
            titleView = itemView.findViewById(R.id.taskTitle);
            dueDateView = itemView.findViewById(R.id.taskDueDate);
            
            // Find views specific to certain layouts
            descriptionView = itemView.findViewById(R.id.taskDescription);
            typeView = itemView.findViewById(R.id.taskType);
            checkBox = itemView.findViewById(R.id.taskCheckbox);
            // Remove priority indicator
            // priorityIndicator = itemView.findViewById(R.id.taskPriorityIndicator);
            typeIndicator = itemView.findViewById(R.id.taskTypeIndicator);
            completionIndicator = itemView.findViewById(R.id.taskCompletionIndicator);
        }
    }
}





