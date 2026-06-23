# Google ML Kit Text Recognition v2 — Algorithm Deep Dive

> How on-device OCR works under the hood in the GoDutch bill-splitting app.

---

## 1. Context: OCR in GoDutch

GoDutch is an Android app that splits restaurant bills by scanning printed receipts. The pipeline is:

```
Camera Capture → Bitmap → ML Kit OCR → Raw Text → ReceiptParser → Structured FoodItems → UI
```

The OCR engine is **Google ML Kit Text Recognition v2** — specifically the Latin-script model via `TextRecognizerOptions.DEFAULT_OPTIONS`. The recognized text is then parsed with regex to extract item names and prices.

---

## 2. What Is Google ML Kit?

ML Kit is Google's on-device machine learning SDK for Android and iOS. It wraps multiple Google ML technologies (Mobile Vision, TensorFlow Lite, custom models) into a single API.

**Key properties of on-device text recognition:**
- **No network call** — all inference happens locally on the device
- **Privacy-preserving** — image data never leaves the device
- **Free** — no cloud API costs
- **Offline-capable** — works without internet
- **Powered by TensorFlow Lite** — optimized for mobile inference

---

## 3. Text Recognition v2 — Architecture Overview

ML Kit Text Recognition v2 is a major evolution over v1. The headline feature is **multi-script support** — five independent script-specific models, each built as a separate TensorFlow Lite model:

| Script | Library (Unbundled) | Library (Bundled) |
|---|---|---|
| Latin | `play-services-mlkit-text-recognition` | `mlkit:text-recognition` |
| Chinese | `play-services-mlkit-text-recognition-chinese` | `mlkit:text-recognition-chinese` |
| Devanagari | `play-services-mlkit-text-recognition-devanagari` | `mlkit:text-recognition-devanagari` |
| Japanese | `play-services-mlkit-text-recognition-japanese` | `mlkit:text-recognition-japanese` |
| Korean | `play-services-mlkit-text-recognition-korean` | `mlkit:text-recognition-korean` |

**Deployment options:**
- **Unbundled** (via Google Play Services): ~260 KB per script, model downloaded dynamically (~4 MB download). Recommended for most apps.
- **Bundled** (statically linked): ~4 MB per script per architecture. Use when Google Play Services is not available.

Each model internally supports Latin characters **plus** its respective script (e.g., the Korean model recognizes both Hangul and Latin).

---

## 4. The 4-Stage Recognition Pipeline

The recognition lifecycle consists of **four sequential stages**, all chained inside a single call to `recognizer.process(image)`:

```
Input Image
    │
    ▼
┌─────────────────────┐
│  1. Text Detection   │  ← CNN-based region proposal
└─────────┬───────────┘
          ▼
┌─────────────────────────┐
│  2. Script Identification│  ← Classifies each region's script
└─────────┬───────────────┘
          ▼
┌─────────────────────┐
│  3. Text Recognition │  ← CRNN (CNN + Bi-LSTM + CTC)
└─────────┬───────────┘
          ▼
┌──────────────────────────┐
│  4. Language Identification│  ← Maps text to BCP-47 language code
└─────────┬────────────────┘
          ▼
    Text Result (hierarchical)
```

### Stage 1: Text Detection

**Goal:** Locate all regions in the image that contain text.

ML Kit uses a **fully convolutional neural network** (similar to the EAST: Efficient and Accurate Scene Text Detector architecture) that:

1. Accepts the full input image at a fixed resolution (typically 320×320 internally)
2. Performs a single forward pass through a CNN backbone
3. Outputs a dense prediction map with:
   - **Pixel-level text/non-text classification** — a score map indicating text presence at each location
   - **Geometry information** — rotated bounding box coordinates (offset, rotation angle) for each positive pixel
4. Applies **Non-Maximum Suppression (NMS)** to merge overlapping detections and produce the final set of text region bounding boxes

**Output:** A list of rotated bounding boxes, each representing a detected text region (e.g., a paragraph or column of text).

**Key characteristics:**
- Rotation-invariant — can detect text at any orientation (all-orientation recognition is a v2 feature)
- Scale-robust — handles text at various sizes through the CNN's multi-scale feature maps
- Each bounding box includes: 4 corner points, rotation degree, and a confidence score

