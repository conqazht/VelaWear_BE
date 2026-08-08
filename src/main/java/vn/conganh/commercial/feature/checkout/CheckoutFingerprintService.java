package vn.conganh.commercial.feature.checkout;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.checkout.dto.CheckoutRequest;
import vn.conganh.commercial.feature.coupon.Coupon;

@Component
public class CheckoutFingerprintService {

    public String computeFingerprint(
            List<CheckoutServiceImpl.QuoteLine> lines,
            Coupon coupon,
            BigDecimal eligibleSubtotal,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal finalAmount) {
        String canonical = lines.stream()
                .sorted(Comparator.comparing(line -> line.variant().getId()))
                .map(line -> String.join(":",
                        line.variant().getId().toString(),
                        Integer.toString(line.item().getQuantity()),
                        line.pricing().listPrice().toPlainString(),
                        line.pricing().effectivePrice().toPlainString(),
                        line.pricing().priceSource().name(),
                        line.pricing().campaignItem() == null ? "-" : line.pricing().campaignItem().getId().toString()))
                .collect(Collectors.joining("|"));
        String couponSnapshot = coupon == null
                ? "-"
                : String.join(":",
                        coupon.getId().toString(),
                        coupon.getCode(),
                        coupon.getType().name(),
                        coupon.getValue().toPlainString(),
                        coupon.getMaxDiscount() == null ? "-" : coupon.getMaxDiscount().toPlainString(),
                        coupon.getMinOrderAmount() == null ? "-" : coupon.getMinOrderAmount().toPlainString());
        return sha256(canonical
                + "|coupon=" + couponSnapshot
                + "|eligible=" + eligibleSubtotal.toPlainString()
                + "|shipping=" + shippingFee.toPlainString()
                + "|discount=" + discountAmount.toPlainString()
                + "|final=" + finalAmount.toPlainString());
    }

    public String computeRequestHash(CheckoutRequest request) {
        return sha256(String.join("|",
                nullSafe(request.receiverName()),
                nullSafe(request.receiverPhone()),
                nullSafe(request.receiverAddress()),
                nullSafe(request.paymentMethod()).toUpperCase(),
                nullSafe(request.couponCode()),
                nullSafe(request.pricingFingerprint())));
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
