-- Removes the price from the 'demo already used' message. Run once in Supabase → SQL Editor.
CREATE OR REPLACE FUNCTION public.start_or_verify_device_demo(
    p_device_id TEXT,
    p_device_model TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_record RECORD;
    v_now TIMESTAMPTZ := now();
    v_clean_device_id TEXT;
    v_remaining_secs INT;
BEGIN
    v_clean_device_id := trim(p_device_id);

    IF v_clean_device_id IS NULL OR v_clean_device_id = '' THEN
        RETURN jsonb_build_object(
            'success', false,
            'remaining_seconds', 0,
            'message', 'ডিভাইস শনাক্তকরণ ব্যর্থ হয়েছে।'
        );
    END IF;

    -- Look up device in demo_devices
    SELECT * INTO v_record
    FROM public.demo_devices
    WHERE device_id = v_clean_device_id;

    IF NOT FOUND THEN
        -- New device: Register and grant 1 hour
        INSERT INTO public.demo_devices (
            device_id,
            device_model,
            first_started_at,
            expires_at
        )
        VALUES (
            v_clean_device_id,
            p_device_model,
            v_now,
            v_now + INTERVAL '1 hour'
        );

        RETURN jsonb_build_object(
            'success', true,
            'is_new', true,
            'remaining_seconds', 3600,
            'message', '১ ঘণ্টার ফ্রি ডেমো সফলভাবে শুরু হয়েছে।'
        );
    ELSE
        -- Device was previously registered
        IF v_now >= v_record.expires_at THEN
            RETURN jsonb_build_object(
                'success', false,
                'is_new', false,
                'remaining_seconds', 0,
                'message', 'আপনার এই ডিভাইসে ১ ঘণ্টার ফ্রি ডেমো সেশন ইতিমধ্যে শেষ হয়েছে। Dokan-Pro নিয়মিত ব্যবহার করতে লাইসেন্স সক্রিয় করুন।'
            );
        ELSE
            v_remaining_secs := EXTRACT(EPOCH FROM (v_record.expires_at - v_now))::INT;
            RETURN jsonb_build_object(
                'success', true,
                'is_new', false,
                'remaining_seconds', v_remaining_secs,
                'message', 'ডেমো সেশন রিস্টোর করা হয়েছে।'
            );
        END IF;
    END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.start_or_verify_device_demo(TEXT, TEXT) TO anon, authenticated;

