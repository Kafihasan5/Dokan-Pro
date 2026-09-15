package com.example.util

import com.example.data.entity.*

object SampleData {

    fun getDefaultCategories(): List<Category> {
        return listOf(
            Category(id = 1, nameBn = "সব পণ্য", nameEn = "All", iconName = "grid_view", sortOrder = 0),
            Category(id = 2, nameBn = "চাল ও ডাল", nameEn = "Rice & Lentils", iconName = "rice_bowl", sortOrder = 1),
            Category(id = 3, nameBn = "তেল ও মশলা", nameEn = "Oil & Spices", iconName = "liquor", sortOrder = 2),
            Category(id = 4, nameBn = "চিনি ও চা", nameEn = "Sugar & Tea", iconName = "local_cafe", sortOrder = 3),
            Category(id = 5, nameBn = "কাঁচাবাজার", nameEn = "Vegetables", iconName = "eco", sortOrder = 4),
            Category(id = 6, nameBn = "শুকনো খাবার", nameEn = "Dry Foods", iconName = "bakery_dining", sortOrder = 5)
        )
    }

    fun getDefaultExpenseCategories(): List<ExpenseCategory> {
        return listOf(
            ExpenseCategory(id = 1, nameBn = "দোকান ভাড়া", nameEn = "Shop Rent", icon = "store"),
            ExpenseCategory(id = 2, nameBn = "বিদ্যুৎ বিল", nameEn = "Electricity", icon = "bolt"),
            ExpenseCategory(id = 3, nameBn = "কর্মচারী বেতন", nameEn = "Staff Salary", icon = "badge"),
            ExpenseCategory(id = 4, nameBn = "পরিবহন খরচ", nameEn = "Transport", icon = "local_shipping"),
            ExpenseCategory(id = 5, nameBn = "অন্যান্য খরচ", nameEn = "Miscellaneous", icon = "more_horiz")
        )
    }

