package vn.conganh.commercial.feature.membership;

import java.time.Instant;
import vn.conganh.commercial.feature.membership.dto.MembershipSummaryResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.util.constant.CustomerTier;

public interface MembershipService {

    CustomerTier getUserTier(User user, Instant now);

    MembershipSummaryResponse getMembershipSummary(User user, Instant now);
}
