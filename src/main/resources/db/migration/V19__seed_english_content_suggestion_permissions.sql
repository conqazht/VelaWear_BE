INSERT INTO permissions (name, api_path, method, module)
VALUES
    (
        'GENERATE_PRODUCT_ENGLISH_CONTENT',
        '/api/v1/products/translation-suggestions/en',
        'POST',
        'PRODUCT'
    ),
    (
        'GENERATE_CATEGORY_ENGLISH_CONTENT',
        '/api/v1/categories/translation-suggestions/en',
        'POST',
        'CATEGORY'
    ),
    (
        'GENERATE_SALE_CAMPAIGN_ENGLISH_CONTENT',
        '/api/v1/sale-campaigns/translation-suggestions/en',
        'POST',
        'SALE'
    )
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT permission.id, role.id
FROM permissions permission
CROSS JOIN roles role
WHERE permission.name IN (
        'GENERATE_PRODUCT_ENGLISH_CONTENT',
        'GENERATE_CATEGORY_ENGLISH_CONTENT',
        'GENERATE_SALE_CAMPAIGN_ENGLISH_CONTENT'
    )
  AND role.name IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;
