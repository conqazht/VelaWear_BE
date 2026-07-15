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
import vn.conganh.commercial.feature.salecampaign.dto.PublicSalesResponse;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "Sales", description = "Public scheduled and flash sales")
public class PublicSaleController {

    private final SaleCampaignService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PublicSalesResponse>> getSales(
            @RequestParam(required = false) SaleCampaignType type,
            @RequestParam(required = false, name = "phase") List<SaleCampaignPhase> phases) {
        return ResponseEntity.ok(ApiResponse.success(service.getPublic(type, phases)));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<SaleCampaignResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(service.getPublicByCode(code)));
    }
}
