package com.ebaa.prayermate;

import com.google.gson.annotations.SerializedName;

public class PrayerTimesResponse {
    @SerializedName("code")
    private int code;

    @SerializedName("status")
    private String status;

    @SerializedName("data")
    private Data data;

    public int getCode() {
        return code;
    }

    public String getStatus() {
        return status;
    }

    public Data getData() {
        return data;
    }

    public static class Data {
        @SerializedName("timings")
        private Timings timings;

        @SerializedName("date")
        private DateInfo date;

        public Timings getTimings() {
            return timings;
        }

        public DateInfo getDate() {
            return date;
        }
    }

    public static class Timings {
        @SerializedName("Fajr")
        private String fajr;

        @SerializedName("Sunrise")
        private String sunrise;

        @SerializedName("Dhuhr")
        private String dhuhr;

        @SerializedName("Asr")
        private String asr;

        @SerializedName("Maghrib")
        private String maghrib;

        @SerializedName("Isha")
        private String isha;

        public String getFajr() {
            return fajr;
        }

        public String getSunrise() {
            return sunrise;
        }

        public String getDhuhr() {
            return dhuhr;
        }

        public String getAsr() {
            return asr;
        }

        public String getMaghrib() {
            return maghrib;
        }

        public String getIsha() {
            return isha;
        }
    }

    public static class DateInfo {
        @SerializedName("readable")
        private String readable;

        @SerializedName("timestamp")
        private String timestamp;

        public String getReadable() {
            return readable;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }
}