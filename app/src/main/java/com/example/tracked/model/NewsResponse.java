package com.example.tracked.model;

import java.util.List;

public class NewsResponse {
    private String status;
    private int totalResults;
    private List<NewsArticle> articles;

    public List<NewsArticle> getArticles() {
        return articles;
    }
}


