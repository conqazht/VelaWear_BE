CREATE INDEX idx_brands_status ON brands(status);
CREATE INDEX idx_brands_created_at ON brands(created_at);

CREATE INDEX idx_categories_status ON categories(status);
CREATE INDEX idx_categories_created_at ON categories(created_at);

CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_created_at ON products(created_at);

CREATE INDEX idx_product_variants_status ON product_variants(status);
CREATE INDEX idx_product_variants_price ON product_variants(price);
CREATE INDEX idx_product_variants_sale_price ON product_variants(sale_price);
CREATE INDEX idx_product_variants_stock_quantity ON product_variants(stock_quantity);
CREATE INDEX idx_product_variants_created_at ON product_variants(created_at);

CREATE INDEX idx_coupons_type ON coupons(type);
CREATE INDEX idx_coupons_status ON coupons(status);
CREATE INDEX idx_coupons_value ON coupons(value);
CREATE INDEX idx_coupons_min_order_amount ON coupons(min_order_amount);
CREATE INDEX idx_coupons_max_discount ON coupons(max_discount);
CREATE INDEX idx_coupons_usage_limit ON coupons(usage_limit);
CREATE INDEX idx_coupons_used_count ON coupons(used_count);
CREATE INDEX idx_coupons_end_date ON coupons(end_date);

CREATE INDEX idx_users_gender ON users(gender);
CREATE INDEX idx_users_birth_date ON users(birth_date);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_updated_at ON users(updated_at);

CREATE INDEX idx_roles_created_at ON roles(created_at);
CREATE INDEX idx_roles_updated_at ON roles(updated_at);

CREATE INDEX idx_permissions_method ON permissions(method);
CREATE INDEX idx_permissions_created_at ON permissions(created_at);
CREATE INDEX idx_permissions_updated_at ON permissions(updated_at);

CREATE INDEX idx_user_addresses_is_default ON user_addresses(is_default);

CREATE INDEX idx_carts_created_at ON carts(created_at);

CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_payment_method ON orders(payment_method);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_orders_final_amount ON orders(final_amount);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_updated_at ON orders(updated_at);

CREATE INDEX idx_payments_provider ON payments(provider);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_amount ON payments(amount);
CREATE INDEX idx_payments_paid_at ON payments(paid_at);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_payments_updated_at ON payments(updated_at);

CREATE INDEX idx_reviews_rating ON reviews(rating);
CREATE INDEX idx_reviews_created_at ON reviews(created_at);

CREATE INDEX idx_wishlists_created_at ON wishlists(created_at);

CREATE INDEX idx_order_status_histories_to_status ON order_status_histories(to_status);
