package com.example.godutch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class MyDataBaseHelper extends SQLiteOpenHelper {

    private final Context context;
    private static final String DATABASE_NAME = "FoodItem.db";
    private static final int DATABASE_VERSION = 1;

    //Declarations
    public static final String TABLE_NAME = "my_food_items";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_FOOD_NAME = "food_name";
    public static final String COLUMN_FOOD_PRICE = "food_price";

    public MyDataBaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        //Sql query
       String query =
               "CREATE TABLE " + TABLE_NAME +
                       " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                       COLUMN_FOOD_NAME + " TEXT, " +
                       COLUMN_FOOD_PRICE + " TEXT);";

       db.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
       db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
       onCreate(db);
    }

    void addFood(String name, String price){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();

        cv.put(COLUMN_FOOD_NAME, name);
        cv.put(COLUMN_FOOD_PRICE, price);

        long result = db.insert(TABLE_NAME, null, cv);

        //Checks if the items have been added
        if(result == -1){
            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show();
        }

    }

    Cursor readAllData(){
        //Selects all the data from the sqlite table
        String query  = "SELECT * FROM " + TABLE_NAME;
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = null;
        //Checks if there is data in the table
        if(db != null){
            //Declares all the data from the table to cursors variable
          cursor = db.rawQuery(query, null);
        }
        return cursor;
    }

    public void clearTable() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_NAME);
    }

    public void deleteRow(String row_id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_ID + "=?", new String[]{row_id});
    }

}
