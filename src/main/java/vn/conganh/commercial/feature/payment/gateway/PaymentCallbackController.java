package vn.conganh.commercial.feature.payment.gateway;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.payment.config.PaymentEnvironmentProperties;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Callbacks & Simulation", description = "Endpoints for provider webhooks, return callbacks, and dev simulations")
public class PaymentCallbackController {

    private final PaymentGatewayRouter paymentGatewayRouter;
    private final PaymentCallbackService paymentCallbackService;
    private final PaymentEnvironmentProperties environmentProperties;

    // ==========================================
    // 1. VNPay Endpoints
    // ==========================================

    @GetMapping("/vnpay/callback")
    @Operation(summary = "VNPay customer return URL callback")
    public ResponseEntity<ApiResponse<PaymentCallbackResult>> handleVnpayCallback(
            @RequestParam Map<String, String> params) {
        log.info("event=vnpay_callback params={}", params);
        PaymentGateway gateway = paymentGatewayRouter.getGateway(PaymentProvider.VNPAY);
        PaymentCallbackResult result = gateway.verifyCallback(params, params.toString(), params.get("vnp_SecureHash"));
        PaymentCallbackResult processed = paymentCallbackService.processCallback(result);
        return ResponseEntity.ok(ApiResponse.success(processed));
    }

    @RequestMapping(value = "/vnpay/ipn")
    @Operation(summary = "VNPay server-to-server IPN notification")
    public ResponseEntity<Map<String, String>> handleVnpayIpn(
            @RequestParam Map<String, String> params) {
        log.info("event=vnpay_ipn params={}", params);
        PaymentGateway gateway = paymentGatewayRouter.getGateway(PaymentProvider.VNPAY);
        PaymentCallbackResult result = gateway.verifyCallback(params, params.toString(), params.get("vnp_SecureHash"));

        if (!"97".equals(result.responseCode())) {
            paymentCallbackService.processCallback(result);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
        } else {
            log.warn("event=vnpay_ipn outcome=invalid_checksum");
            return ResponseEntity.ok(Map.of("RspCode", "97", "Message", "Invalid Checksum"));
        }
    }

    // ==========================================
    // 2. MoMo Endpoints
    // ==========================================

    @GetMapping("/momo/callback")
    @Operation(summary = "MoMo customer return URL callback")
    public ResponseEntity<ApiResponse<PaymentCallbackResult>> handleMomoCallback(
            @RequestParam Map<String, String> params) {
        log.info("event=momo_callback params={}", params);
        PaymentGateway gateway = paymentGatewayRouter.getGateway(PaymentProvider.MOMO);
        PaymentCallbackResult result = gateway.verifyCallback(params, params.toString(), params.get("signature"));
        PaymentCallbackResult processed = paymentCallbackService.processCallback(result);
        return ResponseEntity.ok(ApiResponse.success(processed));
    }

    @PostMapping("/momo/ipn")
    @Operation(summary = "MoMo server-to-server IPN notification")
    public ResponseEntity<Map<String, Object>> handleMomoIpn(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Map<String, String> queryParams) {
        Map<String, String> params = new HashMap<>();
        if (queryParams != null) {
            params.putAll(queryParams);
        }
        String rawBody = "";
        if (body != null) {
            body.forEach((k, v) -> params.put(k, String.valueOf(v)));
            rawBody = body.toString();
        }

        log.info("event=momo_ipn params={}", params);
        PaymentGateway gateway = paymentGatewayRouter.getGateway(PaymentProvider.MOMO);
        PaymentCallbackResult result = gateway.verifyCallback(params, rawBody, params.get("signature"));
        paymentCallbackService.processCallback(result);

        return ResponseEntity.ok(Map.of("resultCode", 0, "message", "Acknowledge"));
    }

    // ==========================================
    // 3. Stripe Endpoints
    // ==========================================

    @PostMapping("/stripe/webhook")
    @Operation(summary = "Stripe webhook notification")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader,
            @RequestBody String payload) {
        log.info("event=stripe_webhook sig_present={}", signatureHeader != null);
        PaymentGateway gateway = paymentGatewayRouter.getGateway(PaymentProvider.STRIPE);
        PaymentCallbackResult result = gateway.verifyCallback(Collections.emptyMap(), payload, signatureHeader);

        if (result.success()) {
            paymentCallbackService.processCallback(result);
            return ResponseEntity.ok(Map.of("received", true));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", result.message()));
        }
    }

    // ==========================================
    // 4. Dev Simulation Endpoints
    // ==========================================

    @PostMapping("/simulate/success")
    @Operation(summary = "Simulate payment success (dev/sandbox only)")
    public ResponseEntity<ApiResponse<PaymentCallbackResult>> simulateSuccess(
            @RequestBody @Valid SimulatePaymentRequest request) {
        assertSimulationEnabled();

        String txCode = request.transactionCode() != null && !request.transactionCode().isBlank()
                ? request.transactionCode()
                : "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentCallbackResult simResult = PaymentCallbackResult.builder()
                .success(true)
                .provider(PaymentProvider.VNPAY)
                .orderCode(request.orderCode())
                .transactionCode(txCode)
                .amount(request.amount())
                .message("Simulated payment success")
                .responseCode("00")
                .rawResponse("{\"simulated\": true, \"status\": \"SUCCESS\"}")
                .build();

        PaymentCallbackResult processed = paymentCallbackService.processCallback(simResult);
        return ResponseEntity.ok(ApiResponse.success(processed));
    }

    @PostMapping("/simulate/failed")
    @Operation(summary = "Simulate payment failure (dev/sandbox only)")
    public ResponseEntity<ApiResponse<PaymentCallbackResult>> simulateFailure(
            @RequestBody @Valid SimulatePaymentRequest request) {
        assertSimulationEnabled();

        String txCode = request.transactionCode() != null && !request.transactionCode().isBlank()
                ? request.transactionCode()
                : "SIM-FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentCallbackResult simResult = PaymentCallbackResult.builder()
                .success(false)
                .provider(PaymentProvider.VNPAY)
                .orderCode(request.orderCode())
                .transactionCode(txCode)
                .amount(request.amount())
                .message("Simulated payment failure")
                .responseCode("99")
                .rawResponse("{\"simulated\": true, \"status\": \"FAILED\"}")
                .build();

        PaymentCallbackResult processed = paymentCallbackService.processCallback(simResult);
        return ResponseEntity.ok(ApiResponse.success(processed));
    }

    private void assertSimulationEnabled() {
        if (environmentProperties.getSimulation() == null
                || !environmentProperties.getSimulation().isEnabled()) {
            throw new InvalidRequestException("Payment simulation is disabled in current environment");
        }
    }
}