    fun getSampleGroceryProducts(): List<Product> {
        return listOf(
            Product(
                nameBn = "মিনিকেট চাল (প্রিমিয়াম)",
                nameEn = "Miniket Rice",
                categoryId = 2,
                unitName = "কেজি",
                barcode = "8901001",
                purchasePricePoisha = 6800, // 68 Tk
                salePricePoisha = 7500, // 75 Tk
                stockQty = 120.0,
                minStock = 20.0
            ),
            Product(
                nameBn = "নাজিরশাইল চাল",
                nameEn = "Nazirshail Rice",
                categoryId = 2,
                unitName = "কেজি",
                barcode = "8901002",
                purchasePricePoisha = 7800, // 78 Tk
                salePricePoisha = 8500, // 85 Tk
                stockQty = 80.0,
                minStock = 15.0
            ),
            Product(
                nameBn = "দেশি মসুর ডাল",
                nameEn = "Deshi Red Lentils",
                categoryId = 2,
                unitName = "কেজি",
                barcode = "8901003",
                purchasePricePoisha = 12000, // 120 Tk
                salePricePoisha = 13500, // 135 Tk
                stockQty = 45.0,
                minStock = 10.0
            ),
            Product(
                nameBn = "মুগ ডাল (ভাজা)",
                nameEn = "Moong Dal",
                categoryId = 2,
                unitName = "কেজি",
                barcode = "8901004",
                purchasePricePoisha = 14500, // 145 Tk
                salePricePoisha = 16000, // 160 Tk
                stockQty = 25.0,
                minStock = 5.0
            ),
            Product(
                nameBn = "রূপচাঁদা সয়াবিন তেল ১ লিটার",
                nameEn = "Rupchanda Soybean Oil 1L",
                categoryId = 3,
                unitName = "বোতল",
                barcode = "8901005",
                purchasePricePoisha = 16800, // 168 Tk
                salePricePoisha = 17500, // 175 Tk
                stockQty = 30.0,
                minStock = 8.0
            ),
            Product(
                nameBn = "তীর সয়াবিন তেল ৫ লিটার",
                nameEn = "Teer Soybean Oil 5L",
                categoryId = 3,
                unitName = "বোতল",
                barcode = "8901006",
                purchasePricePoisha = 81000, // 810 Tk
                salePricePoisha = 84500, // 845 Tk
                stockQty = 12.0,
                minStock = 3.0
            ),
            Product(
                nameBn = "ফ্রেশ সাদা চিনি",
                nameEn = "Fresh White Sugar",
                categoryId = 4,
                unitName = "কেজি",
                barcode = "8901007",
                purchasePricePoisha = 12500, // 125 Tk
                salePricePoisha = 13500, // 135 Tk
                stockQty = 60.0,
                minStock = 15.0
            ),
            Product(
                nameBn = "মোল্লা সুপার আয়োডিন লবণ",
                nameEn = "Molla Salt 1kg",
                categoryId = 3,
                unitName = "প্যাকেট",
                barcode = "8901008",
                purchasePricePoisha = 3600, // 36 Tk
                salePricePoisha = 4200, // 42 Tk
                stockQty = 50.0,
                minStock = 10.0
            ),
            Product(
                nameBn = "দেশি পেঁয়াজ",
                nameEn = "Local Onion",
                categoryId = 5,
                unitName = "কেজি",
                barcode = "8901009",
                purchasePricePoisha = 7500, // 75 Tk
                salePricePoisha = 8500, // 85 Tk
                stockQty = 35.0,
                minStock = 10.0
            ),
            Product(
                nameBn = "বগুড়ার নতুন আলু",
                nameEn = "Potato",
                categoryId = 5,
                unitName = "কেজি",
                barcode = "8901010",
                purchasePricePoisha = 4200, // 42 Tk
                salePricePoisha = 5000, // 50 Tk
                stockQty = 70.0,
                minStock = 15.0
            ),
            Product(
                nameBn = "রসুন (আমদানি)",
                nameEn = "Garlic",
                categoryId = 5,
                unitName = "কেজি",
                barcode = "8901011",
                purchasePricePoisha = 18000, // 180 Tk
                salePricePoisha = 21000, // 210 Tk
                stockQty = 18.0,
                minStock = 5.0
            ),
            Product(
                nameBn = "রাধুনী হলুদ গুঁড়া ২০০ গ্রাম",
                nameEn = "Radhuni Turmeric 200g",
                categoryId = 3,
                unitName = "প্যাকেট",
                barcode = "8901012",
                purchasePricePoisha = 8000, // 80 Tk
                salePricePoisha = 9200, // 92 Tk
                stockQty = 20.0,
                minStock = 5.0
            ),
            Product(
                nameBn = "রাধুনী মরিচ গুঁড়া ২০০ গ্রাম",
                nameEn = "Radhuni Chilli 200g",
                categoryId = 3,
                unitName = "প্যাকেট",
                barcode = "8901013",
                purchasePricePoisha = 9500, // 95 Tk
                salePricePoisha = 11000, // 110 Tk
                stockQty = 18.0,
                minStock = 5.0
            ),
            Product(
                nameBn = "ইস্পাহানি মির্জাপুর চা ৪০০ গ্রাম",
                nameEn = "Ispahani Mirzapore Tea 400g",
                categoryId = 4,
                unitName = "প্যাকেট",
                barcode = "8901014",
                purchasePricePoisha = 21000, // 210 Tk
                salePricePoisha = 23000, // 230 Tk
                stockQty = 15.0,
                minStock = 4.0
            ),
            Product(
                nameBn = "ডানো ফুল ক্রিম গুঁড়ো দুধ ৫০০ গ্রাম",
                nameEn = "Dano Milk Powder 500g",
                categoryId = 4,
                unitName = "প্যাকেট",
                barcode = "8901015",
                purchasePricePoisha = 44000, // 440 Tk
                salePricePoisha = 47500, // 475 Tk
                stockQty = 10.0,
                minStock = 3.0
            ),
            Product(
                nameBn = "ফার্মের লাল ডিম (১ হালি)",
                nameEn = "Farm Eggs (4 pcs)",
                categoryId = 5,
                unitName = "হালি",
                barcode = "8901016",
                purchasePricePoisha = 4800, // 48 Tk
                salePricePoisha = 5400, // 54 Tk
                stockQty = 30.0,
                minStock = 6.0
            ),
            Product(
                nameBn = "তীর আটা ২ কেজি",
                nameEn = "Teer Atta 2kg",
                categoryId = 6,
                unitName = "প্যাকেট",
                barcode = "8901017",
                purchasePricePoisha = 11500, // 115 Tk
                salePricePoisha = 12600, // 126 Tk
                stockQty = 24.0,
                minStock = 6.0
            ),
            Product(
                nameBn = "তীর ময়দা ২ কেজি",
                nameEn = "Teer Maida 2kg",
                categoryId = 6,
                unitName = "প্যাকেট",
                barcode = "8901018",
                purchasePricePoisha = 13500, // 135 Tk
                salePricePoisha = 14800, // 148 Tk
                stockQty = 20.0,
                minStock = 5.0
            ),
            Product(
                nameBn = "অলিম্পিক এনার্জি প্লাস বিস্কুট",
                nameEn = "Olympic Energy Plus",
                categoryId = 6,
                unitName = "প্যাকেট",
                barcode = "8901019",
                purchasePricePoisha = 4200, // 42 Tk
                salePricePoisha = 5000, // 50 Tk
                stockQty = 36.0,
                minStock = 8.0
            ),
            Product(
                nameBn = "লাইফবয় টোটাল সাবান ১০০ গ্রাম",
                nameEn = "Lifebuoy Soap 100g",
                categoryId = 6,
                unitName = "পিস",
                barcode = "8901020",
                purchasePricePoisha = 4600, // 46 Tk
                salePricePoisha = 5500, // 55 Tk
                stockQty = 40.0,
                minStock = 10.0
            )
        )
    }

    fun getSampleCustomers(): List<Customer> {
        return listOf(
            Customer(name = "মোহাম্মদ রফিক", phone = "01712345678", address = "বাড়ি #৪, রোড #২, মেইন বাজার", creditLimitPoisha = 1000000),
            Customer(name = "আব্দুল করিম", phone = "01823456789", address = "উত্তর পাড়া, মসজিদ রোড", creditLimitPoisha = 500000),
            Customer(name = "শাহীন আক্তার", phone = "01934567890", address = "স্কুল মোড়", creditLimitPoisha = 800000)
        )
    }

    fun getSampleSuppliers(): List<Supplier> {
        return listOf(
            Supplier(name = "মেসার্স হক ট্রেডার্স", phone = "01700112233", company = "হক রাইস এজেন্সী", address = "বাদামতলী আড়ত"),
            Supplier(name = "মেঘনা ডিস্ট্রিবিউটরস", phone = "01800223344", company = "মেঘনা গ্রুপ ডিলার", address = "তেজগাঁও শিল্প এলাকা")
        )
    }
}
