package vn.conganh.commercial.feature.salecampaign;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SaleCampaignTranslationId implements Serializable {

    private Long campaignId;

    private String localeCode;
}
