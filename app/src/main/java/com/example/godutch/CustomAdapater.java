package com.example.godutch;

import android.content.ClipData;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;


import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomAdapater extends RecyclerView.Adapter<CustomAdapater.MyViewHolder> {

     Context context;
     private ArrayList<FoodItem> foodItems;
    double totalPrice = 0.0;

    //Constructor
    CustomAdapater(Context context,ArrayList<FoodItem> foodItems){
        //Declares to global variables that can be used in the MainAcivity
        this.context = context;
        this.foodItems= foodItems;

    }

    @NonNull
    @Override
    public CustomAdapater.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        //Inflates the item_row layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.item_row, parent, false);

        return new MyViewHolder(view);
    }



    public void onBindViewHolder(@NonNull CustomAdapater.MyViewHolder holder, int position) {
        FoodItem foodItem = foodItems.get(position);

        holder.foodPrice_txt.setText(String.valueOf(foodItem.getPrice() + "$"));
        holder.checkItem.setText(" " + foodItem.getName());
        holder.checkItem.setChecked(foodItem.isChecked());

        holder.checkItem.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            foodItem.setChecked(isChecked);
            calculateTotalPrice();

            if(context instanceof SecondScreen){
                ((SecondScreen) context).updateTotalPrice(totalPrice);
            }
        });
    }


    @Override
    public int getItemCount() {
        return foodItems.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        TextView foodPrice_txt;
        CheckBox checkItem;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            checkItem = itemView.findViewById(R.id.chkItemRow);
            foodPrice_txt = itemView.findViewById(R.id.txtPriceRow);

        }
    }


    private void calculateTotalPrice() {
        totalPrice = 0.0;
        for (FoodItem item : foodItems) {
            if (item.isChecked()) {
                totalPrice += item.getPrice();
            }
        }
    }

    // Get the total price
    public double getTotalPrice() {
        return totalPrice;
    }
}
