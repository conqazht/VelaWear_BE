package vn.conganh.commercial.feature.storefrontcatalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.color.Color;
import vn.conganh.commercial.feature.size.Size;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogEvaluation.Candidate;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Item;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Offer;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

@Component
class StorefrontCatalogFilterEngine {

    StorefrontCatalogEvaluation evaluate(
            StorefrontCatalogSnapshot snapshot,
            StorefrontProductQuery query) {
        Criteria criteria = Criteria.from(query);
        List<Item> searchMatches = snapshot.items().stream()
                .filter(item -> matchesSearch(item, criteria.search()))
                .toList();
        StorefrontCatalogResponse.Facets facets = buildFacets(searchMatches, criteria);
        List<Candidate> candidates = buildCandidates(searchMatches, criteria);
        return new StorefrontCatalogEvaluation(candidates.stream()
                .sorted(comparator(criteria.sort()))
                .toList(), facets);
    }

    private List<Candidate> buildCandidates(List<Item> items, Criteria criteria) {
        List<Candidate> candidates = new ArrayList<>();
        for (Item item : items) {
            if (!matchesCategory(item, criteria.categorySlugs())) {
                continue;
            }
            List<Offer> matchingOffers = matchingOffers(item, criteria, true, true, true);
            if (matchingOffers.isEmpty()) {
                continue;
            }
            Offer representative = matchingOffers.stream()
                    .min(Comparator.comparing((Offer offer) -> offer.pricing().effectivePrice())
                            .thenComparing(offer -> offer.variant().getId()))
                    .orElseThrow();
            int priority = matchingOffers.stream().mapToInt(o -> o.pricing().salePriority()).max().orElse(0);
            BigDecimal discountRate = matchingOffers.stream()
                    .map(o -> o.pricing().discountRate())
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            candidates.add(new Candidate(item, matchingOffers, representative, priority, discountRate));
        }
        return List.copyOf(candidates);
    }

    private StorefrontCatalogResponse.Facets buildFacets(List<Item> items, Criteria criteria) {
        return new StorefrontCatalogResponse.Facets(
                categoryFacets(items, criteria),
                colorFacets(items, criteria),
                sizeFacets(items, criteria),
                priceRange(items, criteria));
    }

    private List<StorefrontCatalogResponse.CategoryFacet> categoryFacets(
            List<Item> items,
            Criteria criteria) {
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Item> options = new LinkedHashMap<>();
        for (Item item : items) {
            options.putIfAbsent(item.category().getId(), item);
            if (!matchingOffers(item, criteria, true, true, true).isEmpty()) {
                counts.merge(item.category().getId(), 1L, Long::sum);
            }
        }
        return options.values().stream()
                .sorted(Comparator.comparingInt((Item item) -> item.category().getSortOrder())
                        .thenComparing(Item::categoryName, String.CASE_INSENSITIVE_ORDER))
                .map(item -> new StorefrontCatalogResponse.CategoryFacet(
                        item.category().getId(),
                        item.categoryName(),
                        item.category().getSlug(),
                        counts.getOrDefault(item.category().getId(), 0L)))
                .toList();
    }

    private List<StorefrontCatalogResponse.ColorFacet> colorFacets(List<Item> items, Criteria criteria) {
        Map<Long, Color> options = new LinkedHashMap<>();
        Map<Long, Long> counts = new HashMap<>();
        for (Item item : items) {
            for (Offer offer : item.offers()) {
                if (offer.variant().getColor() != null) {
                    options.putIfAbsent(offer.variant().getColor().getId(), offer.variant().getColor());
                }
            }
            if (!matchesCategory(item, criteria.categorySlugs())) {
                continue;
            }
            Set<Long> matchingIds = matchingOffers(item, criteria, false, true, true).stream()
                    .filter(offer -> offer.variant().getColor() != null)
                    .map(offer -> offer.variant().getColor().getId())
                    .collect(java.util.stream.Collectors.toSet());
            for (Long colorId : matchingIds) {
                counts.merge(colorId, 1L, Long::sum);
            }
        }
        return options.values().stream()
                .sorted(Comparator.comparing(Color::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Color::getName, String.CASE_INSENSITIVE_ORDER))
                .map(color -> new StorefrontCatalogResponse.ColorFacet(
                        color.getId(),
                        color.getName(),
                        color.getHexCode(),
                        color.getSortOrder(),
                        counts.getOrDefault(color.getId(), 0L)))
                .toList();
    }

    private List<StorefrontCatalogResponse.SizeFacet> sizeFacets(List<Item> items, Criteria criteria) {
        Map<Long, Size> options = new LinkedHashMap<>();
        Map<Long, Long> counts = new HashMap<>();
        for (Item item : items) {
            for (Offer offer : item.offers()) {
                if (offer.variant().getSize() != null) {
                    options.putIfAbsent(offer.variant().getSize().getId(), offer.variant().getSize());
                }
            }
            if (!matchesCategory(item, criteria.categorySlugs())) {
                continue;
            }
            Set<Long> matchingIds = matchingOffers(item, criteria, true, false, true).stream()
                    .filter(offer -> offer.variant().getSize() != null)
                    .map(offer -> offer.variant().getSize().getId())
                    .collect(java.util.stream.Collectors.toSet());
            for (Long sizeId : matchingIds) {
                counts.merge(sizeId, 1L, Long::sum);
            }
        }
        return options.values().stream()
                .sorted(Comparator.comparing(Size::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Size::getName, String.CASE_INSENSITIVE_ORDER))
                .map(size -> new StorefrontCatalogResponse.SizeFacet(
                        size.getId(),
                        size.getName(),
                        size.getSortOrder(),
                        counts.getOrDefault(size.getId(), 0L)))
                .toList();
    }

