-- ========================================================================
-- DOKAN PRO - CUSTOMER SUPABASE DATABASE SCHEMA
-- ========================================================================
-- Run this SQL in your personal Supabase SQL Editor (https://supabase.com/dashboard)
-- This creates all necessary tables for cloud backup, restore, and multi-device sync.
-- ========================================================================

-- 1. Categories
CREATE TABLE IF NOT EXISTS public.categories (
    id BIGINT PRIMARY KEY,
    name_bn TEXT NOT NULL,
    name_en TEXT DEFAULT '',
    icon_name TEXT DEFAULT 'category',
    sort_order INT DEFAULT 0
);

-- 2. Products
CREATE TABLE IF NOT EXISTS public.products (
    id BIGINT PRIMARY KEY,
    name_bn TEXT NOT NULL,
    name_en TEXT DEFAULT '',
    category_id BIGINT REFERENCES public.categories(id) ON DELETE SET NULL,
    unit_name TEXT DEFAULT 'পিস',
    barcode TEXT DEFAULT '',
    purchase_price_poisha BIGINT DEFAULT 0,
    sale_price_poisha BIGINT DEFAULT 0,
    wholesale_price_poisha BIGINT DEFAULT 0,
    stock_qty NUMERIC DEFAULT 0,
    min_stock NUMERIC DEFAULT 5,
    expiry_date BIGINT,
    supplier_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT
);

-- 3. Customers
CREATE TABLE IF NOT EXISTS public.customers (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    address TEXT,
    credit_limit_poisha BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at BIGINT NOT NULL
);

-- 4. Sales
CREATE TABLE IF NOT EXISTS public.sales (
    id BIGINT PRIMARY KEY,
    invoice_no TEXT NOT NULL,
    customer_id BIGINT REFERENCES public.customers(id) ON DELETE SET NULL,
    customer_name TEXT,
    sale_date BIGINT NOT NULL,
    subtotal_poisha BIGINT NOT NULL,
    discount_poisha BIGINT DEFAULT 0,
    vat_poisha BIGINT DEFAULT 0,
    total_poisha BIGINT NOT NULL,
    paid_amount_poisha BIGINT NOT NULL,
    due_amount_poisha BIGINT DEFAULT 0,
    payment_method TEXT DEFAULT 'cash',
    user_id TEXT DEFAULT 'owner',
    note TEXT,
    is_returned BOOLEAN DEFAULT FALSE,
    created_at BIGINT NOT NULL
);

-- 5. Sale Items
CREATE TABLE IF NOT EXISTS public.sale_items (
    id BIGINT PRIMARY KEY,
    sale_id BIGINT REFERENCES public.sales(id) ON DELETE CASCADE,
    product_id BIGINT,
    product_name TEXT NOT NULL,
    unit_name TEXT DEFAULT 'পিস',
    qty NUMERIC NOT NULL,
    unit_price_poisha BIGINT NOT NULL,
    purchase_price_at_sale_poisha BIGINT DEFAULT 0,
    discount_poisha BIGINT DEFAULT 0,
    line_total_poisha BIGINT NOT NULL
);

-- 6. Customer Ledger (Due & Payment Tracking)
CREATE TABLE IF NOT EXISTS public.customer_ledger (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT REFERENCES public.customers(id) ON DELETE CASCADE,
    ref_type TEXT NOT NULL,
    ref_id BIGINT,
    debit_poisha BIGINT DEFAULT 0,
    credit_poisha BIGINT DEFAULT 0,
    note TEXT,
    entry_date BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

-- 7. Expenses
CREATE TABLE IF NOT EXISTS public.expenses (
    id BIGINT PRIMARY KEY,
    category_id BIGINT DEFAULT 1,
    category_name TEXT NOT NULL,
    amount_poisha BIGINT NOT NULL,
    note TEXT,
    expense_date BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

-- 8. Suppliers
CREATE TABLE IF NOT EXISTS public.suppliers (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    company TEXT,
    address TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at BIGINT NOT NULL
);

-- 9. Purchases
CREATE TABLE IF NOT EXISTS public.purchases (
    id BIGINT PRIMARY KEY,
    invoice_no TEXT NOT NULL,
    supplier_id BIGINT REFERENCES public.suppliers(id) ON DELETE SET NULL,
    supplier_name TEXT,
    purchase_date BIGINT NOT NULL,
    total_poisha BIGINT NOT NULL,
    paid_amount_poisha BIGINT NOT NULL,
    due_amount_poisha BIGINT DEFAULT 0,
    note TEXT,
    created_at BIGINT NOT NULL
);

-- 10. Purchase Items
CREATE TABLE IF NOT EXISTS public.purchase_items (
    id BIGINT PRIMARY KEY,
    purchase_id BIGINT REFERENCES public.purchases(id) ON DELETE CASCADE,
    product_id BIGINT,
    product_name TEXT NOT NULL,
    unit_name TEXT DEFAULT 'পিস',
    qty NUMERIC NOT NULL,
    unit_cost_poisha BIGINT NOT NULL,
    line_total_poisha BIGINT NOT NULL
);

-- 11. Stock Adjustments
CREATE TABLE IF NOT EXISTS public.stock_adjustments (
    id BIGINT PRIMARY KEY,
    product_id BIGINT,
    product_name TEXT NOT NULL,
    qty_change NUMERIC NOT NULL,
    reason TEXT NOT NULL,
    note TEXT,
    created_at BIGINT NOT NULL
);

-- 12. App Config (Optional remote config sync)
CREATE TABLE IF NOT EXISTS public.app_config (
    config_key TEXT PRIMARY KEY,
    config_value TEXT NOT NULL,
    updated_at BIGINT DEFAULT (extract(epoch from now()) * 1000)::BIGINT
);

-- ========================================================================
-- ENABLE ROW LEVEL SECURITY (RLS) WITH FULL CLIENT ACCESS
-- ========================================================================
DO $$
DECLARE
    t text;
    tbls text[] := ARRAY[
        'categories', 'products', 'customers', 'sales', 'sale_items',
        'customer_ledger', 'expenses', 'suppliers', 'purchases', 
        'purchase_items', 'stock_adjustments', 'app_config'
    ];
BEGIN
    FOREACH t IN ARRAY tbls LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY;', t);
        EXECUTE format('DROP POLICY IF EXISTS "Allow full access to anon and authenticated" ON public.%I;', t);
        EXECUTE format('CREATE POLICY "Allow full access to anon and authenticated" ON public.%I FOR ALL USING (true) WITH CHECK (true);', t);
    END LOOP;
END $$;

-- Indexes for lightning fast queries
CREATE INDEX IF NOT EXISTS idx_products_barcode ON public.products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_category ON public.products(category_id);
CREATE INDEX IF NOT EXISTS idx_sales_date ON public.sales(sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_customer ON public.sales(customer_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON public.sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_customer_ledger_customer ON public.customer_ledger(customer_id);
CREATE INDEX IF NOT EXISTS idx_expenses_date ON public.expenses(expense_date);
CREATE INDEX IF NOT EXISTS idx_purchases_date ON public.purchases(purchase_date);
