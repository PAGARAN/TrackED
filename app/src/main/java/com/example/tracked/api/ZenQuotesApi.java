 package com.example.tracked.api;

import com.example.tracked.model.Quote;
import retrofit2.Call;
import retrofit2.http.GET;
import java.util.List;

public interface ZenQuotesApi {
    @GET("/api/today")  // Add leading slash
    Call<List<Quote>> getTodayQuote();  // Renamed for clarity
}


