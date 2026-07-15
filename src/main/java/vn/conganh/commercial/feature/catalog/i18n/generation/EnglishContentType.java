package vn.conganh.commercial.feature.catalog.i18n.generation;

import java.util.List;

public enum EnglishContentType {
    PRODUCT(List.of(
            "name",
            "shortDescription",
            "description",
            "material",
            "careInstruction",
            "seoTitle",
            "seoDescription")),
    CATEGORY(List.of("name", "description", "seoTitle", "seoDescription")),
    SALE_CAMPAIGN(List.of("name", "description"));

    private final List<String> fields;

    EnglishContentType(List<String> fields) {
        this.fields = fields;
    }

    public List<String> fields() {
        return fields;
    }
}
