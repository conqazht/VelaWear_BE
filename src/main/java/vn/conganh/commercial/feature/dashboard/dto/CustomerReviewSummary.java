package vn.conganh.commercial.feature.dashboard.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerReviewSummary {
    private Double averageRating;
    private Long totalReviews;
    private List<RecentReviewSummary> recentReviews;
}
