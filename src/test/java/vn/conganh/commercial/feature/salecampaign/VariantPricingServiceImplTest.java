package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class VariantPricingServiceImplTest {

    @Mock SaleCampaignItemRepository itemRepository;
    @Mock SaleCustomerUsageRepository usageRepository;
    @Mock UserRepository userRepository;
    @InjectMocks VariantPricingServiceImpl service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedCatalogPricingIncludesCustomerRemaining() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("buyer@example.com");
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(user.getEmail())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 3L);
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 11L);
        variant.setProduct(product);
        variant.setPrice(new BigDecimal("100.00"));
        variant.setStockQuantity(10);

        SaleCampaign campaign = new SaleCampaign();
        ReflectionTestUtils.setField(campaign, "id", 19L);
        campaign.setType(SaleCampaignType.FLASH);
        campaign.setStatus(SaleCampaignStatus.PUBLISHED);
        campaign.setStartsAt(Instant.now().minusSeconds(60));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        SaleCampaignItem item = new SaleCampaignItem();
        ReflectionTestUtils.setField(item, "id", 23L);
        item.setVariant(variant);
        item.setReferencePrice(variant.getPrice());
        item.setPromotionalPrice(new BigDecimal("80.00"));
        item.setQuota(10);
        item.setMaxPerCustomer(2);
        campaign.replaceItems(List.of(item));

        SaleCustomerUsage usage = new SaleCustomerUsage();
        usage.setCampaignItem(item);
        usage.setUser(user);
        usage.setPurchasedQuantity(1);

        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));
        when(itemRepository.findActiveForVariants(eq(List.of(11L)), any(Instant.class)))
                .thenReturn(List.of(item));
        when(usageRepository.findByCampaignItemIdInAndUserId(List.of(23L), 7L))
                .thenReturn(List.of(usage));

        VariantPricing pricing = service.resolve(List.of(variant)).get(11L);

        assertThat(pricing.priceSource()).isEqualTo(PriceSource.FLASH_SALE);
        assertThat(pricing.customerRemaining()).isEqualTo(1);
        assertThat(pricing.availableQuantity()).isEqualTo(1);
    }
}
