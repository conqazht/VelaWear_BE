package vn.conganh.commercial.feature.salecampaign;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.salecampaign.dto.PublicSalesResponse;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "Sales", description = "Public scheduled and flash sales")
public class PublicSaleController {

    private final SaleCampaignService service;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<PublicSalesResponse>> getSales(
            @RequestParam(required = false) SaleCampaignType type,
            @RequestParam(required = false, name = "phase") List<SaleCampaignPhase> phases,
            @RequestParam(required = false) String locale,
            @org.springframework.web.bind.annotation.RequestHeader(
                    name = "Accept-Language",
                    required = false) String acceptLanguage) {
        String localeCode = localeResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(service.getPublic(type, phases, localeCode)));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> getByCode(
            @PathVariable String code,
            @RequestParam(required = false) String locale,
            @org.springframework.web.bind.annotation.RequestHeader(
                    name = "Accept-Language",
                    required = false) String acceptLanguage) {
        String localeCode = localeResolver.resolve(locale, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success(service.getPublicByCode(code, localeCode)));
    }
}
