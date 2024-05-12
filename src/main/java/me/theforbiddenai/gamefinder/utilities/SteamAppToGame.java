package me.theforbiddenai.gamefinder.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NonNull;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;

import java.io.IOException;
import java.util.*;

public class SteamAppToGame {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private final SteamRequests steamRequests;
    private final SteamPackageToGame steamPackageToGame;

    public SteamAppToGame(@NonNull SteamRequests steamRequests) {
        this.steamRequests = steamRequests;
        this.steamPackageToGame = new SteamPackageToGame(steamRequests);
    }

    /**
     * Create a game object for a given appId
     *
     * @param appId The appId for the listing
     * @return An optional containing the game
     * @throws IOException If there is a json parsing error or malformed URL
     */
    public Optional<Game> convertAppToGame(@NonNull String appId) throws IOException {
        Optional<JsonNode> appNodeOptional = steamRequests.getAppDetails(appId);
        // App with given id was not found
        if (appNodeOptional.isEmpty()) return Optional.empty();

        JsonNode appNode = appNodeOptional.get();

        boolean isDLC = !appNode.get("type").asText().equals("game");
        //Return null if the listing is a DLC and includeDLCs is disabled
        if (!CONFIG.includeDLCs() && isDLC) return Optional.empty();

        String url = GameFinderConstants.STEAM_STORE_URL + "app/" + appId;

        // TODO: Expiration Epoch
        Game.GameBuilder gameBuilder = Game.builder()
                .title(appNode.get("name").asText())
                .description(appNode.get("short_description").asText())
                .url(url)
                .isDLC(isDLC)
                .platform(Platform.STEAM)
                .storeMedia(getAppStoreMedia(appNode))
                .media(getAppScreenshots(appNode));

        setExpirationEpoch(appNode, appId, gameBuilder);

        return Optional.of(gameBuilder.build());
    }

    /**
     * Sets the expiration epoch for a game builder. Will also update {@link Game#isExpirationEpochEstimate} to true
     * if a clan's event's end epoch is used
     *
     * @param appNode The appId for the listing
     * @param gameBuilder The builder for the game
     */
    private void setExpirationEpoch(@NonNull JsonNode appNode, String appId, Game.GameBuilder gameBuilder) throws IOException {
        String packageId = getPackageIdWithDiscount(appNode);
        // If no packageId can be found, the discount expiration time cannot be recovered
        // I do not believe this is possible to happen unless there is no 100% off discount
        if (packageId == null || packageId.isBlank()) {
            gameBuilder.expirationEpoch(GameFinderConstants.NO_EXPIRATION_EPOCH);
            return;
        }

        long expirationEpoch = steamPackageToGame.getExpirationEpoch(packageId, appId);
        gameBuilder.expirationEpoch(expirationEpoch);
    }

    /**
     * Gets the package id that corresponds with the 100% discount
     *
     * @param appNode The jsonNode for the app
     * @return The found packageId or null if not found
     */
    private String getPackageIdWithDiscount(@NonNull JsonNode appNode) {
        // Make sure appNode has package_groups before continuing (it should always have this)
        JsonNode packageGroupsNode = appNode.get("package_groups");
        if (packageGroupsNode == null) return null;

        for (JsonNode groupNode : packageGroupsNode) {
            // Continue to next element if there is no subs node
            JsonNode subsNode = groupNode.get("subs");
            if (subsNode == null) continue;

            // Loop through sub nodes (these house the buying options for a game; i.e. starter edition, premium, etc.)
            for (JsonNode subNode : subsNode) {
                // Get price in cents; if not found return -1
                int priceInCentsWithDiscount = Optional.of(subNode.get("price_in_cents_with_discount"))
                        .map(JsonNode::asInt)
                        .orElse(-1);

                if (priceInCentsWithDiscount == 0) {
                    // Package with 100% off discount has been found; return packageid
                    return subNode.get("packageid").asText();
                }

            }
        }

        return null;
    }

    /**
     * Gets header, capsule, and capsulev5 images, along with all movies.
     *
     * @param appNode The jsonNode for the app
     * @return A map containing the header, capsule, and capsulev5 images and all movies
     */
    private Map<String, String> getAppStoreMedia(@NonNull JsonNode appNode) {
        Map<String, String> storeMedia = new HashMap<>();

        // Add header, capsule, and capsulev5 images to map. (capsulev5 is just smaller I believe)
        Optional.ofNullable(appNode.get("header_image").asText())
                .ifPresent(path -> storeMedia.put("header_image", path));
        Optional.ofNullable(appNode.get("capsule_image").asText())
                .ifPresent(path -> storeMedia.put("capsule_image", path));
        Optional.ofNullable(appNode.get("capsule_imagev5").asText())
                .ifPresent(path -> storeMedia.put("capsule_imagev5", path));

        JsonNode movieNode = appNode.get("movies");
        if (movieNode == null) return storeMedia;

        // Loop through movies and add them to storeMedia
        for (JsonNode movies : movieNode) {
            JsonNode mp4 = movies.get("mp4");
            if (mp4 == null) continue;

            String name = movies.get("name").asText();
            // If name cannot be found, do not add it to the map
            if (name == null || name.isBlank()) continue;

            // Get max resolution of movie if possible, otherwise get 480p
            String moviePath = Optional.ofNullable(mp4.get("max").asText())
                    .orElse(mp4.get("480").asText());

            // Only add to storeMedia if the moviePath isn't null or blank
            if (moviePath != null && !moviePath.isBlank()) storeMedia.put(name, moviePath);
        }

        return storeMedia;
    }

    /**
     * Get all screenshot URLs from an app's jsonNode
     *
     * @param appNode The jsonNode for the app
     * @return A list containing all found screenshots
     */
    private List<String> getAppScreenshots(@NonNull JsonNode appNode) {
        List<String> screenshots = new ArrayList<>();

        JsonNode screenshotsNode = appNode.get("screenshots");
        if (screenshotsNode == null) return screenshots;

        for (JsonNode screenshot : screenshotsNode) {
            // Get full image if possible, otherwise get thumbnail.
            JsonNode imageNode = Optional.ofNullable(screenshot.get("path_full"))
                    .orElse(screenshot.get("path_thumbnail"));

            String imagePath = imageNode == null ? "" : imageNode.asText();

            // Only add to screenshots list if the imagePath isn't null or blank
            // Unsure if it's possible for a screenshot to have a blank path (I doubt it), but that is why I am not
            // doing a null check here on imageNode and then just adding imageNode.asText()
            if (!imagePath.isBlank()) screenshots.add(imagePath);
        }

        return screenshots;
    }

}