### Stage 2: Script Identification

**Goal:** Determine which script each detected text region belongs to.

A lightweight classifier analyzes each cropped text region and assigns it to one of the supported scripts (Latin, Chinese, Devanagari, Japanese, Korean).

This step is critical for the multi-script pipeline because it determines **which recognition model** to route each text region to. In a single-script app like GoDutch (using `DEFAULT_OPTIONS`), this stage simply confirms that the detected text is Latin-script before moving to recognition.

### Stage 3: Text Recognition (CRNN)

**Goal:** Convert the image pixels inside each text region into a character sequence.

This is the core of the OCR engine. ML Kit uses a **CRNN (Convolutional Recurrent Neural Network)** architecture — the de-facto standard for scene text recognition. The CRNN has three components:

#### 3a. CNN Backbone (Feature Extraction)

```
Input: Cropped text region (normalized to fixed height, e.g., 32px)
       │
       ▼
┌─────────────────────────────────────────────┐
│  Convolutional Layers (VGG-style / ResNet)   │
│  • Conv + BatchNorm + ReLU + MaxPooling      │
│  • Extracts hierarchical visual features     │
│  • Output: feature map of size H' × W' × C   │
└─────────────────────────────────────────────┘
       │
       ▼
Output: Sequence of feature vectors (one per time step)
```

The CNN processes the text region image through multiple convolutional layers. Because text is inherently sequential (read left-to-right or right-to-left), the CNN is designed to **preserve the horizontal dimension** of the feature map. The final feature map is treated as a **sequence of vertical slices**:

- The network uses wide strides in the vertical direction (collapsing height) but keeps the horizontal axis intact
- Each column of the feature map corresponds to a receptive field in the input image — essentially a narrow vertical strip of the original text
- The output is a sequence of feature vectors: `x₁, x₂, ..., x_T` where `T` is the number of time steps (proportional to the width of the text region)

```
"HELLO" in image:
┌─────────────────────────────┐
│ H │ E │ L │ L │ O │         │  ← CNN feature columns
└─────────────────────────────┘
  x₁  x₂  x₃  x₄  x₅  x₆     ← sequence of feature vectors
```

#### 3b. Bi-directional LSTM (Sequence Modeling)

```
Input: Sequence of feature vectors x₁, x₂, ..., x_T
       │
       ▼
┌─────────────────────────────────────────────┐
│  Bidirectional LSTM Layers (2 layers)        │
│                                              │
│  Forward LSTM:  → → → → → → → → → → → →    │
│  Backward LSTM: ← ← ← ← ← ← ← ← ← ← ← ←    │
│                                              │
│  Each time step t concatenates:              │
│  h_t = [h_t_forward; h_t_backward]          │
└─────────────────────────────────────────────┘
       │
       ▼
Output: Sequence of context-rich vectors h₁, h₂, ..., h_T
```

The feature sequence is fed into a **multi-layer Bidirectional LSTM (Long Short-Term Memory)** network:

- **Why LSTM?** LSTMs solve the vanishing/exploding gradient problem of vanilla RNNs using gating mechanisms (input gate, forget gate, output gate, cell state). This allows them to capture long-range dependencies in sequence data.
- **Why bidirectional?** Text context flows both ways. Knowing characters after the current position helps just as much as characters before it. The forward LSTM reads left-to-right, the backward LSTM reads right-to-left.
- **Why 2 layers?** The original CRNN paper (Shi et al., 2015) found 2 hidden layers optimal — 128 cells in the first layer, 32 in the second. Each layer is itself bidirectional, producing 256 and 64 outputs per time step.

Each time step's output combines information from both directions, giving a rich contextual representation of every vertical slice of the text image.

#### 3c. CTC Decoder (Alignment-Free Transcription)

```
Input: Per-timestep probability distributions
       │
       ▼
┌─────────────────────────────────────────────┐
│  Softmax Output Layer                        │
│  • |Σ| + 1 output units                      │
│  • Σ = character set (a-z, 0-9, etc.)        │
│  • +1 = CTC blank token ("-")                │
│                                               │
│  At each timestep t:                          │
│  P(character | x_t) for all characters        │
└─────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│  CTC Decoding (Greedy or Beam Search)        │
│  • Collapse repeated characters              │
│  • Remove blank tokens                       │
│                                               │
│  Example: "HHHE-L-LL-OOO-" → "HELLO"        │
└─────────────────────────────────────────────┘
```

