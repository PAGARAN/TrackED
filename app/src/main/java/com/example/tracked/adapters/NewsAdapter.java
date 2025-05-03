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
        this.newsArticles = newsArticles;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news_vertical, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        NewsArticle article = newsArticles.get(position);
        
        holder.newsTitle.setText(article.getTitle());
        holder.newsDescription.setText(article.getDescription());
        
        // Load image using Glide
        if (article.getUrlToImage() != null) {
            Glide.with(context)
                .load(article.getUrlToImage())
                .centerCrop()
                .placeholder(R.drawable.news_placeholder)
                .error(R.drawable.news_placeholder)
                .into(holder.newsImage);
        } else {
            holder.newsImage.setImageResource(R.drawable.news_placeholder);
        }
        
        // Make the card clickable to open the full article
        holder.newsCard.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                Uri.parse(article.getUrl()));
            context.startActivity(browserIntent);
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


