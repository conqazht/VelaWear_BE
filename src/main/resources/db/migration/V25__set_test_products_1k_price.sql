-- Set test product price to 1000 VND (1k) for SePay payment testing
UPDATE product_variants
SET price = 1000.00
WHERE sku IN ('VW-TEE-BLK-M', 'VW-TEE-RED-L')
   OR sku LIKE '%DEV-CAT-031%'
   OR product_id IN (
       SELECT id FROM products WHERE slug IN ('essential-cotton-tee', 'brass-ring-key-hook')
   );
