package com.example.godutch;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MindeeApiClient {
    private static final String BASE_URL = "https://api.mindee.net/v1";

    private static MindeeApiService sApiService;

    public static MindeeApiService getInstance() {
        if (sApiService == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            sApiService = retrofit.create(MindeeApiService.class);

        }
        return sApiService;
    }
}
