package vn.conganh.commercial.feature.salecampaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VariantPricing model")
class VariantPricingTest {

    @Test
    @DisplayName("salePriority returns 2 for FLASH_SALE, 1 for STANDARD_SALE, 0 for BASE")
    void salePriority_returnsExpectedPriority() {
        VariantPricing flash = pricing(PriceSource.FLASH_SALE, new BigDecimal("100"), new BigDecimal("80"));
        VariantPricing standard = pricing(PriceSource.STANDARD_SALE, new BigDecimal("100"), new BigDecimal("90"));
        VariantPricing base = pricing(PriceSource.BASE, new BigDecimal("100"), new BigDecimal("100"));

        assertThat(flash.salePriority()).isEqualTo(2);
        assertThat(standard.salePriority()).isEqualTo(1);
        assertThat(base.salePriority()).isEqualTo(0);
    }

    @Test
    @DisplayName("discountRate calculates correct discount ratio")
    void discountRate_calculatesAccurately() {
        VariantPricing pricing = pricing(PriceSource.FLASH_SALE, new BigDecimal("200.00"), new BigDecimal("150.00"));
        assertThat(pricing.discountRate()).isEqualByComparingTo("0.250000");
    }

    @Test
    @DisplayName("discountRate returns zero for non-positive list price or no discount")
    void discountRate_nonPositiveOrNoDiscount_returnsZero() {
        VariantPricing zeroPrice = pricing(PriceSource.BASE, BigDecimal.ZERO, BigDecimal.ZERO);
        VariantPricing noDiscount = pricing(PriceSource.BASE, new BigDecimal("100"), new BigDecimal("100"));
        VariantPricing negativeDiscount = pricing(PriceSource.BASE, new BigDecimal("100"), new BigDecimal("120"));

        assertThat(zeroPrice.discountRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(noDiscount.discountRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(negativeDiscount.discountRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private VariantPricing pricing(PriceSource source, BigDecimal listPrice, BigDecimal effectivePrice) {
        return new VariantPricing(
                1L,
                listPrice,
                effectivePrice,
                source,
                null,
                null,
                null,
                10);
    }
}
