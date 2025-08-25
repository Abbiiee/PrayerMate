package com.ebaa.prayermate;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PrayerTimesApi {
    @GET("timings")
    Call<PrayerTimesResponse> getPrayerTimes(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("date") String date
    );
}