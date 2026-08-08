package vn.conganh.commercial.feature.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.cart.CartItem;
import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

@DisplayName("Module Checkout - CheckoutFingerprintService")
class CheckoutFingerprintServiceTest {

    private CheckoutFingerprintService fingerprintService;

    @BeforeEach
    void setUp() {
        fingerprintService = new CheckoutFingerprintService();
    }

    @Test
    @DisplayName("computeFingerprint - generates deterministic SHA-256 hash for quote pricing")
    void computeFingerprint_generatesDeterministicHash() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 10L);
        variant.setProduct(product);
        variant.setPrice(BigDecimal.valueOf(100));

        CartItem item = new CartItem();
        item.setVariantId(10L);
        item.setQuantity(2);

        VariantPricing pricing = new VariantPricing(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(80),
                PriceSource.BASE,
                null, null, null, 5);

        CheckoutServiceImpl.QuoteLine line = new CheckoutServiceImpl.QuoteLine(item, variant, pricing);

        Coupon coupon = new Coupon();
        ReflectionTestUtils.setField(coupon, "id", 100L);
        coupon.setCode("DISCOUNT20");
        coupon.setType(CouponType.FIXED_AMOUNT);
        coupon.setValue(BigDecimal.valueOf(20));
        coupon.setStatus(CouponStatus.ACTIVE);

        String fingerprint1 = fingerprintService.computeFingerprint(
                List.of(line), coupon, BigDecimal.valueOf(160), BigDecimal.valueOf(15), BigDecimal.valueOf(20), BigDecimal.valueOf(155));
        String fingerprint2 = fingerprintService.computeFingerprint(
                List.of(line), coupon, BigDecimal.valueOf(160), BigDecimal.valueOf(15), BigDecimal.valueOf(20), BigDecimal.valueOf(155));

        assertThat(fingerprint1).isNotBlank();
        assertThat(fingerprint1).isEqualTo(fingerprint2);
    }

    @Test
    @DisplayName("computeRequestHash - generates deterministic SHA-256 hash for checkout request")
    void computeRequestHash_generatesDeterministicHash() {
        CheckoutRequest request1 = new CheckoutRequest(
                "Receiver", "0123456789", "Address", "COD", BigDecimal.valueOf(15), "COUPON", "fingerprint-abc");
        CheckoutRequest request2 = new CheckoutRequest(
                "Receiver", "0123456789", "Address", "COD", BigDecimal.valueOf(15), "COUPON", "fingerprint-abc");

        String hash1 = fingerprintService.computeRequestHash(request1);
        String hash2 = fingerprintService.computeRequestHash(request2);

        assertThat(hash1).isNotBlank();
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("sha256 - converts string into hex SHA-256 hash")
    void sha256_convertsValueToHexHash() {
        String input = "test-value";
        String hash = fingerprintService.sha256(input);

        assertThat(hash).hasSize(64); // 256 bits = 64 hex chars
    }
}
