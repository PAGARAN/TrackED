package com.example.tracked;

import android.app.Application;
import com.jakewharton.threetenabp.AndroidThreeTen;

public class TrackEDApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize ThreeTenABP
        AndroidThreeTen.init(this);
    }
}