**The alignment problem:** The CNN produces `T` feature columns, but the text may only be 5 characters long (`"HELLO"`). Which columns correspond to which characters? There's no pre-segmented alignment. This is where **CTC (Connectionist Temporal Classification)** solves the problem.

**How CTC works:**

1. For each time step `t`, the softmax layer outputs a probability distribution over all characters in the alphabet plus a special **blank token** (ε). The blank token represents "no character" — it's the model's way of saying it's between characters.

2. The output is a matrix of size `T × (|Σ| + 1)`, where `T` is the number of time steps and `|Σ|` is the character set size.

3. **Decoding** — two approaches:
   - **Greedy decoding** (used in ML Kit for speed): At each time step, pick the character with the highest probability. Then collapse repeated non-blank characters and remove blanks.
     - Example: `HH ε ε H HE ε L ε LL ε ε O` → collapse repeats → `H H E L L O` → remove blanks → `HELLO`
   - **Beam search decoding** (more accurate, slower): Maintains multiple candidate transcriptions and their probabilities, selecting the most likely overall sequence.

4. **Training** — CTC defines a **loss function** that sums over all possible alignments between the input sequence and the target text. This allows the network to be trained end-to-end without needing character-level segmentation labels. The forward-backward algorithm (similar to HMMs) efficiently computes this loss.

**Why CTC is powerful:**
- No need for pre-segmented training data (just images + their text labels)
- Can handle varying-length outputs
- Jointly learns character appearance and language context

### Stage 4: Language Identification

**Goal:** Assign a BCP-47 language code to each recognized text region.

After the text is transcribed, a final language identification module maps the recognized string to a language code (e.g., `"en"` for English, `"de"` for German, `"fr"` for French). This uses a separate lightweight classifier or lookup based on character n-gram statistics.

ML Kit supports **~80 languages** at various support levels:
- **Supported** (regularly evaluated): English, German, French, Spanish, Italian, Dutch, etc.
- **Experimental** (active development): additional languages
- **Mapped** (falls back to general character recognizer): e.g., `en-GB` → `en`

Latin script is shared across many languages — a text block reading "Menu" could be English, German, or Dutch. Language identification disambiguates this.

---

## 5. Output Data Hierarchy

The recognized text is returned as a **4-level hierarchical structure**:

```
Text (root)
  ├── .text                 → "Total: $45.67\nItem 1: $12.99\n..."
  │
  └── TextBlock[]           (e.g., one block = the receipt)
        ├── .text           → "Total: $45.67"
        ├── .boundingBox    → Rect(10, 20, 200, 40)
        ├── .cornerPoints   → [4 corner points]
        ├── .confidence     → 0.95
        ├── .recognizedLanguage → "en"
        ├── .rotationDegrees → 0
        │
        └── Line[]          (e.g., one line = "Total: $45.67")
              ├── .text     → "Total:"
              ├── .boundingBox
              ├── .confidence → 0.97
              │
              └── Element[]  (e.g., "Total:")
                    ├── .text     → "Total:"
                    ├── .boundingBox
                    ├── .confidence → 0.98
                    │
                    └── Symbol[]  (e.g., "T", "o", "t", "a", "l", ":")
                          ├── .text     → "T"
                          ├── .boundingBox
                          └── .confidence → 0.99
```

| Level | Meaning | Example |
|---|---|---|
| **Block** | Paragraph or column | Entire receipt text block |
| **Line** | Single line of text | `"2x Burger ........ $12.99"` |
| **Element** | Word or word-like entity | `"2x"`, `"Burger"`, `"$12.99"` |
| **Symbol** | Individual character | `"B"`, `"u"`, `"r"`, `"g"`, `"e"`, `"r"` |

Each level exposes:
- `text` — the recognized string
- `boundingBox` — axis-aligned rectangle (android.graphics.Rect)
- `cornerPoints` — array of 4 corner points (for rotated text)
- `confidence` — score from 0.0 to 1.0
- `recognizedLanguage` — BCP-47 language code (v2 feature)

---

## 6. How GoDutch Uses ML Kit

### 6a. The Camera Flow

