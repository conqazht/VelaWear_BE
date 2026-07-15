package vn.conganh.commercial.feature.salecampaign;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.salecampaign.dto.CreateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.EndAndCloneSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.IncreaseQuotaRequest;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignFilterRequest;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignTranslationsResponse;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleDisplayRequest;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignTranslationsRequest;

@RestController
@RequestMapping("/api/v1/sale-campaigns")
@RequiredArgsConstructor
@Tag(name = "Sale Campaigns", description = "Admin sale campaign management")
public class SaleCampaignController {

    private final SaleCampaignService service;
    private final SaleCampaignTranslationService translationService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getAll(
            @ParameterObject SaleCampaignFilterRequest filter,
            @ParameterObject Pageable pageable,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getAll(filter, pageable, localeResolver.resolve(locale, acceptLanguage))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> getById(
            @PathVariable Long id,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getById(id, localeResolver.resolve(locale, acceptLanguage))));
    }

    @GetMapping("/{id}/translations")
    public ResponseEntity<ApiResponse<SaleCampaignTranslationsResponse>> getTranslations(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(translationService.getTranslations(id)));
    }

    @PutMapping("/{id}/translations")
    public ResponseEntity<ApiResponse<SaleCampaignTranslationsResponse>> updateTranslations(
            @PathVariable Long id,
            @RequestBody @Valid UpdateSaleCampaignTranslationsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(translationService.updateTranslations(id, request)));
    }

    @DeleteMapping("/{id}/translations/{localeCode}")
    public ResponseEntity<ApiResponse<SaleCampaignTranslationsResponse>> deleteTranslation(
            @PathVariable Long id,
            @PathVariable String localeCode,
            @RequestParam long version) {
        return ResponseEntity.ok(ApiResponse.success(
                translationService.deleteTranslation(id, localeCode, version)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> create(
            @RequestBody @Valid CreateSaleCampaignRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.create(request, subject(jwt))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateSaleCampaignRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> publish(
            @PathVariable Long id,
            @RequestParam long version,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.publish(id, version, subject(jwt))));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> cancel(
            @PathVariable Long id,
            @RequestParam long version) {
        return ResponseEntity.ok(ApiResponse.success(service.cancel(id, version)));
    }

    @PatchMapping("/{id}/display")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> updateDisplay(
            @PathVariable Long id,
            @RequestBody @Valid UpdateSaleDisplayRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateDisplay(id, request)));
    }

    @PostMapping("/{id}/items/{itemId}/increase-quota")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> increaseQuota(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestBody @Valid IncreaseQuotaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.increaseQuota(id, itemId, request)));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> end(
            @PathVariable Long id,
            @RequestParam long version) {
        return ResponseEntity.ok(ApiResponse.success(service.end(id, version)));
    }

    @PostMapping("/{id}/end-and-clone")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> endAndClone(
            @PathVariable Long id,
            @RequestBody @Valid EndAndCloneSaleCampaignRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.endAndClone(id, request, subject(jwt))));
    }

    private String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
