package me.theforbiddenai.gamefinder.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NonNull;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;

import java.io.IOException;

public class SteamPackageToGame {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();
    private final SteamRequests steamRequests;

    public SteamPackageToGame(SteamRequests steamRequests) {
        this.steamRequests = steamRequests;
    }

    public long getExpirationEpoch(String packageId, String appId) throws IOException {
        JsonNode packageNode = steamRequests.getPackageDetails(packageId);

        // If node is null or has no elements (meaning no packages were found) return GameFinderConstants.NO_EXPIRATION_EPOCH
        if (packageNode == null || !packageNode.elements().hasNext()) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        return getExpirationEpoch(packageNode.elements().next(), appId, packageId, null);
    }

    /**
     * Gets expiration epoch of a package
     *
     * @param packageNode The json information for the package
     * @param appId       The id of the app (can be null)
     * @param packageId   The id of the package (can be null)
     * @param bundleId    The id of the bundle (can be null)
     * @return The found expiration epoch or GameFinderConstants.NO_EXPIRATION_EPOCH
     */
    private long getExpirationEpoch(@NonNull JsonNode packageNode, String appId, String packageId, String bundleId) {
        if (!packageNode.has("discount_end_rtime")) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        long expirationEpoch = packageNode.get("discount_end_rtime").asLong(GameFinderConstants.NO_EXPIRATION_EPOCH);
        // If expiration epoch is 0 then we must get it via a different method
        System.out.println(packageNode);
        if (expirationEpoch > 0) return expirationEpoch;

        if (!CONFIG.useSteamClanEventEndTimes()) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        // Get creator clan ids
        JsonNode clanIdsNode = packageNode.get("creator_clan_ids");
        if (clanIdsNode == null) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        // Loop through clan ids
        for (JsonNode clanId : clanIdsNode) {
            try {
                // Parse the events related to the ids
                SteamParseClanEvents parser = new SteamParseClanEvents(steamRequests, clanId.asText());
                parser.parseEvents(true);

                // If appId is defined, attempt to retrieve the endTime from the parser
                if (appId != null) {
                    long endTime = parser.getEventEndTimeByAppId(appId);
                    if (endTime != GameFinderConstants.NO_EXPIRATION_EPOCH) return endTime;
                }

                // If packageId is defined, attempt to retrieve the endTime from the parser
                if (packageId != null) {
                    long endTime = parser.getEventEndTimeByPackageId(packageId);
                    if (endTime != GameFinderConstants.NO_EXPIRATION_EPOCH) return endTime;
                }

                // If bundleId is defined, attempt to retrieve the endTime from the parser
                if (bundleId != null) {
                    long endTime = parser.getEventEndTimeByBundleId(bundleId);
                    if (endTime != GameFinderConstants.NO_EXPIRATION_EPOCH) return endTime;
                }
            } catch (IOException | GameRetrievalException ex) {
                // Swallowing exception because I would rather say the expiration epoch is
                // GameFinderConstants.NO_EXPIRATION_EPOCH than throw an error in this case
                ex.printStackTrace();
                continue;
            }
        }

        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }
}
