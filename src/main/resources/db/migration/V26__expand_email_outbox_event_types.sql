ALTER TABLE email_outbox
    DROP CONSTRAINT ck_email_outbox_event_type,
    ADD CONSTRAINT ck_email_outbox_event_type CHECK (event_type IN ('ORDER_CREATED', 'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'ORDER_COMPLETED'));

ALTER TABLE email_outbox
    DROP CONSTRAINT ck_email_outbox_template_key,
    ADD CONSTRAINT ck_email_outbox_template_key CHECK (template_key IN ('ORDER_CREATED', 'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'ORDER_COMPLETED'));
