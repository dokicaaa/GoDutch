package com.example.godutch;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import android.app.AlarmManager;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;
import android.widget.EditText;

import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class SecondScreen extends AppCompatActivity {


    private static final int REQUEST_IMAGE_CAPTURE = 1;

    //Initializations of UI elements
    TextView txtSeekBarPercent;
//    TextView txtRes;
    SeekBar seekBarTipPercent;
    Button btnRescan;
    Button btnAddItem;
    Vibrator vibrator;

    //Adapter initializations
//    ArrayList<String> foodID, foodName, foodPrice;
    ArrayList<FoodItem> foodItems;
    CustomAdapater customAdapater;
    RecyclerView recyclerView;


    //DB
    MyDataBaseHelper myDB = new MyDataBaseHelper(SecondScreen.this);


    //PopUp initializations
    EditText edtFoodName;
    EditText edtFoodPrice;
    Dialog myDialog;
    Button btnAddFood;
    TextView cancel;

    //CALCULATION VARIABLES
    private final DecimalFormat decimalFormat = new DecimalFormat("0.00");
    private double totalPrice;

    private InterstitialAd mInterstitialAd;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.second_screen);

        //Declarations'
        txtSeekBarPercent = findViewById(R.id.txtSeekBarProcent);
        seekBarTipPercent = findViewById(R.id.seekBarTipPercent);
        btnRescan = findViewById(R.id.btnRetake);
        btnAddItem = findViewById(R.id.btnAddItem);
        recyclerView = findViewById(R.id.recycleView);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
//        txtRes = findViewById(R.id.txtRes);

        //Calling from adapter
        myDB = new MyDataBaseHelper(SecondScreen.this);


        foodItems = new ArrayList<>();

        //Camera Function
        captureImage();

//        scheduleNotification();

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });

        //Slider for tip percentage
        seekBarTipPercent.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                //Sets the progress bar and multiplies it by five
                String progressBarText = progress * 5 + "%";
                txtSeekBarPercent.setText(progressBarText);

                //Updates the tip and total price when moving the slider
                calculateTip();
                updateTotalPrice(totalPrice);

                //Vibration
                vibrate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        //Button for rescan image
        btnRescan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadAd();
            }
        });

        //Button for adding items
        btnAddItem.setOnClickListener(v -> popUp());

        //Adapter
