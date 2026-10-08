package vn.conganh.commercial.util.constant;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public enum CustomerTier {

    STANDARD("Thành viên", 0, BigDecimal.ZERO),
    SILVER("Bạc", 1, new BigDecimal("2000000")),
    GOLD("Vàng", 2, new BigDecimal("5000000")),
    DIAMOND("Kim Cương", 3, new BigDecimal("10000000"));

    private final String label;
    private final int rank;
    private final BigDecimal minSpend;

    CustomerTier(String label, int rank, BigDecimal minSpend) {
        this.label = label;
        this.rank = rank;
        this.minSpend = minSpend;
    }

    public static CustomerTier fromSpend(BigDecimal spend) {
        if (spend == null || spend.compareTo(BigDecimal.ZERO) <= 0) {
            return STANDARD;
        }
        if (spend.compareTo(DIAMOND.minSpend) >= 0) {
            return DIAMOND;
        }
        if (spend.compareTo(GOLD.minSpend) >= 0) {
            return GOLD;
        }
        if (spend.compareTo(SILVER.minSpend) >= 0) {
            return SILVER;
        }
        return STANDARD;
    }

    public CustomerTier getNextTier() {
        return switch (this) {
            case STANDARD -> SILVER;
            case SILVER -> GOLD;
            case GOLD -> DIAMOND;
            case DIAMOND -> null;
        };
    }

    public BigDecimal getAmountToNextTier(BigDecimal currentSpend) {
        CustomerTier next = getNextTier();
        if (next == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal spend = currentSpend == null ? BigDecimal.ZERO : currentSpend;
        BigDecimal diff = next.getMinSpend().subtract(spend);
        return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
    }
}
