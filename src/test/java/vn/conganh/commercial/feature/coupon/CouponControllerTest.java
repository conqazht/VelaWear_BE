package vn.conganh.commercial.feature.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.coupon.dto.CreateCouponRequest;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

@Transactional
@DisplayName("Module Coupon - CouponController")
class CouponControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private CouponRepository couponRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /coupons - 201: tạo coupon thành công khi dữ liệu hợp lệ")
        void createCoupon_validRequest_returnsCreatedCoupon() throws Exception {
            // Arrange
            CreateCouponRequest request = validRequest("COUPON_POST_10");

            // Act & Assert
            mockMvc.perform(post("/api/v1/coupons")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.code", is("COUPON_POST_10")))
                    .andExpect(jsonPath("$.data.status", is("ACTIVE")));

            assertThat(couponRepository.existsByCode("COUPON_POST_10")).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /coupons - 400: từ chối khi code để trống")
        void createCoupon_blankCode_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateCouponRequest request = validRequest("");

            // Act & Assert
            mockMvc.perform(post("/api/v1/coupons")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(couponRepository.findAll()).noneMatch(coupon -> coupon.getCode().isBlank());
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /coupons - 400: từ chối khi code đã tồn tại")
        void createCoupon_duplicateCode_returnsBadRequestAndDoesNotCreateNewCoupon() throws Exception {
            // Arrange
            couponRepository.save(coupon("COUPON_DUPLICATE_POST"));
            CreateCouponRequest request = validRequest("COUPON_DUPLICATE_POST");
            long countBefore = couponRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/coupons")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(couponRepository.count()).isEqualTo(countBefore);
        }
    }

    private CreateCouponRequest validRequest(String code) {
        return new CreateCouponRequest(
                code,
                CouponType.PERCENTAGE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100000),
                10,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                CouponStatus.ACTIVE);
    }

    private Coupon coupon(String code) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setType(CouponType.PERCENTAGE);
        coupon.setValue(BigDecimal.TEN);
        coupon.setMinOrderAmount(BigDecimal.ZERO);
        coupon.setMaxDiscount(BigDecimal.valueOf(100000));
        coupon.setUsageLimit(10);
        coupon.setStartDate(Instant.parse("2026-01-01T00:00:00Z"));
        coupon.setEndDate(Instant.parse("2026-12-31T00:00:00Z"));
        coupon.setStatus(CouponStatus.ACTIVE);
        return coupon;
    }
}