In `SecondScreen.java`, capturing a receipt photo:

```java
public void captureImage() {
    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    File photoFile = createImageFile();
    Uri photoUri = FileProvider.getUriForFile(this,
        "com.example.apitest.fileprovider", photoFile);
    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
    startActivityForResult.launch(intent);
}
```

The result handler decodes the photo and passes it to OCR:

```java
ActivityResultLauncher<Intent> startActivityForResult =
    registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK) {
                Bitmap imageBitmap = BitmapFactory.decodeFile(currentPhotoPath);
                processReceiptImage(imageBitmap);  // kicks off OCR
            }
        }
    );
```

### 6b. OCR Execution (ReceiptOcrProcessor.java)

```java
public void processBitmap(Bitmap bitmap, OcrCallback callback) {
    InputImage image = InputImage.fromBitmap(bitmap, 0);

    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        .process(image)
        .addOnSuccessListener(visionText -> {
            callback.onSuccess(visionText.getText());
        })
        .addOnFailureListener(e -> {
            callback.onFailure(e);
        });
}
```

**Step-by-step what happens inside `process()`:**

1. `InputImage.fromBitmap(bitmap, 0)` — wraps the Android Bitmap into ML Kit's universal image container, specifying 0 degrees of rotation
2. `TextRecognition.getClient(DEFAULT_OPTIONS)` — creates (or retrieves) a `TextRecognizer` instance with the Latin-script TF Lite model
3. `.process(image)` — queues the image on ML Kit's internal executor thread:
   - If using unbundled model: checks if the TF Lite model is already downloaded via Google Play Services; if not, triggers a download
   - ML Kit internally creates an `MlImage` from the `InputImage`
   - The model is loaded into the TFLite runtime (with hardware acceleration if available — GPU via GPU Delegates, or NNAPI on supported devices)
   - The 4-stage pipeline executes (Detection → Script ID → Recognition → Language ID)
   - A `Text` object is assembled with the full hierarchical result
4. `addOnSuccessListener` — callback fires on the main thread with the result

### 6c. Parsing the Raw OCR Output (ReceiptParser.java)

The raw text from `visionText.getText()` looks like:

```
ROCCO'S PIZZA
123 Main St, City

2x Margherita     $24.00
1x Pepperoni      $12.99
1x Caesar Salad   $8.99
Subtotal          $45.98
Tax (8%)          $3.68
Total             $49.66
```

`ReceiptParser` applies:
1. **Line splitting** — splits by newline (`\n`)
2. **Price regex** — `\$?\s*(\d+\.\d{2})\s*$` matches lines ending with an amount like `$12.99` or `12.99`
3. **Filtering** — excludes lines containing keywords (Total, Subtotal, Tax, Tip, Cash, Card, Date, etc.)
4. **Name extraction** — everything before the price becomes the item name
5. **Validation** — price must be between $0.01 and $9,999.99; name must contain at least one letter

Result: `ArrayList<FoodItem>` with `{name: "2x Margherita", price: 24.00}` and `{name: "1x Pepperoni", price: 12.99}`.

---

## 7. Performance & Optimization

### 7a. Hardware Acceleration

ML Kit automatically leverages device hardware for neural network inference:

| Hardware | Mechanism | Benefit |
|---|---|---|
| **GPU** | TensorFlow Lite GPU Delegate | 2-4× faster than CPU for CNN layers |
| **NNAPI** | Android Neural Networks API | Hardware-specific acceleration on DSP/NPU |
| **CPU** | TFLite XNNPACK Delegate | Optimized floating-point kernels |

The framework selects the best available delegate automatically. The developer does not need to configure this.

### 7b. Input Image Guidelines

For best accuracy, Google recommends:
- **Minimum character size**: 16×16 pixels per character
- **Optimal character size**: 24×24 pixels — larger provides no accuracy gains
- **Resolution**: Lower resolutions process faster. Consider downscaling large images
- **Text should occupy as much of the frame as possible**

### 7c. Memory Management

