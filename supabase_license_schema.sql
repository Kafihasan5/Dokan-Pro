-- ==============================================================================
-- Dokan-Pro App Licensing & Activation Schema for Supabase
-- Integrates with Webix Solution (Laravel) Website
-- ==============================================================================

-- 1. Create the licenses table
CREATE TABLE IF NOT EXISTS public.app_licenses (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    customer_name TEXT DEFAULT '',
    customer_phone TEXT DEFAULT '',
    order_id TEXT DEFAULT '',
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'blocked')),
    max_devices INT NOT NULL DEFAULT 1,
    device_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ DEFAULT NULL -- NULL means Lifetime License
);

-- Index for fast email lookup
CREATE INDEX IF NOT EXISTS idx_app_licenses_email ON public.app_licenses (lower(email));

-- 2. Enable Row Level Security (RLS)
ALTER TABLE public.app_licenses ENABLE ROW LEVEL SECURITY;

-- 3. Security Policy:
-- Do NOT allow direct table SELECT, INSERT, UPDATE, DELETE to anonymous public users!
-- Only Service Role (Laravel backend) has full access to the table.
DROP POLICY IF EXISTS "Deny direct public access" ON public.app_licenses;
CREATE POLICY "Deny direct public access"
    ON public.app_licenses
    FOR ALL
    TO anon
    USING (false);

-- 4. Secure Activation RPC Function (Callable by Android App with anon key)
-- Validates buyer email, checks device limits, and registers the device.
CREATE OR REPLACE FUNCTION public.activate_app_license(
    p_email TEXT,
    p_device_id TEXT,
    p_device_model TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER -- Runs with elevated privileges to update the table safely
SET search_path = public
AS $$
DECLARE
    v_license RECORD;
    v_clean_email TEXT;
    v_device_exists BOOLEAN := FALSE;
    v_device_count INT := 0;
    v_now TIMESTAMPTZ := now();
    v_new_device JSONB;
BEGIN
    -- Normalize email
    v_clean_email := lower(trim(p_email));
    
    IF v_clean_email IS NULL OR v_clean_email = '' THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'অনুগ্রহ করে সঠিক ইমেইল প্রদান করুন।'
        );
    END IF;

    IF p_device_id IS NULL OR trim(p_device_id) = '' THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'ডিভাইস শনাক্তকরণ ব্যর্থ হয়েছে।'
        );
    END IF;

    -- Lookup license
    SELECT * INTO v_license
    FROM public.app_licenses
    WHERE lower(email) = v_clean_email;

    -- Case 1: Email not found
    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'এই ইমেইলটি নিবন্ধিত নয়। অ্যাপটি ব্যবহার করতে অনুগ্রহ করে Webix Solution থেকে লাইসেন্স ক্রয় করুন।'
        );
    END IF;

    -- Case 2: License blocked
    IF v_license.status = 'blocked' THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'আপনার লাইসেন্সটি স্থগিত করা হয়েছে। সহায়তার জন্য সাপোর্টে যোগাযোগ করুন।'
        );
    END IF;

    -- Case 3: License expired
    IF v_license.expires_at IS NOT NULL AND v_license.expires_at < v_now THEN
        UPDATE public.app_licenses SET status = 'expired' WHERE id = v_license.id;
        RETURN jsonb_build_object(
            'success', false,
            'message', 'আপনার সাবস্ক্রিপশন মেয়াদের সময় শেষ হয়েছে। রিনিউ করতে Webix Solution-এ ভিজিট করুন।'
        );
    END IF;

    -- Check if device is already registered in device_ids array
    SELECT EXISTS (
        SELECT 1
        FROM jsonb_array_elements(v_license.device_ids) AS elem
        WHERE elem->>'device_id' = p_device_id
    ) INTO v_device_exists;

    IF v_device_exists THEN
        -- Device already authorized! Return success
        RETURN jsonb_build_object(
            'success', true,
            'message', 'অ্যাপটি সফলভাবে সক্রিয় করা হয়েছে।',
            'email', v_license.email,
            'customer_name', v_license.customer_name,
            'status', v_license.status,
            'expires_at', v_license.expires_at,
            'is_lifetime', (v_license.expires_at IS NULL)
        );
    END IF;

    -- If device is new, check device limit
    v_device_count := jsonb_array_length(v_license.device_ids);
    IF v_device_count >= v_license.max_devices THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', format('সর্বোচ্চ ডিভাইস লিমিট (%s টি) অতিক্রান্ত হয়েছে! এই লাইসেন্সটি ইতিমধ্যে অন্য ডিভাইসে সক্রিয় রয়েছে।', v_license.max_devices)
        );
    END IF;

    -- Register this new device
    v_new_device := jsonb_build_object(
        'device_id', p_device_id,
        'model', p_device_model,
        'activated_at', v_now
    );

    UPDATE public.app_licenses
    SET device_ids = device_ids || v_new_device
    WHERE id = v_license.id;

    RETURN jsonb_build_object(
        'success', true,
        'message', 'অভিনন্দন! আপনার Dokan-Pro অ্যাপ সফলভাবে সক্রিয় হয়েছে।',
        'email', v_license.email,
        'customer_name', v_license.customer_name,
        'status', v_license.status,
        'expires_at', v_license.expires_at,
        'is_lifetime', (v_license.expires_at IS NULL)
    );
END;
$$;

-- 5. Grant execute permission to anon users so Android App can call the RPC
GRANT EXECUTE ON FUNCTION public.activate_app_license(TEXT, TEXT, TEXT) TO anon, authenticated;

-- 6. Issue License RPC Function (Callable by Laravel backend with anon or service_role key)
CREATE OR REPLACE FUNCTION public.issue_app_license(
    p_email TEXT,
    p_customer_name TEXT DEFAULT '',
    p_customer_phone TEXT DEFAULT '',
    p_order_id TEXT DEFAULT '',
    p_max_devices INT DEFAULT 1,
    p_expires_at TIMESTAMPTZ DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_email TEXT;
BEGIN
    v_clean_email := lower(trim(p_email));
    
    IF v_clean_email IS NULL OR v_clean_email = '' THEN
        RETURN jsonb_build_object('success', false, 'message', 'Email is required');
    END IF;

    INSERT INTO public.app_licenses (
        email,
        customer_name,
        customer_phone,
        order_id,
        status,
        max_devices,
        expires_at
    )
    VALUES (
        v_clean_email,
        p_customer_name,
        p_customer_phone,
        p_order_id,
        'active',
        p_max_devices,
        p_expires_at
    )
    ON CONFLICT (email) DO UPDATE SET
        customer_name = EXCLUDED.customer_name,
        customer_phone = EXCLUDED.customer_phone,
        order_id = EXCLUDED.order_id,
        status = 'active',
        max_devices = EXCLUDED.max_devices,
        expires_at = EXCLUDED.expires_at;

    RETURN jsonb_build_object('success', true, 'message', 'License issued successfully', 'email', v_clean_email);
END;
$$;

GRANT EXECUTE ON FUNCTION public.issue_app_license(TEXT, TEXT, TEXT, TEXT, INT, TIMESTAMPTZ) TO anon, authenticated, service_role;

-- ==============================================================================
-- Optional Test Data (Uncomment to test)
-- ==============================================================================
-- INSERT INTO public.app_licenses (email, customer_name, customer_phone, order_id, max_devices)
-- VALUES ('test@webixsolution.com', 'Test Customer', '01700000000', 'ORD-1001', 1)
-- ON CONFLICT (email) DO NOTHING;
