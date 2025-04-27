package com.example.tracked.model;

import com.google.gson.annotations.SerializedName;

public class Quote {
    @SerializedName("q")
    private String text;
    
    @SerializedName("a")
    private String author;

    public String getText() {
        return text;
    }

    public String getAuthor() {
        return author;
    }
}