- Always call `recognizer.close()` when done to release native model resources
- The model is loaded once and cached — creating multiple `TextRecognizer` instances loads multiple copies
- For live camera analysis, use `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to prevent frame queuing

### 7d. Real-Time Performance

For Latin script:
- **Unbundled** (Play Services): real-time on most modern devices (30+ FPS)
- **Bundled**: near real-time, slightly slower due to model loading overhead

Other scripts (Chinese, Japanese, Korean, Devanagari) are slower due to larger character sets and more complex models.

---

## 8. Why ML Kit v2 for GoDutch

| Requirement | How ML Kit Satisfies It |
|---|---|
| **Free** | No API costs — all on-device |
| **Offline** | Works on airplane mode |
| **Privacy** | Receipt images never leave the device |
| **Receipt text** | Latin model handles printed restaurant receipts well |
| **Easy integration** | Single dependency, ~10 lines of code |
| **Fast** | Real-time on most devices |
| **Structured output** | Hierarchical Text → Block → Line → Element for fine-grained parsing |

The migration from Mindee API (cloud-based) to ML Kit (on-device) was motivated by:
- Eliminating network dependency
- Removing API costs
- Faster text recognition (no network round-trip)
- Simpler codebase (no Retrofit/OkHttp/Gson for OCR)

---

## 9. Summary

Google ML Kit Text Recognition v2 is a **production-grade, on-device OCR engine** built on TensorFlow Lite. Its algorithm pipeline:

1. **Text Detection** — CNN locates text regions in the image (EAST-style)
2. **Script Identification** — classifies each region's script type
3. **Text Recognition** — CRNN (CNN + Bi-LSTM + CTC) transcribes image pixels to character sequences:
   - CNN extracts visual features as a sequence of column vectors
   - Bi-directional LSTM models the sequential context
   - CTC decoder produces the final transcription without requiring character-level alignment
4. **Language Identification** — assigns a BCP-47 language code to each text region

In GoDutch, this pipeline processes receipt photos in under a second, returning raw text that is then parsed into structured food items for bill splitting.

---

## Appendix A: Visual Aids for Presentation

For your presentation slides, the following diagrams would be helpful:

### A1. Pipeline Flow Diagram

```
[Camera Photo] → [Bitmap] → [InputImage] → [TextRecognizer.process()]
                                                    │
                         ┌──────────────────────────┼──────────────────────────┐
                         │          ┌────────────────▼────────────────┐        │
                         │          │   1. Text Detection (CNN)       │        │
                         │          └────────────────┬────────────────┘        │
                         │          ┌────────────────▼────────────────┐        │
                         │          │   2. Script Identification     │        │
                         │          └────────────────┬────────────────┘        │
                         │          ┌────────────────▼────────────────┐        │
                         │          │   3. Text Recognition (CRNN)    │        │
                         │          │   ┌──────────────────────┐      │        │
                         │          │   │ CNN Feature Extractor │      │        │
                         │          │   └──────────┬───────────┘      │        │
                         │          │   ┌──────────▼───────────┐      │        │
                         │          │   │ Bi-Directional LSTM  │      │        │
                         │          │   └──────────┬───────────┘      │        │
                         │          │   ┌──────────▼───────────┐      │        │
                         │          │   │    CTC Decoder       │      │        │
                         │          │   └──────────────────────┘      │        │
                         │          └────────────────┬────────────────┘        │
                         │          ┌────────────────▼────────────────┐        │
                         │          │   4. Language Identification   │        │
                         │          └────────────────┬────────────────┘        │
                         └──────────────────────────┼──────────────────────────┘
                                                    ▼
                                           [Text Object]
                                                    │
                                           [visionText.getText()]
                                                    │
                                           [ReceiptParser.parse()]
                                                    │
                                           [ArrayList<FoodItem>]
```

### A2. CRNN Architecture Detail

```
Input: 32 × W × 1 (grayscale text region)
    │
    ▼
┌──────────────────────────────────────┐
│  CNN Feature Extractor               │
│                                      │
│  Conv(3×3, 64) + BN + ReLU          │
│  MaxPool(2×2)                        │  → 16 × W/2 × 64
│  Conv(3×3, 128) + BN + ReLU         │
│  MaxPool(2×2)                        │  → 8 × W/4 × 128
│  Conv(3×3, 256) + BN + ReLU         │
│  Conv(3×3, 256) + BN + ReLU         │
│  MaxPool(2×1)                        │  → 4 × W/4 × 256  ← preserves width
│  Conv(3×3, 512) + BN + ReLU         │
│  BatchNorm + ReLU                    │
│  MaxPool(2×1)                        │  → 2 × W/4 × 512
│  Conv(3×3, 512) + BN + ReLU         │
│  MaxPool(2×1)                        │  → 1 × W/4 × 512
│                                      │
│  Feature sequence: x₁, x₂, ..., x_T  │  T = W/4 time steps
└──────────────┬───────────────────────┘
               ▼
