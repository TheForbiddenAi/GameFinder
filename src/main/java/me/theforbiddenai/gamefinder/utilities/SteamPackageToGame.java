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

        return getExpirationEpoch(packageNode.elements().next(), appId);
    }

    /**
     * Gets expiration epoch of a package
     *
     * @param packageNode The json information for the package
     * @param appId       The id of the app (can be null)
     * @return The found expiration epoch or GameFinderConstants.NO_EXPIRATION_EPOCH
     */
    private long getExpirationEpoch(@NonNull JsonNode packageNode, String appId) {
        if (!packageNode.has("discount_end_rtime")) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        long expirationEpoch = packageNode.get("discount_end_rtime").asLong(GameFinderConstants.NO_EXPIRATION_EPOCH);
        // If expiration epoch is 0 then we must get it via a different method
        if (expirationEpoch > 0) return expirationEpoch;

        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }
}
