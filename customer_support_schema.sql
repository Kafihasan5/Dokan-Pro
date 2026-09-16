-- ==============================================================================
-- Dokan-Pro Telegram Bot Live Support Schema for Supabase
-- Integrates with Telegram Forum Topics (@dokanpro_bot) for 2-way customer chat
-- ==============================================================================

-- 1. Support Threads (Mapping Device ID -> Telegram Topic ID)
CREATE TABLE IF NOT EXISTS public.support_threads (
    device_id TEXT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    shop_name TEXT DEFAULT '',
    shop_phone TEXT DEFAULT '',
    app_version TEXT DEFAULT '',
    license_status TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_support_threads_topic ON public.support_threads (topic_id);

-- 2. Support Messages Table
CREATE TABLE IF NOT EXISTS public.support_messages (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id TEXT NOT NULL,
    topic_id BIGINT NOT NULL,
    sender TEXT NOT NULL CHECK (sender IN ('user', 'support')),
    message_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_read BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_support_messages_device ON public.support_messages (device_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_support_messages_topic ON public.support_messages (topic_id);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE public.support_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_messages ENABLE ROW LEVEL SECURITY;

-- 4. Permissive Policies for Dokan Pro App (anon key access by device_id)
DROP POLICY IF EXISTS "Allow anon read threads" ON public.support_threads;
CREATE POLICY "Allow anon read threads"
    ON public.support_threads FOR SELECT
    TO anon
    USING (true);

DROP POLICY IF EXISTS "Allow anon upsert threads" ON public.support_threads;
CREATE POLICY "Allow anon upsert threads"
    ON public.support_threads FOR ALL
    TO anon
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon read messages" ON public.support_messages;
CREATE POLICY "Allow anon read messages"
    ON public.support_messages FOR SELECT
    TO anon
    USING (true);

DROP POLICY IF EXISTS "Allow anon insert messages" ON public.support_messages;
CREATE POLICY "Allow anon insert messages"
    ON public.support_messages FOR INSERT
    TO anon
    WITH CHECK (true);

-- 5. RPC Function: Receive Telegram Webhook Reply
-- When admin replies in Telegram group topic, Telegram webhook calls this function or an Edge Function
CREATE OR REPLACE FUNCTION public.receive_telegram_support_reply(
    p_topic_id BIGINT,
    p_message_text TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_device_id TEXT;
BEGIN
    SELECT device_id INTO v_device_id
    FROM public.support_threads
    WHERE topic_id = p_topic_id
    LIMIT 1;

    IF v_device_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'No device thread found for this topic_id');
    END IF;

    INSERT INTO public.support_messages (device_id, topic_id, sender, message_text, created_at, is_read)
    VALUES (v_device_id, p_topic_id, 'support', p_message_text, now(), false);

    UPDATE public.support_threads
    SET last_message_at = now()
    WHERE device_id = v_device_id;

    RETURN jsonb_build_object('success', true, 'device_id', v_device_id);
END;
$$;