//        customAdapater = new CustomAdapater(SecondScreen.this, foodID, foodName, foodPrice);
        customAdapater = new CustomAdapater(SecondScreen.this, foodItems);
        recyclerView.setLayoutManager(new LinearLayoutManager(SecondScreen.this));
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        //Calculates the total price
        calculateTotalPrice();

        //Assigns the totalPrice variable from the Adapter to a textView
        updateTotalPrice(totalPrice);

        //Notification Channel
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            CharSequence name = "Reminder Notifications";
//            String description = "Receive reminders to use the app";
//            int importance = NotificationManager.IMPORTANCE_DEFAULT;
//            NotificationChannel channel = new NotificationChannel("reminder_channel", name, importance);
//            channel.setDescription(description);
//
//            NotificationManager notificationManager = getSystemService(NotificationManager.class);
//            notificationManager.createNotificationChannel(channel);
//        }
//
//        reminderNotif();
//
//        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
//        Calendar calendar = Calendar.getInstance();
//        calendar.add(Calendar.SECOND, 5);
//
//        Intent intent = new Intent("android.media.action.DISPLAY_NOTIFICATION");
//        PendingIntent broadcast = PendingIntent.getBroadcast(this,  100, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), broadcast);
    }

    //Swipe action for deleting item in RecycleView
    ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            int position = viewHolder.getAdapterPosition();

            if(position >= 0 && position < foodItems.size()){
                // Get the ID of the item to be removed
                FoodItem deletedItem = foodItems.get(position);

                // Remove the item from the DB
                myDB.deleteRow(String.valueOf(deletedItem.getId()));

                // Subtract the price of the deleted item from the total price
                totalPrice -= deletedItem.getPrice();

                // Remove the item from the ArrayLists
                foodItems.remove(position);

                // Notify the adapter of the item removal
                customAdapater.notifyItemRemoved(position);
                customAdapater.notifyItemRangeChanged(position, foodItems.size());

                View parentLayout = findViewById(android.R.id.content);
                final Snackbar snackbar = Snackbar.make(parentLayout, "", Snackbar.LENGTH_LONG);

                View customSnackbarView = getLayoutInflater().inflate(R.layout.custom_snackbar, null);

                Button btnUndo = customSnackbarView.findViewById(R.id.btnUndo);
                btnUndo.setOnClickListener(view1 -> {

                    //Undo the removal by adding the item back at the same position
                    foodItems.add(position, deletedItem);

                    // Add the price of the undeleted item back to the total price
                    totalPrice += deletedItem.getPrice();

                    //Notify the adapter
                    customAdapater.notifyItemInserted(position);

                    //Re-adds the deleted row in DB
                    myDB.addFood(deletedItem.getName(), String.valueOf(deletedItem.getPrice()));

                    // Update the total price and tip
                    updateTotalPrice(totalPrice);

                    snackbar.dismiss();

                });

                // Set the custom view to the Snackbar and show it
                Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();
                snackbarLayout.setBackgroundColor(Color.TRANSPARENT);
                snackbarLayout.setPadding(0,0,0,20);
                snackbarLayout.addView(customSnackbarView, 0);

                snackbar.show();

                //Update the price and tip
                updateTotalPrice(totalPrice);
            }
        }

        //Swipe delete fun customisation
        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            new RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    .addSwipeRightBackgroundColor(ContextCompat.getColor(SecondScreen.this, R.color.Delete))
                    .addSwipeRightActionIcon(R.drawable.delete_32)
                    .addCornerRadius(TypedValue.COMPLEX_UNIT_DIP,14)
                    .create()
                    .decorate();
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    };

    //POPUP for adding items
    public void popUp() {
        myDialog = new Dialog(SecondScreen.this);
        myDialog.setContentView(R.layout.add_food_popup);
        myDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        myDialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        myDialog.setCancelable(false);
        myDialog.show();

        edtFoodName = myDialog.findViewById(R.id.edtFoodName);
        edtFoodPrice = myDialog.findViewById(R.id.edtFoodPrice);
        btnAddFood = myDialog.findViewById(R.id.btnAddFood);
        cancel = myDialog.findViewById(R.id.popUpCancel);

        //Button for adding items in popup
        btnAddFood.setOnClickListener(v -> {
            myDB.addFood(edtFoodName.getText().toString().trim(), edtFoodPrice.getText().toString().trim());
            storeDataInArrays();
            recyclerView.setAdapter(customAdapater);

            // Recalculate the total price after adding the item
            calculateTotalPrice();
            updateTotalPrice(totalPrice);
            customAdapater.notifyDataSetChanged();


            //Dismisses the popUP
            myDialog.dismiss();
        });

        cancel.setOnClickListener(v -> myDialog.dismiss());
    }

    //Storing the data from the sql table
    public void storeDataInArrays() {
        Cursor cursor = myDB.readAllData();

        //Clears the table
        foodItems.clear();

        if (cursor.getCount() == 0) {
            Log.i("TAG", "No data");
        } else {
             while(cursor.moveToNext()){
                 int id = cursor.getInt(0);
                 String name = cursor.getString(1);
                 double price = Double.parseDouble(cursor.getString(2));
                 FoodItem foodItem = new FoodItem(id, name, price, true);
                 foodItems.add(foodItem);
             }
        }
        customAdapater.notifyDataSetChanged(); // Notify the adapter about the data change
    }

    // Method to update the total price based on checked items
    public void calculateTotalPrice(){
        totalPrice = 0.0;
        for(FoodItem item : foodItems){
            if(item.isChecked()){
                totalPrice += item.getPrice();
            }
        }
    }

    //Updates the total price
    public void updateTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
        calculateTotalPrice();
        calculateTip();

        // Calculate the tip amount
        double tipPercentage = seekBarTipPercent.getProgress() * 5;
        double tipAmount = totalPrice * (tipPercentage / 100.0);

        // Calculate the total amount
        double totalAmount = totalPrice + tipAmount;

        if(totalAmount < 0){
            totalAmount = 0.0;
        }

        // Format the total amount to two decimal places
        String formattedTotalAmount = decimalFormat.format(totalAmount);

        // Find the txtTotal TextView
        TextView txtTotal = findViewById(R.id.txtTotal);

        // Set the total amount to the TextView
        txtTotal.setText(formattedTotalAmount + "$");
    }

    //Method to handle adding new items to list
    private void addItemToFoodList(String name, double price){
        FoodItem newItem = new FoodItem(foodItems.size() + 1, name, price,true);
        foodItems.add(newItem);
        customAdapater.notifyItemInserted(foodItems.size() - 1);
    }

    //Calculation for tips
    public void calculateTip() {
        //Gets the Percentage
        double tipPercentage = seekBarTipPercent.getProgress() * 5;
        //Formula
        double tipAmount = totalPrice * (tipPercentage / 100.0);

        // Format the tip and total amounts
        TextView txtTip = findViewById(R.id.txtTipAmount);
        String formattedTipAmount = decimalFormat.format(tipAmount);

        txtTip.setText(formattedTipAmount + "$");
    }

    //Camera Permissions
    ActivityResultLauncher<Intent> startActivityForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.i("Camera Permissions", "Camera Permissions Granted");
                } else {
                    Log.i("Camera Permissions", "Camera Permissions Denied");

                }
            }
    );


    //Vibration for seekbar
    private void vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.EFFECT_TICK));
        } else {
            // Use a simple duration for older devices
            vibrator.vibrate(40);
        }
    }

    //Notification
//    public void reminderNotif(){
//        // Create a Calendar object with the desired time (10:00 AM)
//        Calendar calendar = Calendar.getInstance();
//        calendar.set(Calendar.HOUR_OF_DAY, 21);
//        calendar.set(Calendar.MINUTE, 25);
//        calendar.set(Calendar.SECOND, 0);
//
//        // Check if the specified time has already passed today
//        if (System.currentTimeMillis() > calendar.getTimeInMillis()) {
//            // If it has, schedule it for the next day
//            calendar.add(Calendar.DAY_OF_YEAR, 1);
//        }
//
//        // Create an Intent to trigger the notification
//        Intent notificationIntent = new Intent(this, NotificationReceiver.class);
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        // Schedule the daily reminder
//        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
//        alarmManager.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
//
//    }

