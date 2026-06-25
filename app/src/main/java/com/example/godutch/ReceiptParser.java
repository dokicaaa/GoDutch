package com.example.godutch;

import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {

    private static final String TAG = "ReceiptParser";

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "\\$?\\s*(\\d+[.,]\\d{2})");

    private static final String[] STRONG_EXCLUDED = {
            "subtotal", "sub total", "total", "grand total",
            "tax", "gst", "hst", "pst", "vat",
            "tip", "gratuity", "service charge",
            "change", "balance", "amount due", "due",
            "payment", "tendered", "cash", "card",
            "visa", "master", "amex", "credit", "debit",
            "rounding", "adjustment", "discount",
            "staff", "member", "server", "cashier"
    };

    private static final String[] WEAK_EXCLUDED = {
            "receipt", "invoice", "order", "thank you", "welcome",
            "phone", "tel", "fax", "www", "email",
            "date", "time", "table", "guest", "party"
    };

    public static ArrayList<FoodItem> parse(Text visionText) {
        ArrayList<FoodItem> items = new ArrayList<>();
        if (visionText == null) {
            Log.w(TAG, "Null visionText received");
            return items;
        }

        String fullText = visionText.getText();
        if (fullText == null || fullText.trim().isEmpty()) {
            Log.w(TAG, "Empty OCR text");
            return items;
        }

        List<Text.Line> allLines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            allLines.addAll(block.getLines());
        }

        allLines.sort((a, b) -> {
            int aY = a.getBoundingBox() != null ? a.getBoundingBox().top : 0;
            int bY = b.getBoundingBox() != null ? b.getBoundingBox().top : 0;
            return Integer.compare(aY, bY);
        });

        Log.d(TAG, "Processing " + allLines.size() + " lines");

        boolean pastTotal = false;
        int idCounter = 1;

        for (int i = 0; i < allLines.size(); i++) {
            Text.Line line = allLines.get(i);

            if (pastTotal) {
                Log.d(TAG, "Line " + i + " - Skipped (past total): '" + line.getText() + "'");
                continue;
            }

            ParsedItem parsed = extractFromLine(line);
            if (parsed == null) {
                Log.v(TAG, "Line " + i + " - No price found: '" + line.getText() + "'");
                continue;
            }

            Log.d(TAG, "Line " + i + " - Extracted: name='" + parsed.name + "' price=" + parsed.price);

            if (isStrongExcluded(parsed.name)) {
                Log.d(TAG, "Line " + i + " - Strong excluded: '" + parsed.name + "'");
                if (parsed.name.toLowerCase().contains("total")) {
                    pastTotal = true;
                }
                continue;
            }

            if (isWeakExcluded(parsed.name)) {
                Log.d(TAG, "Line " + i + " - Weak excluded: '" + parsed.name + "'");
                continue;
            }

            if (parsed.price <= 0.0 || parsed.price > 9999.99) {
                Log.d(TAG, "Line " + i + " - Price out of range: " + parsed.price);
                continue;
            }

            String cleanName = cleanItemName(parsed.name);
            if (cleanName.isEmpty() || cleanName.length() <= 2) {
                Log.d(TAG, "Line " + i + " - Name too short after cleaning");
                continue;
            }

            if (!hasLetter(cleanName)) {
                Log.d(TAG, "Line " + i + " - No letters in name");
                continue;
            }

            if (isDateOrTime(cleanName)) {
                Log.d(TAG, "Line " + i + " - Date/time pattern: '" + cleanName + "'");
                continue;
            }

            Log.d(TAG, "Line " + i + " - ACCEPTED: '" + cleanName + "' $" + parsed.price);
            items.add(new FoodItem(idCounter++, cleanName, parsed.price, true));
        }

        Log.d(TAG, "Parsed " + items.size() + " items from receipt");
        return items;
    }

    private static ParsedItem extractFromLine(Text.Line line) {
        List<Text.Element> elements = new ArrayList<>(line.getElements());
        if (elements.isEmpty()) return null;

        elements.sort((a, b) -> {
            int aX = a.getBoundingBox() != null ? a.getBoundingBox().left : 0;
            int bX = b.getBoundingBox() != null ? b.getBoundingBox().left : 0;
            return Integer.compare(aX, bX);
        });

        int priceIdx = -1;
        double price = 0;
        Double secondaryPrice = null;

        for (int i = elements.size() - 1; i >= 0; i--) {
            Double p = tryParsePrice(elements.get(i).getText());
            if (p != null) {
                if (priceIdx == -1) {
                    priceIdx = i;
                    price = p;
                } else {
                    secondaryPrice = p;
                    break;
                }
            }
        }

        if (priceIdx == -1) return null;

        StringBuilder name = new StringBuilder();
        for (int i = 0; i < priceIdx; i++) {
            if (name.length() > 0) name.append(" ");
            name.append(elements.get(i).getText());
        }

        if (secondaryPrice != null && priceIdx == elements.size() - 1) {
            for (int i = elements.size() - 2; i >= 0; i--) {
                Double p = tryParsePrice(elements.get(i).getText());
                if (p != null) {
                    StringBuilder realName = new StringBuilder();
                    for (int j = 0; j < i; j++) {
                        if (realName.length() > 0) realName.append(" ");
                        realName.append(elements.get(j).getText());
                    }
                    if (realName.length() > 0) {
                        return new ParsedItem(realName.toString(), p);
                    }
                    break;
                }
            }
        }

        if (name.length() == 0) {
            return null;
        }

        return new ParsedItem(name.toString(), price);
    }

    private static Double tryParsePrice(String text) {
        if (text == null || text.isEmpty()) return null;

        String s = text.trim();

        s = s.replaceAll("[$€£\\s]", "");

        s = s.replace('O', '0')
                .replace('o', '0')
                .replace('l', '1')
                .replace('I', '1')
                .replace('B', '8')
                .replace('S', '5')
                .replace('Z', '2')
                .replace('g', '9')
                .replace('z', '2');

        s = s.replace(',', '.');

        Matcher m = PRICE_PATTERN.matcher(s);
        if (!m.matches()) {
            if (m.find()) {
                s = m.group(0).replaceAll("[$€£\\s]", "");
            } else {
                return null;
            }
        }

        s = s.replace(',', '.');

        try {
            double price = Double.parseDouble(s);
            if (price > 0 && price <= 9999.99) return price;
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static boolean isStrongExcluded(String name) {
        String lower = name.toLowerCase().trim();
        for (String keyword : STRONG_EXCLUDED) {
            String k = keyword.toLowerCase();
            if (lower.equals(k) || lower.startsWith(k + " ") || lower.endsWith(" " + k)
                    || lower.contains(" " + k + " ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWeakExcluded(String name) {
        String lower = name.toLowerCase().trim();

        if (lower.matches(".*\\d{4,}.*")) return true;

        for (String keyword : WEAK_EXCLUDED) {
            String k = keyword.toLowerCase();
            if (lower.equals(k) || lower.startsWith(k + " ") || lower.endsWith(" " + k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDateOrTime(String name) {
        String lower = name.toLowerCase();
        if (lower.matches(".*\\d{1,2}[/:]\\d{1,2}[/:]\\d{0,4}.*")) return true;
        if (lower.matches(".*\\d{1,2}:\\d{2}.*")) return true;
        if (lower.matches(".*\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}.*")) return true;
        return false;
    }

    private static boolean hasLetter(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) return true;
        }
        return false;
    }

    private static String cleanItemName(String name) {
        name = name.replaceAll("\\.{2,}", " ");
        name = name.replaceAll("-{2,}", " ");
        name = name.replaceAll("[^a-zA-Z0-9 &'.\\-()/,]", " ");
        name = name.replaceAll("\\s+", " ").trim();
        return name;
    }

    private static class ParsedItem {
        String name;
        double price;

        ParsedItem(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }
}
