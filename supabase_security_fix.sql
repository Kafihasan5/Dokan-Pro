-- =====================================================================
-- Dokan Pro — Supabase security fix (run once in the Supabase SQL editor)
-- =====================================================================
-- 1. issue_app_license was executable by the public anon key (which ships inside the app),
--    so anyone could create, unblock or extend any license. Only the service role may call it now.
-- 2. Support chat tables were readable/writable by anon: every shop could read every other
--    shop's support conversation. Support now runs through Firebase Cloud Functions, so anon
--    access is removed entirely.
-- 3. verify_app_license: a read-only check the app uses to re-validate its license
--    (expired / blocked / removed licenses stop working).
-- =====================================================================

-- 1. License issuing: server / dashboard only
REVOKE EXECUTE ON FUNCTION public.issue_app_license(TEXT, TEXT, TEXT, TEXT, INT, TIMESTAMPTZ) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.issue_app_license(TEXT, TEXT, TEXT, TEXT, INT, TIMESTAMPTZ) FROM anon;
REVOKE EXECUTE ON FUNCTION public.issue_app_license(TEXT, TEXT, TEXT, TEXT, INT, TIMESTAMPTZ) FROM authenticated;
GRANT EXECUTE ON FUNCTION public.issue_app_license(TEXT, TEXT, TEXT, TEXT, INT, TIMESTAMPTZ) TO service_role;

-- Functions are executable by PUBLIC by default; make sure new SECURITY DEFINER functions are not.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- 2. Support tables: no anonymous access
DO $$
BEGIN
    IF to_regclass('public.support_threads') IS NOT NULL THEN
        DROP POLICY IF EXISTS "Allow anon read threads" ON public.support_threads;
        DROP POLICY IF EXISTS "Allow anon upsert threads" ON public.support_threads;
        REVOKE ALL ON public.support_threads FROM anon, authenticated;
    END IF;
    IF to_regclass('public.support_messages') IS NOT NULL THEN
        DROP POLICY IF EXISTS "Allow anon read messages" ON public.support_messages;
        DROP POLICY IF EXISTS "Allow anon insert messages" ON public.support_messages;
        REVOKE ALL ON public.support_messages FROM anon, authenticated;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regprocedure('public.receive_telegram_support_reply(bigint,text)') IS NOT NULL THEN
        EXECUTE 'REVOKE EXECUTE ON FUNCTION public.receive_telegram_support_reply(bigint,text) FROM PUBLIC, anon, authenticated';
    END IF;
END $$;

-- 3. Read-only license re-validation (does not register new devices)
CREATE OR REPLACE FUNCTION public.verify_app_license(
    p_email TEXT,
    p_device_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_license RECORD;
BEGIN
    SELECT * INTO v_license FROM public.app_licenses WHERE lower(email) = lower(trim(p_email));
    IF NOT FOUND THEN
        RETURN jsonb_build_object('valid', false, 'reason', 'not_found',
            'message', 'এই লাইসেন্সটি আর পাওয়া যাচ্ছে না। সহায়তার জন্য যোগাযোগ করুন।');
    END IF;
    IF v_license.status = 'blocked' THEN
        RETURN jsonb_build_object('valid', false, 'reason', 'blocked',
            'message', 'আপনার লাইসেন্সটি স্থগিত করা হয়েছে। সহায়তার জন্য সাপোর্টে যোগাযোগ করুন।');
    END IF;
    IF v_license.expires_at IS NOT NULL AND v_license.expires_at < now() THEN
        RETURN jsonb_build_object('valid', false, 'reason', 'expired',
            'message', 'আপনার সাবস্ক্রিপশনের মেয়াদ শেষ হয়েছে। রিনিউ করতে যোগাযোগ করুন।');
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(v_license.device_ids) AS elem
        WHERE elem->>'device_id' = p_device_id
    ) THEN
        RETURN jsonb_build_object('valid', false, 'reason', 'device_removed',
            'message', 'এই ডিভাইসটি লাইসেন্স থেকে সরিয়ে দেওয়া হয়েছে।');
    END IF;
    RETURN jsonb_build_object('valid', true, 'expires_at', v_license.expires_at);
END;
$$;

REVOKE EXECUTE ON FUNCTION public.verify_app_license(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.verify_app_license(TEXT, TEXT) TO anon, authenticated;

-- The functions the app legitimately calls stay available to anon.
GRANT EXECUTE ON FUNCTION public.activate_app_license(TEXT, TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.start_or_verify_device_demo(TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_device_demo_status(TEXT) TO anon, authenticated;

-- Check: this must return no rows.
-- SELECT grantee FROM information_schema.routine_privileges
--  WHERE routine_name = 'issue_app_license' AND grantee IN ('anon', 'authenticated', 'PUBLIC');
