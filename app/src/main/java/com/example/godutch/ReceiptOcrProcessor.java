package com.example.godutch;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class ReceiptOcrProcessor {

    private static final String TAG = "ReceiptOcrProcessor";

    public interface OcrCallback {
        void onSuccess(Text visionText);
        void onFailure(Exception e);
    }

    public void processBitmap(Bitmap bitmap, OcrCallback callback) {
        if (bitmap == null) {
            Log.e(TAG, "processBitmap called with null bitmap");
            callback.onFailure(new IllegalArgumentException("Bitmap is null"));
            return;
        }

        Log.d(TAG, "Processing bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight()
                + " config=" + bitmap.getConfig());

        Bitmap preprocessed = ImagePreprocessor.preprocess(bitmap);
        if (preprocessed == null) {
            preprocessed = bitmap;
        }

        InputImage image = InputImage.fromBitmap(preprocessed, 0);

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(visionText -> {
                    String fullText = visionText.getText();
                    Log.d(TAG, "=== OCR RAW TEXT START ===");
                    Log.d(TAG, fullText);
                    Log.d(TAG, "=== OCR RAW TEXT END ===");
                    Log.d(TAG, "OCR completed. Text length: " + fullText.length()
                            + ", blocks: " + visionText.getTextBlocks().size());

                    for (Text.TextBlock block : visionText.getTextBlocks()) {
                        Log.d(TAG, "Block: " + block.getText());
                        for (Text.Line line : block.getLines()) {
                            Log.d(TAG, "  Line: " + line.getText());
                            for (Text.Element element : line.getElements()) {
                                Log.d(TAG, "    Element: " + element.getText()
                                        + " bbox=" + element.getBoundingBox());
                            }
                        }
                    }

                    callback.onSuccess(visionText);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR failed: " + e.getMessage(), e);
                    callback.onFailure(e);
                });
    }
}
