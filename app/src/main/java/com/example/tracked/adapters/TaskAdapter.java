package com.example.tracked.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tracked.R;
import com.google.api.services.tasks.model.Task;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;
import java.text.ParseException;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> tasks = new ArrayList<>();
    private SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_overview, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.titleView.setText(task.getTitle());
        
        // Format and set due date
        String dueDate = task.getDue();
        if (dueDate != null) {
            try {
                Date date = apiFormat.parse(dueDate);
                holder.dueDateView.setText(displayFormat.format(date));
            } catch (ParseException e) {
                holder.dueDateView.setText(dueDate);
            }
        } else {
            holder.dueDateView.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView titleView;
        TextView dueDateView;

        TaskViewHolder(View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.taskTitle);
            dueDateView = itemView.findViewById(R.id.taskDueDate);
        }
    }
}