//    public void makeNotif(){
//        String channelID = "CHANNEL_ID_NOTIFICATION";
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelID);
//        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
//                .setContentTitle("Dining Out Tonight?")
//                .setContentText("Don't stress about the check!Split bills and calculate tips with a simple scan 🍽️")
//                .setAutoCancel(true)
//                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
//
//        Intent intent = new Intent(getApplicationContext(), FirstScreen.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//
//        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_MUTABLE);
//        builder.setContentIntent(pendingIntent);
//
//        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
//            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelID);
//            if(notificationChannel == null){
//                int importance = NotificationManager.IMPORTANCE_HIGH;
//                notificationChannel = new NotificationChannel(channelID, "Reminder", importance);
//                notificationChannel.setLightColor(Color.GREEN);
//                notificationChannel.enableVibration(true);
//                notificationManager.createNotificationChannel(notificationChannel);
//            }
//        }
//        notificationManager.notify(0,builder.build());
//
//    }

//    private void scheduleNotification() {
//        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
//        Intent notificationIntent = new Intent(this, NotificationReceiver.class);
//
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        // Create a Calendar instance
//        Calendar calendar = Calendar.getInstance();
//
//        // Set the desired hour, minute, and second for the notification
//        calendar.set(Calendar.HOUR_OF_DAY, 14); // Set the hour (e.g., 10 AM)
//        calendar.set(Calendar.MINUTE, 30);      // Set the minute
//        calendar.set(Calendar.SECOND, 0);      // Set the second
//
//        // Calculate the next trigger time
////        long delay = 4 * 24 * 60 * 60 * 1000; // 4 days in milliseconds
////        long triggerTime = calendar.getTimeInMillis() + delay;
//
//        // Create a notification channel
//        createNotificationChannel();
//
//        // Schedule the notification
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, AlarmManager.INTERVAL_DAY, pendingIntent);
//        } else {
//            alarmManager.setExact(AlarmManager.RTC_WAKEUP, AlarmManager.INTERVAL_DAY , pendingIntent);
//        }
//    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelID = "CHANNEL_ID_NOTIFICATION";
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelID);
            if (notificationChannel == null) {
                int importance = NotificationManager.IMPORTANCE_HIGH;
                notificationChannel = new NotificationChannel(channelID, "Reminder", importance);
                notificationChannel.setLightColor(Color.GREEN);
                notificationChannel.enableVibration(true);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
    }

    //Camera Intent
    public void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult.launch(intent);
    }

    //Save the image as a BitMap
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_IMAGE_CAPTURE) {
            Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
        }
    }

    public void interstitialAdd(){
        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(this,"ca-app-pub-3940256099942544~3347511713", adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                        Log.i(TAG, "onAdLoaded");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                        Log.e("TAG", "onAdFailedToLoad: " + loadAdError.getMessage());
                    }
                });

    }

    public void loadAd(){

        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(this,"ca-app-pub-3940256099942544/1033173712", adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {

//                        Toast.makeText(SecondScreen.this,"Ad Loaded", Toast.LENGTH_SHORT).show();
                        interstitialAd.show(SecondScreen.this);
                        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                super.onAdFailedToShowFullScreenContent(adError);
//                                Toast.makeText(SecondScreen.this, "Faild to show Ad", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                super.onAdShowedFullScreenContent();
//                                Toast.makeText(SecondScreen.this,"Ad Shown Successfully",Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                super.onAdDismissedFullScreenContent();

                                //Launches the camera when dismissing the ad
                                captureImage();

//                                Toast.makeText(SecondScreen.this,"Ad Dismissed / Closed",Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onAdImpression() {
                                super.onAdImpression();
//                                Toast.makeText(SecondScreen.this,"Ad Impression Count",Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onAdClicked() {
                                super.onAdClicked();
//                                Toast.makeText(SecondScreen.this,"Ad Clicked",Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Toast.makeText(SecondScreen.this,"Failed to Load Ad because="+loadAdError.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                });

    }

    public void showInterstitialAdd() {
        if (mInterstitialAd != null) {
            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    // Ad dismissed callback
                    Log.d("TAG", "The ad was dismissed.");
                    captureImage(); // Capture the image after ad is dismissed
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    // Ad failed to show callback
                    Log.d("TAG", "The ad failed to show.");
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    // Ad showed callback
                    mInterstitialAd = null;
                    Log.d("TAG", "The ad was shown.");
                }
            });

            mInterstitialAd.show(SecondScreen.this);
        } else {
            Log.d("TAG", "The interstitial ad wasn't ready yet.");
            captureImage(); // Fallback to capture image if ad isn't ready
        }
    }

    //Deletes the Sql table every time the app is closed
    @Override
    protected void onPause() {
        super.onPause();
        myDB.clearTable();
    }
}

