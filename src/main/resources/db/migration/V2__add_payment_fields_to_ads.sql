-- Add payment fields to product_ads table
ALTER TABLE public.product_ads
ADD COLUMN total_amount numeric DEFAULT 0,
ADD COLUMN payment_ref character varying UNIQUE,
ADD COLUMN payment_url text;

-- Change status constraint for product_ads
ALTER TABLE public.product_ads DROP CONSTRAINT IF EXISTS product_ads_status_check;
ALTER TABLE public.product_ads ADD CONSTRAINT product_ads_status_check CHECK (status::text = ANY (ARRAY['PENDING'::character varying::text, 'ACTIVE'::character varying::text, 'PAUSED'::character varying::text, 'EXPIRED'::character varying::text]));
ALTER TABLE public.product_ads ALTER COLUMN status SET DEFAULT 'PENDING';

-- Add payment fields to banners table
ALTER TABLE public.banners
ADD COLUMN payment_ref character varying UNIQUE,
ADD COLUMN payment_url text;
