package me.theforbiddenai.gamefinder.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

/**
 * Holds information about a free game listing
 *
 * @author TheForbiddenAi
 */
@Data
@Builder
public class Game {

    private String title;
    private String description;
    private String url;
    private boolean isDLC;

    @Setter
    private String originalPrice;

    @Builder.Default
    private Platform platform = Platform.UNDEFINED;

    // storeImages are comprised of thumbnails, header images, and capsule images
    @Builder.Default
    private Map<String, String> storeMedia = new HashMap<>();

    // media includes screenshots
    @Builder.Default
    private List<String> media = new ArrayList<>();

    @Builder.Default
    private Long expirationEpoch = GameFinderConstants.NO_EXPIRATION_EPOCH;

    /**
     * Formats a price into currency & locale specific currency format
     *
     * @param originalPrice The double being formatted
     * @param currencyCode  The currencyCode of the currency
     * @return The formatted price
     */
    private static String _setOriginalPrice(double originalPrice, String currencyCode) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(GameFinderConfiguration.getInstance().getLocale());
        Currency currency = Currency.getInstance(currencyCode);

        numberFormat.setCurrency(currency);

        return numberFormat.format(originalPrice);
    }

    public void setOriginalPrice(double originalPrice, String currencyCode) {
        this.originalPrice = _setOriginalPrice(originalPrice, currencyCode);
    }

    public static class GameBuilder {

        private String originalPrice = "N/A";

        public GameBuilder originalPrice(double originalPrice, String currencyCode) {
            this.originalPrice = _setOriginalPrice(originalPrice, currencyCode);
            return this;
        }

        public GameBuilder originalPrice(double originalPrice) {
            Currency currency = Currency.getInstance(GameFinderConfiguration.getInstance().getLocale());
            this.originalPrice = _setOriginalPrice(originalPrice, currency.getCurrencyCode());
            return this;
        }

        public GameBuilder originalPrice(String originalPrice) {
            this.originalPrice = originalPrice;
            return this;
        }

        public GameBuilder originalPrice(int priceNoDecimal, int decimalCount) {
            BigDecimal unscaled = new BigDecimal(priceNoDecimal);
            double priceWithDecimal = unscaled.scaleByPowerOfTen(-decimalCount).doubleValue();

            return originalPrice(priceWithDecimal);
        }
    }

}
