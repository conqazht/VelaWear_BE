package vn.conganh.commercial.feature.payment.sepay;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments/sepay")
public class SePayController {

    private final SePayService sePayService;

    @PostMapping("/ipn")
    public ResponseEntity<Map<String, Object>> ipn(
            @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
            @RequestBody SePayIpnRequest request) {
        if (!sePayService.hasValidSecret(secretKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
        }
        sePayService.handleIpn(request);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
