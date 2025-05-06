package com.example.tracked.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.tracked.R;
import com.example.tracked.model.NewsArticle;

import java.util.ArrayList;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {
    private List<NewsArticle> newsArticles = new ArrayList<>();
    private Context context;

    public NewsAdapter(Context context) {
        this.context = context;
    }

    public void setNewsArticles(List<NewsArticle> newsArticles) {
        // Accept all articles, even those without images
        this.newsArticles = newsArticles;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        NewsArticle article = newsArticles.get(position);
        
        // Set title with proper handling for null values
        holder.newsTitle.setText(article.getTitle() != null ? article.getTitle() : "");
        
        // Set description with proper handling for null values
        String description = article.getDescription();
        if (description != null && description.length() > 0) {
            holder.newsDescription.setText(description);
            holder.newsDescription.setVisibility(View.VISIBLE);
        } else {
            holder.newsDescription.setVisibility(View.GONE);
        }
        
        // Load image using Glide with improved fitting and transitions
        if (article.getUrlToImage() != null && !article.getUrlToImage().isEmpty()) {
            Glide.with(context)
                .load(article.getUrlToImage())
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.newsImage);
        } else {
            // Set a solid color background for articles without images
            holder.newsImage.setBackgroundColor(context.getResources().getColor(android.R.color.holo_blue_light));
            // Clear any previous image
            holder.newsImage.setImageDrawable(null);
        }
        
        // Make the card clickable to open the full article
        holder.newsCard.setOnClickListener(v -> {
            if (article.getUrl() != null && !article.getUrl().isEmpty()) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                    Uri.parse(article.getUrl()));
                context.startActivity(browserIntent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return newsArticles.size();
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        ImageView newsImage;
        TextView newsTitle;
        TextView newsDescription;
        CardView newsCard;

        NewsViewHolder(View itemView) {
            super(itemView);
            newsImage = itemView.findViewById(R.id.newsImage);
            newsTitle = itemView.findViewById(R.id.newsTitle);
            newsDescription = itemView.findViewById(R.id.newsDescription);
            newsCard = itemView.findViewById(R.id.newsCard);
        }
    }
}











