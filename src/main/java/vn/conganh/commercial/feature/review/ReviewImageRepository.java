package vn.conganh.commercial.feature.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findAllByReviewIdInOrderByIdAsc(List<Long> reviewIds);

    @Query("select image.image from ReviewImage image")
    List<String> findAllImagePaths();
}
