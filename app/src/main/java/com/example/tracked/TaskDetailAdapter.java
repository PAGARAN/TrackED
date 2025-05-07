package com.example.tracked;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.api.services.tasks.model.Task;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskDetailAdapter extends RecyclerView.Adapter<TaskDetailAdapter.TaskViewHolder> {
    private List<Task> tasks = new ArrayList<>();
    private SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private TaskActionListener taskActionListener;
    
    // Interface for task actions
    public interface TaskActionListener {
        void onEditTask(Task task);
        void onDeleteTask(Task task);
        void onCompleteTask(Task task);
        void onUncompleteTask(Task task);
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
                .inflate(R.layout.item_task_detail, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView titleView;
        TextView dateView;
        TextView descriptionView;
        TextView typeView;
        TextView startDateView;
        ImageButton editButton;
        ImageButton deleteButton;
        ImageButton completeButton;

        TaskViewHolder(View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.taskTitle);
            dateView = itemView.findViewById(R.id.taskDueDate);
            descriptionView = itemView.findViewById(R.id.taskDescription);
            typeView = itemView.findViewById(R.id.taskType);
            startDateView = itemView.findViewById(R.id.taskStartDate);
            editButton = itemView.findViewById(R.id.editTaskButton);
            deleteButton = itemView.findViewById(R.id.deleteTaskButton);
            completeButton = itemView.findViewById(R.id.completeTaskButton);
        }

        void bind(Task task) {
            titleView.setText(task.getTitle());
            
            // Extract metadata from notes
            String taskType = "Task";
            String startDate = null;
            String cleanDescription = task.getNotes();
            
            if (task.getNotes() != null && !task.getNotes().isEmpty()) {
                // Extract type and start date from notes metadata
                Pattern typePattern = Pattern.compile("Type:\\s*([^\\n]+)");
                Pattern startDatePattern = Pattern.compile("Start Date:\\s*([^\\n]+)");
                
                Matcher typeMatcher = typePattern.matcher(task.getNotes());
                if (typeMatcher.find()) {
                    taskType = typeMatcher.group(1).trim();
                }
                
                Matcher startDateMatcher = startDatePattern.matcher(task.getNotes());
                if (startDateMatcher.find()) {
                    startDate = startDateMatcher.group(1).trim();
                }
                
                // Extract clean description (after metadata)
                int metadataEnd = task.getNotes().indexOf("\n\n");
                if (metadataEnd != -1) {
                    cleanDescription = task.getNotes().substring(metadataEnd + 2);
                }
                
                // Set description
                descriptionView.setText(cleanDescription);
                descriptionView.setVisibility(cleanDescription != null && !cleanDescription.isEmpty() ? 
                                             View.VISIBLE : View.GONE);
            } else {
                descriptionView.setVisibility(View.GONE);
            }
            
            // Set task type with color based on type
            if (taskType != null && !taskType.isEmpty()) {
                typeView.setText(taskType);
                
                // Set background color based on task type
                int colorResId;
                switch (taskType.toLowerCase()) {
                    case "assignment":
                        colorResId = android.R.color.holo_red_light;
                        break;
                    case "quiz":
                        colorResId = android.R.color.holo_orange_light;
                        break;
                    case "exam":
                        colorResId = android.R.color.holo_red_dark;
                        break;
                    case "project":
                        colorResId = android.R.color.holo_purple;
                        break;
                    case "reading":
                        colorResId = android.R.color.holo_green_light;
                        break;
                    default:
                        colorResId = android.R.color.holo_blue_light;
                        break;
                }
                
                // Apply the color to the background drawable
                GradientDrawable drawable = (GradientDrawable) typeView.getBackground();
                if (drawable != null) {
                    drawable.setColor(itemView.getContext().getResources().getColor(colorResId));
                }
                
                typeView.setVisibility(View.VISIBLE);
            } else {
                typeView.setVisibility(View.GONE);
            }
            
            // Format and set due date
            if (task.getDue() != null && !task.getDue().isEmpty()) {
                try {
                    Date date = apiFormat.parse(task.getDue());
                    dateView.setText(displayFormat.format(date));
                    dateView.setVisibility(View.VISIBLE);
                } catch (ParseException e) {
                    dateView.setText(task.getDue());
                    dateView.setVisibility(View.VISIBLE);
                }
            } else {
                dateView.setVisibility(View.GONE);
            }
            
            // Format and set start date
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    Date date = apiFormat.parse(startDate);
                    startDateView.setText(displayFormat.format(date));
                    startDateView.setVisibility(View.VISIBLE);
                } catch (ParseException e) {
                    startDateView.setText(startDate);
                    startDateView.setVisibility(View.VISIBLE);
                }
            } else {
                startDateView.setVisibility(View.GONE);
            }
            
            // Set up button states based on task status
            boolean isCompleted = "completed".equals(task.getStatus());
            completeButton.setImageResource(isCompleted ? 
                R.drawable.ic_check_circle : R.drawable.ic_circle_outline);
            
            // Set up button click listeners
            editButton.setOnClickListener(v -> {
                if (taskActionListener != null) {
                    taskActionListener.onEditTask(task);
                }
            });
            
            deleteButton.setOnClickListener(v -> {
                if (taskActionListener != null) {
                    taskActionListener.onDeleteTask(task);
                }
            });
            
            completeButton.setOnClickListener(v -> {
                if (taskActionListener != null) {
                    if (isCompleted) {
                        taskActionListener.onUncompleteTask(task);
                    } else {
                        taskActionListener.onCompleteTask(task);
                    }
                }
            });
        }
    }
}







