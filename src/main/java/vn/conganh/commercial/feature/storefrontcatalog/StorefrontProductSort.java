package vn.conganh.commercial.feature.storefrontcatalog;

import java.util.Locale;
import vn.conganh.commercial.exception.InvalidRequestException;

enum StorefrontProductSort {
    FEATURED,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC;

    static StorefrontProductSort from(String value) {
        if (value == null || value.isBlank()) {
            return FEATURED;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "featured" -> FEATURED;
            case "newest" -> NEWEST;
            case "price-asc" -> PRICE_ASC;
            case "price-desc" -> PRICE_DESC;
            default -> throw new InvalidRequestException("Unsupported storefront product sort: " + value);
        };
    }
}
