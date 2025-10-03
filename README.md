# Circle2Capture

Circle2Capture is an Android app that lets you circle a region in an image and get a text description from an on‑device **Gemma‑3n E2B** model via MediaPipe `tasks-genai`.

---

## 📥 Steps to install Gemma‑3n `.task` file into your phone

1. **Download the model**
   Visit the official Hugging Face release and download the `.task` file:
   👉 [https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/tree/main](https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/tree/main)

   Example downloaded filename:

   ```
   gemma-3n-E2B-it-int4.task
   ```

2. **Install ADB on your PC (Ubuntu/Debian)**

   ```bash
   sudo apt update
   sudo apt install adb
   ```

3. **Connect your device**

   * Enable **USB debugging** in Developer Options on your Android phone (or use an emulator).
   * Connect the phone via USB.
   * Verify the connection:

     ```bash
     adb devices
     ```

4. **Create the target directory on the device**

   ```bash
   adb shell mkdir -p /data/local/tmp/llm/
   ```

5. **Copy the model from PC → device**
   Replace `<LOCAL_PATH>` with the directory where you saved the `.task` file:

   ```bash
   adb push <LOCAL_PATH>/gemma-3n-E2B-it-int4.task /data/local/tmp/llm/
   ```

   **Example:**

   ```bash
   adb push ~/Downloads/gemma-3n-E2B-it-int4.task /data/local/tmp/llm/
   ```

6. **Verify the file on the device**

   ```bash
   adb shell ls -lh /data/local/tmp/llm/
   ```

   You should see your file listed (size ≈ 3 GB).

7. **Point the app to the model path**
   In the app code, ensure the model path matches the pushed file:

   ```kotlin
   val modelPath = "/data/local/tmp/llm/gemma-3n-E2B-it-int4.task"
   ```

> ℹ️ **Note:** `/data/local/tmp` is a temporary developer directory. If you reboot the device/emulator, you may need to run the `adb push` step again.

---

## ⚠️ First‑run behaviour

"first time when you input the image it may show "no ai description available", just startover and input image again"

---

## Requirements (quick)

* Android 10+ device with sufficient RAM (≥ 6 GB recommended for Gemma‑3n E2B).
* Android Studio to build and run the app.
* Internet connection once to download the `.task` file.

---

## Usage (quick)

1. Run the app on your device.
2. Pick an image from the gallery.
3. Draw a circle around the region you want described.
4. Tap **Crop** — the local Gemma model generates the description.
