package com.example.tracked.model;

import com.google.gson.annotations.SerializedName;

public class Weather {
    @SerializedName("weather")
    private WeatherInfo[] weather;
    
    @SerializedName("main")
    private MainInfo main;

    public WeatherInfo[] getWeather() {
        return weather;
    }

    public MainInfo getMain() {
        return main;
    }

    public static class WeatherInfo {
        @SerializedName("description")
        private String description;
        
        @SerializedName("icon")
        private String icon;

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
    }

    public static class MainInfo {
        @SerializedName("temp")
        private double temperature;

        public double getTemperature() {
            return temperature;
        }
    }
}
