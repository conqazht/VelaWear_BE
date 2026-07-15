package vn.conganh.commercial.feature.catalog.i18n.generation;

import vn.conganh.commercial.feature.catalog.i18n.generation.dto.CategoryEnglishSuggestionRequest;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.CategoryEnglishSuggestionResponse;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.ProductEnglishSuggestionRequest;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.ProductEnglishSuggestionResponse;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.SaleCampaignEnglishSuggestionRequest;
import vn.conganh.commercial.feature.catalog.i18n.generation.dto.SaleCampaignEnglishSuggestionResponse;

public interface EnglishContentSuggestionService {

    ProductEnglishSuggestionResponse suggestProduct(ProductEnglishSuggestionRequest request);

    CategoryEnglishSuggestionResponse suggestCategory(CategoryEnglishSuggestionRequest request);

    SaleCampaignEnglishSuggestionResponse suggestSaleCampaign(SaleCampaignEnglishSuggestionRequest request);
}
