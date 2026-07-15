DROP INDEX IF EXISTS idx_product_variants_sale_price;
ALTER TABLE product_variants DROP CONSTRAINT IF EXISTS ck_product_variants_sale_not_greater;
ALTER TABLE product_variants DROP CONSTRAINT IF EXISTS ck_product_variants_sale_price;
ALTER TABLE product_variants DROP COLUMN IF EXISTS sale_price;
