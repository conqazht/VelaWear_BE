INSERT INTO locales (code, name, is_default, is_enabled)
VALUES ('en', 'English', FALSE, TRUE)
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    is_enabled = EXCLUDED.is_enabled;

CREATE TABLE sale_campaign_translations (
    campaign_id BIGINT NOT NULL,
    locale_code VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sale_campaign_translations PRIMARY KEY (campaign_id, locale_code),
    CONSTRAINT fk_sale_campaign_translations_campaign
        FOREIGN KEY (campaign_id) REFERENCES sale_campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_campaign_translations_locale
        FOREIGN KEY (locale_code) REFERENCES locales(code) ON DELETE RESTRICT
);

CREATE INDEX idx_sale_campaign_translations_locale_code
    ON sale_campaign_translations(locale_code);

INSERT INTO sale_campaign_translations (campaign_id, locale_code, name, description)
SELECT id, 'vi', name, description
FROM sale_campaigns
ON CONFLICT (campaign_id, locale_code) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

CREATE TRIGGER trg_sale_campaign_translations_set_updated_at
BEFORE UPDATE ON sale_campaign_translations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
