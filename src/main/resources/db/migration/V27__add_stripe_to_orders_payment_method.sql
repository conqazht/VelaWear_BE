ALTER TABLE orders DROP CONSTRAINT ck_orders_payment_method;
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_method
    CHECK (payment_method IN ('COD', 'VNPAY', 'MOMO', 'BANK_TRANSFER', 'SEPAY', 'STRIPE'));
