package com.example.tracked.api;

import com.example.tracked.model.NewsResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface NewsApi {
    @GET("v2/everything")
    Call<NewsResponse> getEducationNews(
        @Query("q") String query,
        @Query("sortBy") String sortBy,
        @Query("language") String language,
        @Query("apiKey") String apiKey
    );

    // Add a new method for school-specific news
    @GET("v2/everything")
    Call<NewsResponse> getSchoolNews(
        @Query("q") String schoolName,
        @Query("sortBy") String sortBy,
        @Query("language") String language,
        @Query("apiKey") String apiKey
    );

    // Simplified search method used in the app
    @GET("v2/everything")
    Call<NewsResponse> searchNews(
        @Query("q") String query,
        @Query("apiKey") String apiKey
    );

    // Add top headlines method
    @GET("v2/top-headlines")
    Call<NewsResponse> getTopHeadlines(
        @Query("country") String country,
        @Query("category") String category,
        @Query("q") String query,
        @Query("apiKey") String apiKey
    );

    // Extension method for convenience
    default void searchNews(String query, Callback<NewsResponse> callback) {
        // Use a default API key - in a real app, this would be stored securely
        String apiKey = "your_api_key_here";
        searchNews(query, apiKey).enqueue(callback);
    }
}

