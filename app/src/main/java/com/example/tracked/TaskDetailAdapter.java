package com.example.tracked;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);

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

        TaskViewHolder(View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.taskTitle);
            dateView = itemView.findViewById(R.id.taskDate);
            descriptionView = itemView.findViewById(R.id.taskDescription);
            typeView = itemView.findViewById(R.id.taskType);
            startDateView = itemView.findViewById(R.id.taskStartDate);
        }

        void bind(Task task) {
            // Set title
            titleView.setText(task.getTitle());
            
            // Format and set due date
            String dueDate = task.getDue();
            if (dueDate != null) {
                try {
                    Date date = apiFormat.parse(dueDate);
                    dateView.setText(displayFormat.format(date));
                } catch (ParseException e) {
                    dateView.setText(dueDate);
                }
            }
            
            // Parse description for type and start date
            String notes = task.getNotes();
            if (notes != null) {
                // Extract task type
                Pattern typePattern = Pattern.compile("Type: ([^\\n]+)");
                Matcher typeMatcher = typePattern.matcher(notes);
                if (typeMatcher.find()) {
                    typeView.setText("Type: " + typeMatcher.group(1));
                }
                
                // Extract start date
                Pattern startDatePattern = Pattern.compile("Start Date: ([^\\n]+)");
                Matcher startDateMatcher = startDatePattern.matcher(notes);
                if (startDateMatcher.find()) {
                    String startDateStr = startDateMatcher.group(1);
                    try {
                        Date startDate = apiFormat.parse(startDateStr);
                        startDateView.setText("Start Date: " + displayFormat.format(startDate));
                    } catch (ParseException e) {
                        startDateView.setText("Start Date: " + startDateStr);
                    }
                }
                
                // Set description (remove the metadata)
                String cleanDescription = notes
                    .replaceAll("Type: [^\\n]+\\n", "")
                    .replaceAll("Start Date: [^\\n]+\\n\\n", "")
                    .trim();
                descriptionView.setText(cleanDescription);
            }
        }
    }
}
