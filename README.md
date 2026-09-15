<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Dokan Pro - অফলাইন মোবাইল POS ও দোকান ব্যবস্থাপনা অ্যাপ

**Dokan Pro** হলো মুদি ও যেকোনো রিটেইল ব্যবসার জন্য সম্পূর্ণ অফলাইন মোবাইল পয়েন্ট অফ সেল (POS) এবং হিসাব খাতা ব্যবস্থাপনা অ্যাপ্লিকেশন।

- **অফিসিয়াল ওয়েবসাইট:** [Webix Solution](https://webixsolution.com)
- **লাইসেন্স ও অ্যাক্টিভেশন:** Webix Solution Laravel & Supabase Licensing
- **ডাটা প্রাইভেসি:** ১০০% লোকাল ও সুরক্ষিত অফলাইন SQLite (Room Database)


## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
