CREATE TABLE locales (
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_locales PRIMARY KEY (code),
    CONSTRAINT ck_locales_code CHECK (code ~ '^[a-z]{2}(-[a-z]{2})?$')
);

CREATE UNIQUE INDEX uidx_locales_single_default
    ON locales(is_default)
    WHERE is_default = TRUE;

INSERT INTO locales (code, name, is_default, is_enabled)
VALUES ('vi', 'Vietnamese', TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    is_default = EXCLUDED.is_default,
    is_enabled = EXCLUDED.is_enabled;

CREATE TABLE product_translations (
    product_id BIGINT NOT NULL,
    locale_code VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(280) NOT NULL,
    short_description VARCHAR(500),
    description TEXT,
    material TEXT,
    care_instruction TEXT,
    seo_title VARCHAR(255),
    seo_description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_translations PRIMARY KEY (product_id, locale_code),
    CONSTRAINT uq_product_translations_locale_slug UNIQUE (locale_code, slug),
    CONSTRAINT fk_product_translations_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_translations_locale FOREIGN KEY (locale_code) REFERENCES locales(code) ON DELETE RESTRICT
);

CREATE TABLE category_translations (
    category_id BIGINT NOT NULL,
    locale_code VARCHAR(10) NOT NULL,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    description TEXT,
    seo_title VARCHAR(255),
    seo_description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_category_translations PRIMARY KEY (category_id, locale_code),
    CONSTRAINT uq_category_translations_locale_slug UNIQUE (locale_code, slug),
    CONSTRAINT fk_category_translations_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT fk_category_translations_locale FOREIGN KEY (locale_code) REFERENCES locales(code) ON DELETE RESTRICT
);

CREATE INDEX idx_product_translations_locale_code ON product_translations(locale_code);
CREATE INDEX idx_category_translations_locale_code ON category_translations(locale_code);

INSERT INTO product_translations (
    product_id,
    locale_code,
    name,
    slug,
    short_description,
    description,
    seo_title,
    seo_description
)
SELECT
    id,
    'vi',
    name,
    slug,
    description,
    description,
    name,
    description
FROM products
ON CONFLICT (product_id, locale_code) DO UPDATE
SET
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    short_description = EXCLUDED.short_description,
    description = EXCLUDED.description,
    seo_title = EXCLUDED.seo_title,
    seo_description = EXCLUDED.seo_description;

INSERT INTO category_translations (
    category_id,
    locale_code,
    name,
    slug,
    description,
    seo_title,
    seo_description
)
SELECT
    id,
    'vi',
    name,
    slug,
    NULL,
    name,
    NULL
FROM categories
ON CONFLICT (category_id, locale_code) DO UPDATE
SET
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    description = EXCLUDED.description,
    seo_title = EXCLUDED.seo_title,
    seo_description = EXCLUDED.seo_description;

CREATE TRIGGER trg_locales_set_updated_at
BEFORE UPDATE ON locales
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_product_translations_set_updated_at
BEFORE UPDATE ON product_translations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_category_translations_set_updated_at
BEFORE UPDATE ON category_translations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
