package com.example.tracked.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tracked.R;
import com.example.tracked.model.NewsArticle;

import java.util.ArrayList;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {
    private static final String TAG = "NewsAdapter";
    private List<NewsArticle> newsArticles = new ArrayList<>();
    private Context context;

    public NewsAdapter(Context context) {
        this.context = context;
    }

    public void setNewsArticles(List<NewsArticle> newsArticles) {
        if (newsArticles == null) {
            this.newsArticles = new ArrayList<>();
        } else {
            this.newsArticles = newsArticles;
        }
        notifyDataSetChanged();
    }

    /**
     * Validates if the URL is likely to be a valid image URL
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // Check if URL has common image extensions
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".jpg") || 
               lowerUrl.endsWith(".jpeg") || 
               lowerUrl.endsWith(".png") || 
               lowerUrl.endsWith(".gif") || 
               lowerUrl.endsWith(".webp") ||
               lowerUrl.contains("image") ||
               lowerUrl.contains("photo");
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Log the layout inflation to help debug
        Log.d(TAG, "Creating new ViewHolder");
        
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        // Log the binding to help debug
        Log.d(TAG, "Binding ViewHolder at position " + position);
        
        if (position >= newsArticles.size()) {
            Log.e(TAG, "Position out of bounds: " + position + ", size: " + newsArticles.size());
            return;
        }
        
        NewsArticle article = newsArticles.get(position);
        if (article == null) {
            Log.e(TAG, "Article at position " + position + " is null");
            return;
        }
        
        // Bind data to views with null checks
        try {
            // Set title
            if (holder.newsTitle != null) {
                String title = article.getTitle();
                holder.newsTitle.setText(title != null ? title : "");
                Log.d(TAG, "Set title: " + (title != null ? title : "null"));
            } else {
                Log.e(TAG, "newsTitle view is null");
            }
            
            // Set image with improved validation
            if (holder.newsImage != null) {
                String imageUrl = article.getUrlToImage();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    if (isValidImageUrl(imageUrl)) {
                        Log.d(TAG, "Loading valid image from: " + imageUrl);
                        
                        // Use Glide with proper settings to fill the container
                        Glide.with(context)
                            .load(imageUrl)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .centerCrop()
                            .into(holder.newsImage);
                    } else {
                        Log.d(TAG, "Invalid image URL: " + imageUrl);
                        holder.newsImage.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                } else {
                    Log.d(TAG, "No image URL available, using placeholder");
                    holder.newsImage.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                Log.e(TAG, "newsImage view is null");
            }
            
            // Set source name
            if (holder.newsSource != null) {
                String source = "";
                // Check if we can get source from article properties
                try {
                    if (article.getSourceName() != null) {
                        source = article.getSourceName();
                    } else if (article.getUrl() != null) {
                        // Extract domain from URL as fallback
                        String url = article.getUrl();
                        if (url.startsWith("http")) {
                            Uri uri = Uri.parse(url);
                            source = uri.getHost().replaceAll("^www\\.", "");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting source", e);
                }
                
                if (source.isEmpty()) {
                    source = "News";
                }
                
                holder.newsSource.setText(source);
                holder.newsSource.setVisibility(View.VISIBLE);
            }
            
            // Set click listener
            if (holder.newsCard != null) {
                holder.newsCard.setOnClickListener(v -> {
                    String url = article.getUrl();
                    if (url != null && !url.isEmpty()) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        context.startActivity(browserIntent);
                        Log.d(TAG, "Opening URL: " + url);
                    }
                });
            } else {
                Log.e(TAG, "newsCard view is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding ViewHolder", e);
        }
    }

    @Override
    public int getItemCount() {
        return newsArticles != null ? newsArticles.size() : 0;
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        ImageView newsImage;
        TextView newsTitle;
        TextView newsSource;
        CardView newsCard;

        NewsViewHolder(View itemView) {
            super(itemView);
            try {
                newsCard = itemView.findViewById(R.id.newsCard);
                newsImage = itemView.findViewById(R.id.newsImage);
                newsTitle = itemView.findViewById(R.id.newsTitle);
                newsSource = itemView.findViewById(R.id.newsSource);
                
                // Log the view IDs to help debug
                Log.d("NewsAdapter", "ViewHolder initialized with: " +
                      "newsCard=" + (newsCard != null) + 
                      ", newsImage=" + (newsImage != null) + 
                      ", newsTitle=" + (newsTitle != null) +
                      ", newsSource=" + (newsSource != null));
            } catch (Exception e) {
                Log.e("NewsAdapter", "Error finding views in news item layout", e);
            }
        }
    }
}










