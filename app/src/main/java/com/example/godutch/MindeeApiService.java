package com.example.godutch;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface MindeeApiService {
    @Multipart
    @POST("/products/mindee/expense_receipts/v5/predict")
    Call<ResponseBody> uploadImage(
            @Header("Authorization") String authorization,
            @Part MultipartBody.Part document
    );
}

