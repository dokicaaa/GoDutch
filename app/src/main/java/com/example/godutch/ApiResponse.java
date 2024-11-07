package com.example.godutch;
import com.google.gson.annotations.SerializedName;

public class ApiResponse {
    @SerializedName("itemName")
    private String itemName;

    @SerializedName("itemPrice")
    private double itemPrice;

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public double getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(double itemPrice) {
        this.itemPrice = itemPrice;
    }
}