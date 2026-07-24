package vn.conganh.commercial.feature.salecampaign;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignFilterRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(
        statements = "TRUNCATE TABLE sale_campaign_items, sale_campaign_translations, sale_campaigns, product_images, product_variants, products, users RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class SaleCampaignQueryPerformanceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SaleCampaignService saleCampaignService;

    @Autowired
    private SaleCampaignRepository campaignRepository;

    @Autowired
    private SaleCampaignItemRepository itemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionFactory sessionFactory;

    private User testUser;
    private Instant now;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("perf-campaign@example.com");
        testUser.setFullName("Campaign Perf Admin");
        testUser.setPassword("password123");
        testUser.setBirthDate(java.time.LocalDate.of(1990, 1, 1));
        testUser.setGender(UserGender.OTHER);
        testUser = userRepository.save(testUser);
        now = Instant.now();
    }

    private Product createProduct(String name) {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setBrandId(1L);
        product.setName(name);
        product.setSlug(name.toLowerCase().replace(" ", "-"));
        product.setDescription("Test product: " + name);
        product.setStatus("ACTIVE");
        return productRepository.save(product);
    }

    private ProductVariant createVariant(Product product, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setPrice(BigDecimal.valueOf(100));
        variant.setStockQuantity(50);
        variant.setStatus("ACTIVE");
        return productVariantRepository.save(variant);
    }

    private SaleCampaign createCampaign(String code, List<ProductVariant> variants) {
        SaleCampaign campaign = new SaleCampaign();
        campaign.setCode(code);
        campaign.setName("Campaign " + code);
        campaign.setDescription("Desc " + code);
        campaign.setType(SaleCampaignType.STANDARD);
        campaign.setStatus(SaleCampaignStatus.DRAFT);
        campaign.setStartsAt(now.plus(1, ChronoUnit.DAYS));
        campaign.setEndsAt(now.plus(2, ChronoUnit.DAYS));
        campaign.setCreatedBy(testUser);
        
        List<SaleCampaignItem> items = new ArrayList<>();
        for (ProductVariant variant : variants) {
            SaleCampaignItem item = new SaleCampaignItem();
            item.setVariant(variant);
            item.setReferencePrice(variant.getPrice());
            item.setPromotionalPrice(BigDecimal.valueOf(90));
            items.add(item);
        }
        campaign.replaceItems(items);
        return campaignRepository.saveAndFlush(campaign);
    }

    @Test
    @DisplayName("Query count should be constant when fetching a page of campaigns with 1 vs 5 items")
    void getAll_queryCount_constantAcrossItemCount() {
        // Create products and variants
        List<ProductVariant> allVariants = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Product product = createProduct("Product " + i);
            allVariants.add(createVariant(product, "SKU-" + i));
        }

        // Create campaign with 1 item
        SaleCampaign singleItemCampaign = createCampaign("CAMP-SINGLE", List.of(allVariants.getFirst()));
        
        // Create campaign with 5 items
        SaleCampaign fiveItemCampaign = createCampaign("CAMP-MULTI", allVariants);

        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        
        // Fetch page with only single item campaign
        stats.clear();
        saleCampaignService.getAll(
            new SaleCampaignFilterRequest("CAMP-SINGLE", null, null, null), 
            PageRequest.of(0, 10), 
            CatalogLocaleResolver.DEFAULT_LOCALE);
        long singleItemQueries = stats.getQueryExecutionCount();

        // Fetch page with only five item campaign
        stats.clear();
        saleCampaignService.getAll(
            new SaleCampaignFilterRequest("CAMP-MULTI", null, null, null), 
            PageRequest.of(0, 10), 
            CatalogLocaleResolver.DEFAULT_LOCALE);
        long fiveItemQueries = stats.getQueryExecutionCount();

        // The query difference should be extremely small (only affected by the number of returned records, not N+1)
        long queryDifference = Math.abs(fiveItemQueries - singleItemQueries);
        assertTrue(queryDifference <= 2, 
            "Query growth between 1 item and 5 items in a campaign should be bounded. " + 
            "Single-item queries: " + singleItemQueries + ", five-item queries: " + fiveItemQueries);
    }

    @Test
    @DisplayName("Overlap validation query count should be constant for 1 item vs 5 items")
    void publish_overlapQueryCount_constantAcrossItemCount() {
        List<ProductVariant> allVariants = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Product product = createProduct("Publish Product " + i);
            allVariants.add(createVariant(product, "PUB-SKU-" + i));
        }

        SaleCampaign singleItemCampaign = createCampaign("PUB-SINGLE", List.of(allVariants.getFirst()));
        SaleCampaign fiveItemCampaign = createCampaign("PUB-MULTI", allVariants);

        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);

        // Publish single item campaign
        stats.clear();
        saleCampaignService.publish(singleItemCampaign.getId(), singleItemCampaign.getVersion(), testUser.getEmail());
        long singleItemQueries = stats.getQueryExecutionCount();

        // Cancel it so it doesn't overlap
        saleCampaignService.cancel(singleItemCampaign.getId(), singleItemCampaign.getVersion() + 1);

        // Publish five item campaign
        stats.clear();
        saleCampaignService.publish(fiveItemCampaign.getId(), fiveItemCampaign.getVersion(), testUser.getEmail());
        long fiveItemQueries = stats.getQueryExecutionCount();

        long queryDifference = Math.abs(fiveItemQueries - singleItemQueries);
        assertTrue(queryDifference <= 4,
            "Query growth for publishing 1 item vs 5 items should be bounded. " +
            "Single-item queries: " + singleItemQueries + ", five-item queries: " + fiveItemQueries);
    }
}
