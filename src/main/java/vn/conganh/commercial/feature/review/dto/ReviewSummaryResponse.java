package vn.conganh.commercial.feature.review.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.conganh.commercial.feature.review.ReviewRatingCount;

public record ReviewSummaryResponse(
        long total,
        double averageRating,
        Map<Integer, Long> ratingCounts
) {

    public ReviewSummaryResponse {
        ratingCounts = Collections.unmodifiableMap(new LinkedHashMap<>(ratingCounts));
    }

    public static ReviewSummaryResponse fromCounts(List<ReviewRatingCount> counts) {
        Map<Integer, Long> ratingCounts = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            ratingCounts.put(rating, 0L);
        }

        long total = 0;
        long weightedTotal = 0;
        for (ReviewRatingCount count : counts) {
            int rating = count.getRating().intValue();
            long amount = count.getTotal();
            ratingCounts.put(rating, amount);
            total += amount;
            weightedTotal += (long) rating * amount;
        }

        double average = total == 0
                ? 0.0
                : BigDecimal.valueOf(weightedTotal)
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                        .doubleValue();
        return new ReviewSummaryResponse(total, average, ratingCounts);
    }
}
