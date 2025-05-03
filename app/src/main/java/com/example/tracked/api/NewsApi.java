package com.example.tracked.api;

import com.example.tracked.model.NewsResponse;
import retrofit2.Call;
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
}
