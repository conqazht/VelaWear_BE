INSERT INTO permissions (name, api_path, method, module)
VALUES
    ('VIEW_PRODUCT_TRANSLATIONS', '/api/v1/products/{id}/translations', 'GET', 'PRODUCT'),
    ('UPDATE_PRODUCT_TRANSLATIONS', '/api/v1/products/{id}/translations', 'PUT', 'PRODUCT'),
    ('DELETE_PRODUCT_TRANSLATION', '/api/v1/products/{id}/translations/{locale}', 'DELETE', 'PRODUCT'),
    ('UPDATE_PRODUCT_STATUS', '/api/v1/products/{id}/status', 'PATCH', 'PRODUCT'),
    ('VIEW_CATEGORY_TRANSLATIONS', '/api/v1/categories/{id}/translations', 'GET', 'CATEGORY'),
    ('UPDATE_CATEGORY_TRANSLATIONS', '/api/v1/categories/{id}/translations', 'PUT', 'CATEGORY'),
    ('DELETE_CATEGORY_TRANSLATION', '/api/v1/categories/{id}/translations/{locale}', 'DELETE', 'CATEGORY'),
    ('UPDATE_CATEGORY_STATUS', '/api/v1/categories/{id}/status', 'PATCH', 'CATEGORY'),
    ('UPDATE_BRAND_STATUS', '/api/v1/brands/{id}/status', 'PATCH', 'BRAND'),
    ('UPDATE_PRODUCT_VARIANT_STATUS', '/api/v1/product-variants/{id}/status', 'PATCH', 'PRODUCT'),
    ('VIEW_SALE_CAMPAIGN_TRANSLATIONS', '/api/v1/sale-campaigns/{id}/translations', 'GET', 'SALE'),
    ('UPDATE_SALE_CAMPAIGN_TRANSLATIONS', '/api/v1/sale-campaigns/{id}/translations', 'PUT', 'SALE'),
    ('DELETE_SALE_CAMPAIGN_TRANSLATION', '/api/v1/sale-campaigns/{id}/translations/{locale}', 'DELETE', 'SALE')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT permission.id, role.id
FROM permissions permission
CROSS JOIN roles role
WHERE permission.name IN (
        'VIEW_PRODUCT_TRANSLATIONS',
        'UPDATE_PRODUCT_TRANSLATIONS',
        'DELETE_PRODUCT_TRANSLATION',
        'UPDATE_PRODUCT_STATUS',
        'VIEW_CATEGORY_TRANSLATIONS',
        'UPDATE_CATEGORY_TRANSLATIONS',
        'DELETE_CATEGORY_TRANSLATION',
        'UPDATE_CATEGORY_STATUS',
        'UPDATE_BRAND_STATUS',
        'UPDATE_PRODUCT_VARIANT_STATUS',
        'VIEW_SALE_CAMPAIGN_TRANSLATIONS',
        'UPDATE_SALE_CAMPAIGN_TRANSLATIONS',
        'DELETE_SALE_CAMPAIGN_TRANSLATION'
    )
  AND role.name IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role (permission_id, role_id)
SELECT permission.id, role.id
FROM permissions permission
CROSS JOIN roles role
WHERE permission.name IN (
        'VIEW_PRODUCT_TRANSLATIONS',
        'VIEW_CATEGORY_TRANSLATIONS',
        'VIEW_SALE_CAMPAIGN_TRANSLATIONS'
    )
  AND role.name = 'STAFF'
ON CONFLICT DO NOTHING;
