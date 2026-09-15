package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.PaponDao
import com.example.data.entity.*

@Database(
    entities = [
        Product::class,
        Category::class,
        Sale::class,
        SaleItem::class,
        Customer::class,
        CustomerLedger::class,
        Supplier::class,
        Purchase::class,
        PurchaseItem::class,
        SupplierLedger::class,
        ExpenseCategory::class,
        Expense::class,
        StockAdjustment::class,
        BackupLog::class,
        DeletedRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PaponDatabase : RoomDatabase() {
    abstract fun paponDao(): PaponDao

    companion object {
        @Volatile
        private var INSTANCE: PaponDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN localImagePath TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): PaponDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaponDatabase::class.java,
                    "papon_shop_database.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
