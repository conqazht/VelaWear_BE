package vn.conganh.commercial.feature.salecampaign;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.salecampaign.dto.CreateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.EndAndCloneSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.IncreaseQuotaRequest;
import vn.conganh.commercial.feature.salecampaign.dto.PublicSalesResponse;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignFilterRequest;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignResponse;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleCampaignRequest;
import vn.conganh.commercial.feature.salecampaign.dto.UpdateSaleDisplayRequest;

public interface SaleCampaignService {
    ResultPaginationDTO getAll(SaleCampaignFilterRequest filter, Pageable pageable);
    ResultPaginationDTO getAll(SaleCampaignFilterRequest filter, Pageable pageable, String localeCode);
    SaleCampaignResponse getById(Long id);
    SaleCampaignResponse getById(Long id, String localeCode);
    SaleCampaignResponse create(CreateSaleCampaignRequest request, String actorEmail);
    SaleCampaignResponse update(Long id, UpdateSaleCampaignRequest request);
    void delete(Long id);
    SaleCampaignResponse publish(Long id, long version, String actorEmail);
    SaleCampaignResponse cancel(Long id, long version);
    SaleCampaignResponse updateDisplay(Long id, UpdateSaleDisplayRequest request);
    SaleCampaignResponse increaseQuota(Long id, Long itemId, IncreaseQuotaRequest request);
    SaleCampaignResponse end(Long id, long version);
    SaleCampaignResponse endAndClone(Long id, EndAndCloneSaleCampaignRequest request, String actorEmail);
    PublicSalesResponse getPublic(
            SaleCampaignType type,
            java.util.List<SaleCampaignPhase> phases,
            String localeCode);
    SaleCampaignResponse getPublicByCode(String code, String localeCode);
}
