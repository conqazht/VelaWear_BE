-- V29__add_membership_tier_and_coupon_vip.sql

ALTER TABLE coupons ADD COLUMN min_tier VARCHAR(20) DEFAULT 'STANDARD';

UPDATE coupons SET min_tier = 'STANDARD' WHERE min_tier IS NULL;

CREATE INDEX idx_coupons_min_tier ON coupons(min_tier);

CREATE INDEX idx_orders_user_status_created ON orders(user_id, status, created_at);

-- Seed sample VIP coupons for testing
INSERT INTO coupons (code, type, value, min_order_amount, max_discount, usage_limit, used_count, start_date, end_date, status, min_tier)
VALUES
    ('VIPSILVER10', 'PERCENTAGE', 10.00, 300000.00, 100000.00, 500, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '180 days', 'ACTIVE', 'SILVER'),
    ('VIPGOLD15', 'PERCENTAGE', 15.00, 500000.00, 200000.00, 300, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '180 days', 'ACTIVE', 'GOLD'),
    ('VIPDIAMOND20', 'PERCENTAGE', 20.00, 1000000.00, 500000.00, 100, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '180 days', 'ACTIVE', 'DIAMOND')
ON CONFLICT (code) DO NOTHING;
