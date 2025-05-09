package com.example.tracked;

import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tracked.adapters.NewsAdapter;
import com.example.tracked.api.NewsApi;
import com.example.tracked.api.RssFeedService;
import com.example.tracked.model.NewsArticle;
import com.example.tracked.model.NewsResponse;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewsFragment extends Fragment {
    private RecyclerView newsRecyclerView;
    private ProgressBar newsProgress;
    private NewsAdapter newsAdapter;
    private NewsApi newsApi;
    private RssFeedService rssFeedService;
    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private final int AUTO_SCROLL_DELAY = 5000; // 5 seconds between scrolls
    private boolean isFragmentActive = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_news, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        newsRecyclerView = view.findViewById(R.id.newsRecyclerView);
        newsProgress = view.findViewById(R.id.newsProgress);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        
        // Set up menu button
        menuButton.setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                DashboardActivity activity = (DashboardActivity) getActivity();
                activity.openDrawer();
            }
        });

        // Set up news category buttons
        TextView schoolNewsButton = view.findViewById(R.id.schoolNewsButton);
        TextView educationNewsButton = view.findViewById(R.id.educationNewsButton);
        
        schoolNewsButton.setOnClickListener(v -> {
            showSchoolSelectionDialog(); // Use our new dialog with university list
        });
        
        educationNewsButton.setOnClickListener(v -> {
            fetchEducationNews();
        });

        // Initialize handler for auto-scroll (though we won't use it)
        autoScrollHandler = new Handler();

        // Set up RecyclerView with vertical layout
        LinearLayoutManager newsLayoutManager = new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL, false);
        newsRecyclerView.setLayoutManager(newsLayoutManager);

        // Initialize adapter with vertical layout and full width
        newsAdapter = new NewsAdapter(getContext(), true, true);
        newsRecyclerView.setAdapter(newsAdapter);

        // Get NewsApi and RssFeedService from activity
        if (getActivity() instanceof DashboardActivity) {
            DashboardActivity activity = (DashboardActivity) getActivity();
            newsApi = activity.getNewsApi();
            rssFeedService = activity.getRssFeedService();
            
            // Fetch BukSU news by default
            fetchBuksuNews();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isFragmentActive = true;
        // Remove auto-scroll for vertical list
        // startAutoScroll();
    }

    @Override
    public void onPause() {
        super.onPause();
        isFragmentActive = false;
        // Remove auto-scroll for vertical list
        // stopAutoScroll();
    }

    // We can keep these methods but we won't call them
    private void startAutoScroll() {
        // Remove any existing callbacks to avoid duplicates
        stopAutoScroll();
        // Start auto-scrolling
        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
    }

    private void stopAutoScroll() {
        // Remove the auto-scroll callback
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
    }

    private void fetchEducationNews() {
        if (newsApi == null || !isAdded()) return;
        
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        String apiKey = getString(R.string.news_api_key);
        
        newsApi.getEducationNews(
            "\"DepEd\" OR \"Department of Education Philippines\" OR \"Philippine education\" OR \"Philippine schools\"",
            "publishedAt",
            "en",
            apiKey
        ).enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (!isAdded()) return;
                
                newsProgress.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null && 
                    !response.body().getArticles().isEmpty()) {
                    
                    List<NewsArticle> articles = response.body().getArticles();
                    newsAdapter.setNewsArticles(articles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Start auto-scrolling once we have news articles
                    startAutoScroll();
                } else {
                    showNewsError("Failed to load DepEd news");
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                if (!isAdded()) return;
                
                newsProgress.setVisibility(View.GONE);
                showNewsError("Network error: " + t.getMessage());
            }
        });
    }

    private void showNewsError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), "News error: " + message, Toast.LENGTH_SHORT).show();
        }
        newsRecyclerView.setVisibility(View.GONE);
    }

    private void fetchBuksuNews() {
        if (newsApi == null || !isAdded()) return;
        
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        String apiKey = getString(R.string.news_api_key);
        
        // Create a specific query for Bukidnon State University
        String query = "\"Bukidnon State University\" OR \"BukSU\" OR \"BSU Bukidnon\" OR buksu.edu.ph";
        
        newsApi.getSchoolNews(
            query,
            "publishedAt",
            "en",
            apiKey
        ).enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (!isAdded()) return;
                
                newsProgress.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null && 
                    !response.body().getArticles().isEmpty()) {
                    
                    List<NewsArticle> articles = response.body().getArticles();
                    newsAdapter.setNewsArticles(articles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    // If no results from NewsAPI, try RSS feed approach
                    fetchBuksuRssFeed();
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                if (!isAdded()) return;
                
                // On failure, try RSS feed approach
                fetchBuksuRssFeed();
            }
        });
    }

    private void fetchBuksuRssFeed() {
        if (rssFeedService == null || !isAdded()) return;
        
        // Try to fetch from Bukidnon State University's own RSS feed
        String buksuFeedUrl = "https://buksu.edu.ph/feed/";
        
        rssFeedService.fetchFeed(buksuFeedUrl, new RssFeedService.RssFeedCallback() {
            @Override
            public void onSuccess(List<NewsArticle> newsArticles) {
                if (!isAdded()) return;
                
                getActivity().runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    
                    if (!newsArticles.isEmpty()) {
                        newsAdapter.setNewsArticles(newsArticles);
                        newsRecyclerView.setVisibility(View.VISIBLE);
                    } else {
                        showNewsError("No BukSU news found");
                    }
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                if (!isAdded()) return;
                
                getActivity().runOnUiThread(() -> {
                    newsProgress.setVisibility(View.GONE);
                    showNewsError("Error loading BukSU news: " + e.getMessage());
                });
            }
        });
    }

    private void showSchoolSelectionDialog() {
        if (!isAdded() || getContext() == null) return;
        
        // List of popular schools in the Philippines
        final String[] popularSchools = new String[] {
            "Bukidnon State University", // BukSU at the top
            "University of the Philippines",
            "Ateneo de Manila University",
            "De La Salle University",
            "University of Santo Tomas",
            "Polytechnic University of the Philippines",
            "University of San Carlos",
            "Mindanao State University",
            "Central Mindanao University",
            "Xavier University",
            "Far Eastern University",
            "Silliman University",
            "Other (Search)"  // Last option to search for a specific school
        };

        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Select School")
            .setItems(popularSchools, (dialog, which) -> {
                if (which == popularSchools.length - 1) {
                    // Last option is "Other (Search)" - show search dialog
                    showCustomSchoolSearchDialog();
                } else if (which == 0) {
                    // First option is "Bukidnon State University" - use specialized method
                    fetchBuksuNews();
                } else {
                    // Fetch news for the selected school
                    fetchSchoolNews(popularSchools[which]);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showCustomSchoolSearchDialog() {
        if (!isAdded() || getContext() == null) return;
        
        // Create an EditText for the dialog
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Enter school name");
        
        // Create a layout to add padding
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding);
        layout.setPadding(padding, padding, padding, padding);
        layout.addView(input);
        
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("School News")
            .setView(layout)
            .setPositiveButton("Search", (dialog, which) -> {
                String schoolName = input.getText().toString().trim();
                if (!schoolName.isEmpty()) {
                    fetchSchoolNews(schoolName);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void fetchSchoolNews(String schoolName) {
        if (newsApi == null || !isAdded()) return;
        
        newsProgress.setVisibility(View.VISIBLE);
        newsRecyclerView.setVisibility(View.GONE);
        
        String apiKey = getString(R.string.news_api_key);
        
        // Create a query that combines the school name with education-related terms
        String query = "\"" + schoolName + "\" AND (education OR school OR students OR teachers)";
        
        newsApi.getSchoolNews(
            query,
            "publishedAt",
            "en",
            apiKey
        ).enqueue(new Callback<NewsResponse>() {
            @Override
            public void onResponse(Call<NewsResponse> call, Response<NewsResponse> response) {
                if (!isAdded()) return;
                
                newsProgress.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null && 
                    !response.body().getArticles().isEmpty()) {
                    
                    List<NewsArticle> articles = response.body().getArticles();
                    newsAdapter.setNewsArticles(articles);
                    newsRecyclerView.setVisibility(View.VISIBLE);
                    
                    // Start auto-scrolling once we have news articles
                    startAutoScroll();
                } else {
                    showNewsError("No news found for " + schoolName);
                }
            }
            
            @Override
            public void onFailure(Call<NewsResponse> call, Throwable t) {
                if (!isAdded()) return;
                
                newsProgress.setVisibility(View.GONE);
                showNewsError("Network error: " + t.getMessage());
            }
        });
    }
}












