package vn.conganh.commercial.feature.dashboard.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentReviewSummary {
    private Long id;
    private String customerName;
    private String productName;
    private Integer rating;
    private String comment;
    private Instant createdAt;
}
