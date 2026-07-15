ALTER TABLE order_items
    ADD COLUMN product_slug VARCHAR(280);

UPDATE order_items order_item
SET product_slug = product.slug
FROM product_variants variant
JOIN products product ON product.id = variant.product_id
WHERE order_item.variant_id = variant.id
  AND order_item.product_slug IS NULL;
