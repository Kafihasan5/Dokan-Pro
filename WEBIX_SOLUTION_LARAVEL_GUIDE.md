# Webix Solution (Laravel) & Supabase License Integration Guide

এই গাইডে দেখানো হয়েছে কীভাবে আপনার **Webix Solution** (Laravel) ওয়েবসাইট থেকে কেউ Dokan-Pro অ্যাপ কিনলে স্বয়ংক্রিয়ভাবে তার লাইসেন্স তৈরি হবে।

---

## ১. Laravel `.env` কনফিগারেশন

আপনার Laravel প্রজেক্টের `.env` ফাইলে Supabase-এর তথ্য যোগ করুন:

```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key_here
```

> **সতর্কতা:** এখানে অবশ্যই **`service_role`** কী ব্যবহার করবেন (কখনোই `anon` কী নয়), কারণ এটি Laravel ব্যাকএন্ড থেকে সুরক্ষিতভাবে লাইসেন্স টেবিলে ডাটা ইনসার্ট করার অধিকার রাখে।

---

## ২. Laravel License Service তৈরি (`app/Services/SupabaseLicenseService.php`)

আপনার Laravel প্রজেক্টে একটি নতুন ফাইল তৈরি করুন:

```php
<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class SupabaseLicenseService
{
    protected string $url;
    protected string $serviceKey;

    public function __construct()
    {
        $this->url = rtrim(config('services.supabase.url', env('SUPABASE_URL')), '/');
        $this->serviceKey = config('services.supabase.service_key', env('SUPABASE_SERVICE_ROLE_KEY'));
    }

    /**
     * Create or update a customer license in Supabase upon successful payment
     *
     * @param string $email Customer's email
     * @param string|null $customerName
     * @param string|null $customerPhone
     * @param string|null $orderId
     * @param int $maxDevices Number of devices allowed (Default: 1)
     * @param string|null $expiresAt Optional ISO 8601 string or null for lifetime
     * @return bool
     */
    public function issueLicense(
        string $email,
        ?string $customerName = '',
        ?string $customerPhone = '',
        ?string $orderId = '',
        int $maxDevices = 1,
        ?string $expiresAt = null
    ): bool {
        $cleanEmail = strtolower(trim($email));

        $payload = [
            'email' => $cleanEmail,
            'customer_name' => $customerName ?? '',
            'customer_phone' => $customerPhone ?? '',
            'order_id' => $orderId ?? '',
            'status' => 'active',
            'max_devices' => $maxDevices,
            'expires_at' => $expiresAt,
        ];

        try {
            $response = Http::withHeaders([
                'apikey' => $this->serviceKey,
                'Authorization' => 'Bearer ' . $this->serviceKey,
                'Content-Type' => 'application/json',
                'Prefer' => 'resolution=merge-duplicates'
            ])->post("{$this->url}/rest/v1/app_licenses", $payload);

            if ($response->successful()) {
                Log::info("Dokan-Pro License created successfully for {$cleanEmail}");
                return true;
            }

            Log::error("Supabase license creation failed: " . $response->body());
            return false;
        } catch (\Exception $e) {
            Log::error("Supabase license network error: " . $e->getMessage());
            return false;
        }
    }
}
```

---

## ৩. Controller বা Payment Success Event-এ কল করা

যখন কোনো কাস্টমার আপনার সাইট থেকে পেমেন্ট সফলভাবে সম্পন্ন করবে:

```php
use App\Services\SupabaseLicenseService;

class OrderController extends Controller
{
    public function paymentSuccess(Request $request, SupabaseLicenseService $licenseService)
    {
        $order = Order::findOrFail($request->order_id);
        
        // ১. অর্ডার স্ট্যাটাস পেইড মার্ক করুন
        $order->status = 'paid';
        $order->save();

        // ২. Supabase-এ Dokan-Pro লাইসেন্স সক্রিয় করুন
        $licenseService->issueLicense(
            email: $order->customer_email,
            customerName: $order->customer_name,
            customerPhone: $order->customer_phone,
            orderId: $order->order_number,
            maxDevices: 1, // ক্রেতা ১টি ফোনে চালাতে পারবে
            expiresAt: null // null = লাইফটাইম লাইসেন্স
        );

        // ৩. ক্রেতাকে অভিনন্দন ও অ্যাপ অ্যাক্টিভেশন সংক্রান্ত ইমেইল/মেসেজ পাঠান
        return view('payment.success', compact('order'));
    }
}
```

---

## ৪. লাইসেন্স ম্যানেজমেন্ট (এডমিন ড্যাশবোর্ড থেকে)

ভবিষ্যতে যদি কোনো কাস্টমারের ডিভাইস রিসেট করতে চান (যেমন তার ফোন নষ্ট বা চুরি হয়ে নতুন ফোনে ট্রান্সফার করতে চায়):

```php
// একটি নির্দিষ্ট ইমেইলের রেজিস্টার্ড ডিভাইস খালি করে দেওয়া:
Http::withHeaders([
    'apikey' => env('SUPABASE_SERVICE_ROLE_KEY'),
    'Authorization' => 'Bearer ' . env('SUPABASE_SERVICE_ROLE_KEY'),
])->patch(env('SUPABASE_URL') . "/rest/v1/app_licenses?email=eq." . urlencode($email), [
    'device_ids' => []
]);
```
