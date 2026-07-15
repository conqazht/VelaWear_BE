package vn.conganh.commercial.feature.salecampaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sale_campaign_translations")
@IdClass(SaleCampaignTranslationId.class)
public class SaleCampaignTranslation {

    @Id
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Id
    @Column(name = "locale_code", nullable = false, length = 10)
    private String localeCode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;
}
