package vn.conganh.commercial.feature.review;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.review.dto.PublicReviewResponse;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;

@Component
@RequiredArgsConstructor
public class ReviewResponseAssembler {

    private final ProductVariantRepository productVariantRepository;
    private final ReviewImageRepository reviewImageRepository;

    public ResultPaginationDTO toAdminPage(Page<Review> reviews) {
        BatchContext context = loadContext(reviews.getContent());
        return ResultPaginationDTO.fromPage(reviews.map(review -> toAdminResponse(review, context)));
    }

    public ResultPaginationDTO toPublicPage(Page<Review> reviews) {
        BatchContext context = loadContext(reviews.getContent());
        return ResultPaginationDTO.fromPage(reviews.map(review -> toPublicResponse(review, context)));
    }

    public ReviewResponse toAdminResponse(Review review) {
        return toAdminResponse(review, loadContext(List.of(review)));
    }

    private ReviewResponse toAdminResponse(Review review, BatchContext context) {
        return ReviewResponse.fromEntity(
                review,
                context.variantsById().get(review.getOrderItem().getVariantId()),
                context.imageUrlsByReviewId().getOrDefault(review.getId(), List.of()));
    }

    private PublicReviewResponse toPublicResponse(Review review, BatchContext context) {
        return PublicReviewResponse.fromEntity(
                review,
                context.variantsById().get(review.getOrderItem().getVariantId()),
                context.imageUrlsByReviewId().getOrDefault(review.getId(), List.of()));
    }

    private BatchContext loadContext(List<Review> reviews) {
        List<Long> variantIds = reviews.stream()
                .map(Review::getOrderItem)
                .map(OrderItem::getVariantId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, ProductVariant> variantsById = variantIds.isEmpty()
                ? Map.of()
                : productVariantRepository.findAllByIdInAndDeletedAtIsNull(variantIds).stream()
                        .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, List<String>> imageUrlsByReviewId = reviewIds.isEmpty()
                ? Map.of()
                : reviewImageRepository.findAllByReviewIdInOrderByIdAsc(reviewIds).stream()
                        .collect(Collectors.groupingBy(
                                image -> image.getReview().getId(),
                                Collectors.mapping(ReviewImage::getImage, Collectors.toList())));
        return new BatchContext(variantsById, imageUrlsByReviewId);
    }

    private record BatchContext(
            Map<Long, ProductVariant> variantsById,
            Map<Long, List<String>> imageUrlsByReviewId) {
    }
}