┌──────────────────────────────────────┐
│  Bi-directional LSTM                 │
│                                      │
│  Layer 1: Bi-LSTM (128 cells each)   │
│    → h₁₁, h₁₂, ..., h₁_T            │
│                                      │
│  Layer 2: Bi-LSTM (32 cells each)    │
│    → h₂₁, h₂₂, ..., h₂_T            │
└──────────────┬───────────────────────┘
               ▼
┌──────────────────────────────────────┐
│  Softmax (|Σ| + 1 classes)           │
│  │   │   │   │   │   │   │   │      │
│  y₁  y₂  y₃  y₄  y₅  y₆  y₇  y₈     │
└──────────────┬───────────────────────┘
               ▼
┌──────────────────────────────────────┐
│  CTC Decoder                          │
│                                      │
│  Greedy path:                        │
│  y₁  y₂  y₃  y₄  y₅  y₆             │
│  H   ε   E   L   L   O               │
│                                      │
│  Collapse: H E L L O                 │
│  Remove blanks: HELLO                │
└──────────────────────────────────────┘
```

### A3. Text Hierarchy Tree

```
                        ┌─────────────────────────┐
                        │    Text (root)            │
                        │    "Total: $45.67\n..."   │
                        └────────────┬────────────┘
                                     │
                     ┌───────────────┼───────────────┐
                     │               │               │
              ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
              │  Block #0    │ │  Block #1    │ │  Block #2    │
              │ "RECEIPT"    │ │ "Item 1..."  │ │ "Total..."   │
              └──────┬──────┘ └──────────────┘ └──────────────┘
                     │
              ┌──────▼──────┐
              │  Line #0     │
              │ "Item 1..."  │
              └──────┬──────┘
                     │
              ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
              │ Element #0   │ │ Element #1   │ │ Element #2   │
              │ "1x Burger"  │ │ "$12.99"     │ │              │
              └──────┬──────┘ └──────────────┘ └──────────────┘
                     │
         ┌───────────┼───────────┐
         │           │           │
   ┌─────▼────┐ ┌────▼───┐ ┌────▼───┐
   │Symbol "B" │ │Symbol"u"│ │...     │
   └──────────┘ └────────┘ └────────┘
```

### A4. CTC Alignment Example

```
Input image: [H][E][L][L][O][ ]

Time steps:  1   2   3   4   5   6   7   8   9  10  11  12

        H:   0.9 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1
        E:   0.1 0.8 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1
        L:   0.1 0.1 0.1 0.7 0.7 0.1 0.5 0.8 0.1 0.1 0.1 0.1
        O:   0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.1 0.8 0.8 0.1
    blank:   0.1 0.1 0.7 0.1 0.1 0.8 0.3 0.1 0.8 0.1 0.1 0.8

Greedy:     H   ε   ε   L   L   ε   L   L   ε   O   O   ε
                  ↓ collapse repeats                  ↓ remove blanks
Result:                 H   E   L   L   O
```

---

## References

1. Shi, B., Bai, X., & Yao, C. (2015). "An End-to-End Trainable Neural Network for Image-based Sequence Recognition and Its Application to Scene Text Recognition." *arXiv:1507.05717*.
2. Graves, A., Fernandez, S., Gomez, F., & Schmidhuber, J. (2006). "Connectionist Temporal Classification: Labelling Unsegmented Sequence Data with Recurrent Neural Networks." *ICML 2006*.
3. Zhou, X., et al. (2017). "EAST: An Efficient and Accurate Scene Text Detector." *CVPR 2017*.
4. Google ML Kit Documentation — *Text Recognition v2*. https://developers.google.com/ml-kit/vision/text-recognition/v2
5. Google I/O 2021 — *New features in ML Kit: Text Recognition V2 & Pose Detection*. https://www.youtube.com/watch?v=9EKQ0UC04S8
