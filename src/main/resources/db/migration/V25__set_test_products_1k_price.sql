-- Set test product (Brass Ring Key Hook) price to 1000 VND (1k) for SePay payment testing
UPDATE product_variants
SET price = 1000.00
WHERE sku LIKE '%DEV-CAT-031%'
   OR product_id IN (
       SELECT id FROM products WHERE slug = 'brass-ring-key-hook'
   );