    private StorefrontCatalogResponse.PriceRange priceRange(List<Item> items, Criteria criteria) {
        List<BigDecimal> prices = new ArrayList<>();
        for (Item item : items) {
            if (!matchesCategory(item, criteria.categorySlugs())) {
                continue;
            }
            for (Offer offer : matchingOffers(item, criteria, true, true, false)) {
                prices.add(offer.pricing().effectivePrice());
            }
        }
        BigDecimal minimum = prices.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal maximum = prices.stream().max(BigDecimal::compareTo).orElse(null);
        return new StorefrontCatalogResponse.PriceRange(minimum, maximum);
    }

    private List<Offer> matchingOffers(
            Item item,
            Criteria criteria,
            boolean applyColor,
            boolean applySize,
            boolean applyPrice) {
        return item.offers().stream()
                .filter(offer -> !applyColor || criteria.colorIds().isEmpty()
                        || offer.variant().getColor() != null
                        && criteria.colorIds().contains(offer.variant().getColor().getId()))
                .filter(offer -> !applySize || criteria.sizeIds().isEmpty()
                        || offer.variant().getSize() != null
                        && criteria.sizeIds().contains(offer.variant().getSize().getId()))
                .filter(offer -> !applyPrice || priceMatches(offer, criteria.minimumPrice(), criteria.maximumPrice()))
                .toList();
    }

    private boolean priceMatches(Offer offer, BigDecimal minimum, BigDecimal maximum) {
        BigDecimal price = offer.pricing().effectivePrice();
        boolean aboveMinimum = minimum == null || price.compareTo(minimum) >= 0;
        boolean belowMaximum = maximum == null || price.compareTo(maximum) <= 0;
        return aboveMinimum && belowMaximum;
    }

    private boolean matchesCategory(Item item, Set<String> slugs) {
        return slugs.isEmpty() || slugs.contains(item.category().getSlug().toLowerCase(Locale.ROOT));
    }

    private boolean matchesSearch(Item item, String search) {
        if (search == null) {
            return true;
        }
        List<String> values = List.of(
                value(item.translation().getName()),
                value(item.translation().getSlug()),
                value(item.translation().getShortDescription()),
                value(item.translation().getDescription()),
                value(item.translation().getMaterial()),
                value(item.product().getName()),
                value(item.product().getSlug()),
                value(item.categoryName()));
        return values.stream().anyMatch(value -> value.contains(search));
    }

    private String value(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }


    private Comparator<Candidate> comparator(StorefrontProductSort sort) {
        Comparator<Candidate> newest = Comparator
                .comparing(
                        (Candidate candidate) -> candidate.item().product().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> candidate.item().product().getId(), Comparator.reverseOrder());
        return switch (sort) {
            case NEWEST -> newest;
            case PRICE_ASC -> priceComparator(false);
            case PRICE_DESC -> priceComparator(true);
            case FEATURED -> Comparator.comparingInt(Candidate::salePriority).reversed()
                    .thenComparing(Candidate::discountRate, Comparator.reverseOrder())
                    .thenComparing(
                            candidate -> candidate.item().reviewScore().averageRating(),
                            Comparator.reverseOrder())
                    .thenComparing(
                            candidate -> candidate.item().reviewScore().reviewCount(),
                            Comparator.reverseOrder())
                    .thenComparing(newest);
        };
    }

    private Comparator<Candidate> priceComparator(boolean descending) {
        Comparator<BigDecimal> direction = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(
                        (Candidate candidate) -> candidate.representative().pricing().effectivePrice(),
                        direction)
                .thenComparing(candidate -> candidate.item().product().getId(), Comparator.reverseOrder());
    }

    private record Criteria(
            String search,
            Set<String> categorySlugs,
            Set<Long> colorIds,
            Set<Long> sizeIds,
            BigDecimal minimumPrice,
            BigDecimal maximumPrice,
            StorefrontProductSort sort
    ) {
        static Criteria from(StorefrontProductQuery query) {
            String search = query.q() == null || query.q().isBlank()
                    ? null
                    : query.q().trim().toLowerCase(Locale.ROOT);
            Set<String> categories = new LinkedHashSet<>();
            if (query.categorySlugs() != null) {
                for (String slug : query.categorySlugs()) {
                    categories.add(slug.trim().toLowerCase(Locale.ROOT));
                }
            }
            return new Criteria(
                    search,
                    Set.copyOf(categories),
                    query.colorIds() == null ? Set.of() : Set.copyOf(query.colorIds()),
                    query.sizeIds() == null ? Set.of() : Set.copyOf(query.sizeIds()),
                    query.minPrice(),
                    query.maxPrice(),
                    StorefrontProductSort.from(query.sort()));
        }
    }
}
