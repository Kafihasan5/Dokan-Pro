# গুগল প্লেস্টোরে ডেকান প্রো (Dokan Pro) আপলোড নির্দেশিকা

এই ফোল্ডারে গুগল প্লেস্টোরের রুলস ও পলিসি অনুযায়ী স্বয়ংক্রিয়ভাবে জেনারেট হওয়া লেটেস্ট **Android App Bundle (.aab)** ফাইল সংরক্ষিত হয়।

---

## ১. ফাইল বিবরণী

* **ফাইল নেম:** `app-release.aab`
* **ফরম্যাট:** Android App Bundle (গুগল প্লেস্টোর শুধুমাত্র `.aab` ফাইল গ্রহণ করে)
* **টার্গেট এসডিকে (Target SDK):** `36` (গুগল প্লেস্টোরের বর্তমান রিকোয়ারমেন্ট পূরণ করে)
* **মিনিমাম এসডিকে (Min SDK):** `24` (Android 7.0+)
* **প্যাকেজ নেম (Application ID):** `com.webix.dokanpro`
* **সাইনিং কি (Signing Key):** `playstore-upload.jks` (Upload Key)

---

## ২. প্লেস্টোর ও গিটহাব ভার্সনের পার্থক্য

প্রতিবার প্রজেক্টে কোড পুশ বা আপডেট করার সাথে সাথে GitHub Actions স্বয়ংক্রিয়ভাবে দুটি ভার্সন তৈরি করে:

1. **গিটহাব ভার্সন (`app-debug.apk`):**
   * সরাসরি ফোনে ইনস্টল বা টেস্ট করার জন্য।
   * অ্যাপের ভেতরে অটো-আপডেট নোটিফিকেশন থেকে সরাসরি ডাউনলোড হয়ে আপডেট হওয়ার জন্য।
2. **প্লেস্টোর ভার্সন (`app-release.aab`):**
   * শুধুমাত্র গুগল প্লে কনসোলে আপলোড করার জন্য।
   * `playstore_release/app-release.aab` ফাইলটি বা GitHub Releases পেজ থেকে যেকোনো সময় ডাউনলোড করা যাবে।

---

## ৩. গুগল প্লে কনসোলে আপলোড করার সহজ ধাপসমূহ

### ধাপ ১: প্লে কনসোলে লগইন ও অ্যাপ তৈরি
1. [Google Play Console](https://play.google.com/console) এ আপনার ডেভেলপার একাউন্টে লগইন করুন।
2. **Create app** বাটনে ক্লিক করুন:
   * **App name:** `দোকান প্রো - Dokan Pro POS & হিসাব` (বা আপনার পছন্দের নাম)
   * **Default language:** `Bengali` অথবা `English`
   * **App or game:** `App`
   * **Free or paid:** `Free` (বা পেইড)
   * Declarations টিক দিয়ে **Create app** চাপুন।

### ধাপ ২: Play App Signing সক্রিয়করণ
1. বামপাশের মেনু থেকে **Release** > **Production** (অথবা **Internal testing**) এ যান।
2. **Create new release** এ ক্লিক করুন।
3. **Play App Signing** সেকশনে গুগল স্বয়ংক্রিয়ভাবে সিকিউর অ্যাপ সাইনিং ম্যানেজ করবে।

### ধাপ ৩: AAB ফাইল আপলোড
1. **App bundles** সেকশনে **Upload** বাটনে ক্লিক করুন।
2. আপনার কম্পিউটার থেকে এই ফোল্ডারের `app-release.aab` ফাইলটি নির্বাচন করুন (বা GitHub Releases থেকে ডাউনলোড করা `app-release.aab` দিন)।
3. আপলোড সম্পন্ন হলে রিলিজের নাম (Release name) যেমন `1.0.XX` এবং রিলিজ নোটস লিখে **Next** এ ক্লিক করুন।

### ধাপ ৪: প্রয়োজনীয় অ্যাপ ডিক্লারেশন পূরণ (Policy & App Content)
বামপাশের **Policy and programs** > **App content** এ গিয়ে নিচের বিষয়গুলো সম্পন্ন করুন:
* **Privacy Policy:** আপনার দোকানের প্রাইভেসি পলিসি লিঙ্ক দিন।
* **Ads:** "No, my app does not contain ads" সিলেক্ট করুন।
* **App Access:** "All functionality is available without special access" অথবা আপনার ডেমো ক্রেডেনশিয়াল দিন।
* **Content Ratings:** প্রশ্নাবলীর উত্তর দিয়ে রেটিং কমপ্লিট করুন (Retail/Shop Utilities)।
* **Target Audience:** ১৮ বা তার বেশি বয়সের ব্যবসায়ীদের জন্য।
* **Sensitive Permissions (Camera):** বারকোড ও কিউআর স্ক্যান করার জন্য ক্যামেরা পারমিশন উল্লেখ করুন।

### ধাপ ৫: রিভিউ ও পাবলিশ
1. **Review release** এ ক্লিক করুন।
2. সবকিছু ঠিক থাকলে **Start rollout to Production** এ ক্লিক করুন।
3. গুগল টিম অ্যাপটি রিভিউ করার পর প্লেস্টোরে লাইভ হয়ে যাবে।

---

## ৪. পরবর্তী প্রতিটি আপডেটের ক্ষেত্রে করণীয়

প্রতিবার যখন আপনি নতুন কোনো আপডেট রিলিজ করবেন:
1. GitHub Actions স্বয়ংক্রিয়ভাবে নতুন ভার্সন কোড (`versionCode`) দিয়ে একটি নতুন `app-release.aab` বিল্ড করে দেবে।
2. আপনি শুধু [GitHub Releases](https://github.com/Kafihasan5/Dokan-Pro/releases) পেজ থেকে অথবা এই ফোল্ডার থেকে লেটেস্ট `app-release.aab` ফাইলটি নামিয়ে প্লে কনসোলের **Production** > **Create new release** এ গিয়ে আপলোড করে দিবেন।
