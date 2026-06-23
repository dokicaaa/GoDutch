package com.example.godutch;

import android.util.Log;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {

    private static final String TAG = "ReceiptParser";

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$?\\s*(\\d+\\.\\d{2})\\s*$");

    private static final String[] EXCLUDED_KEYWORDS = {
            "total", "subtotal", "sub total", "tax", "gst", "hst", "pst", "vat",
            "tip", "gratuity", "change", "cash", "card", "visa", "master",
            "amex", "credit", "debit", "payment", "balance", "due",
            "date", "time", "receipt", "invoice", "order", "check",
            "thank", "welcome", "visit", "again", "appreciate",
            "phone", "tel", "fax", "web", "www", "email",
            "server", "table", "guest", "party", "cover",
            "discount", "coupon", "offer", "promo", "reward",
            "fee", "service charge", "delivery", "bag charge",
            "amount", "tendered", "return", "refund", "void",
            "net", "gross", "rounding", "adjustment"
    };

    public static ArrayList<FoodItem> parse(String rawText) {
        ArrayList<FoodItem> items = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            Log.w(TAG, "Empty OCR text received");
            return items;
        }

        String[] lines = rawText.split("\n");
        Log.d(TAG, "Attempting to parse " + lines.length + " lines");
        int idCounter = 1;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = PRICE_PATTERN.matcher(trimmed);
            if (!matcher.find()) {
                Log.v(TAG, "Line " + i + " - NO price match: '" + trimmed + "'");
                continue;
            }

            String priceStr = matcher.group(1);
            String itemName = trimmed.substring(0, matcher.start()).trim();
            Log.d(TAG, "Line " + i + " - Price match: name='" + itemName + "', priceStr='" + priceStr + "'");

            double price;
            try {
                price = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Line " + i + " - Bad price format: " + priceStr);
                continue;
            }

            if (price <= 0.0 || price > 9999.99) {
                Log.d(TAG, "Line " + i + " - Price out of range: " + price);
                continue;
            }

            if (itemName.isEmpty()) {
                Log.d(TAG, "Line " + i + " - Empty item name");
                continue;
            }

            if (shouldExclude(itemName, price)) {
                Log.d(TAG, "Line " + i + " - Excluded: '" + itemName + "'");
                continue;
            }

            itemName = cleanItemName(itemName);
            if (itemName.isEmpty()) {
                Log.d(TAG, "Line " + i + " - Empty after cleaning");
                continue;
            }

            Log.d(TAG, "Line " + i + " - ACCEPTED: '" + itemName + "' $" + price);
            items.add(new FoodItem(idCounter++, itemName, price, true));
        }

        Log.d(TAG, "Parsed " + items.size() + " items from receipt");
        return items;
    }

    private static boolean shouldExclude(String name, double price) {
        String lower = name.toLowerCase().trim();

        for (String keyword : EXCLUDED_KEYWORDS) {
            if (lower.equals(keyword) || lower.startsWith(keyword + " ") || lower.endsWith(" " + keyword)) {
                return true;
            }
        }

        if (lower.matches(".*\\d{1,2}[/:]\\d{1,2}[/:]\\d{0,4}.*")) return true;
        if (lower.matches(".*\\d{1,2}:\\d{2}.*")) return true;

        if (lower.length() <= 2) return true;

        boolean hasLetter = false;
        for (char c : lower.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) return true;

        return false;
    }

    private static String cleanItemName(String name) {
        name = name.replaceAll("[^a-zA-Z0-9 &'.\\-()/,]", " ").trim();
        name = name.replaceAll("\\s+", " ").trim();
        return name;
    }
}
