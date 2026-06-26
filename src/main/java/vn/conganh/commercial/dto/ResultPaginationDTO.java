package vn.conganh.commercial.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ResultPaginationDTO(
        Meta meta,
        List<?> result
) {

    public static ResultPaginationDTO fromPage(Page<?> page) {
        Meta meta = new Meta(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements());
        return new ResultPaginationDTO(meta, page.getContent());
    }

    public record Meta(
            int page,
            int pageSize,
            int pages,
            long total
    ) {}
}
