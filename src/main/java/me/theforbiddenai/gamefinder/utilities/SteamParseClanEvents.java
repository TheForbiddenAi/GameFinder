package me.theforbiddenai.gamefinder.utilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NonNull;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.SteamClanEvent;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;

import java.io.IOException;
import java.util.*;

public class SteamParseClanEvents {

    private final SteamRequests steamRequests;
    private final JsonNode eventsNode;

    @Getter
    private final String clanId;
    @Getter
    private final List<SteamClanEvent> events;

    public SteamParseClanEvents(@NonNull SteamRequests steamRequests, @NonNull String clanId) throws IOException, GameRetrievalException {
        this.steamRequests = steamRequests;
        this.clanId = clanId;

        Optional<JsonNode> optionalEventsNode = steamRequests.getEventDetails(clanId);
        if (optionalEventsNode.isEmpty()) {
            throw new GameRetrievalException("Unable to retrieve event details for steam clan id: " + clanId);
        }

        this.eventsNode = optionalEventsNode.get();
        this.events = new ArrayList<>();
    }

    /**
     * Parses event json objects into SteamClanEvents
     *
     * @param mustHaveEndEpoch If this is true, an even json node will only be converted to a SteamClanEvents if there
     *                         is an rtime32_end_time defined that is greater than 0
     * @throws JsonProcessingException If json data cannot be parsed
     */
    public void parseEvents(boolean mustHaveEndEpoch) throws JsonProcessingException {
        for (JsonNode eventNode : eventsNode) {
            long endEpoch = Optional.ofNullable(eventNode.get("rtime32_end_time"))
                    .map(node -> node.asLong(GameFinderConstants.NO_EXPIRATION_EPOCH))
                    .orElse(GameFinderConstants.NO_EXPIRATION_EPOCH);

            // If endEpoch is less than or equal to 0, then there is no designated endEpoch
            // So if that is the case and mustHaveEndEpoch is true, we will ignore this event
            if (mustHaveEndEpoch && endEpoch <= 0) continue;

            SteamClanEvent.SteamClanEventBuilder eventBuilder = SteamClanEvent.builder()
                    .name(eventNode.get("event_name").asText())
                    .endEpoch(endEpoch);

            // Parse jsonData field of eventNode if it has one
            // This contains the includedApps, packages, and bundles
            parseJsonData(eventNode, eventBuilder);

            events.add(eventBuilder.build());
        }
    }

    /**
     * Parses the jsonData JsonNode to identify all apps, packages, and bundles, and then adds them to the eventBuilder
     *
     * @param eventNode    The json information about the event
     * @param eventBuilder The event builder for the event
     * @throws JsonProcessingException If jsonData is unable to be parsed
     */
    private void parseJsonData(JsonNode eventNode, SteamClanEvent.SteamClanEventBuilder eventBuilder) throws JsonProcessingException {
        // Get jsonData
        String jsonData = Optional.ofNullable(eventNode.get("jsondata"))
                .map(JsonNode::asText)
                .orElse("null"); // This is because jsondata can return the string null, so this keeps it consistent if it's not found

        // If jsonData is set to "null" or there is no sale_sections node, then set includedApps, packages, and bundles to empty list

        if (jsonData.equalsIgnoreCase("null")) {
            eventBuilder.includedApps(new HashSet<>());
            eventBuilder.includedPackages(new HashSet<>());
            eventBuilder.includedBundles(new HashSet<>());
            return;
        }

        JsonNode dataNode = steamRequests.getMapper().readTree(jsonData);

        JsonNode salesNode = dataNode.get("sale_sections");
        Set<String> includedApps = new HashSet<>();
        Set<String> includedPackages = new HashSet<>();
        Set<String> includedBundles = new HashSet<>();

        // Loop through sales sections
        for (JsonNode sectionNode : salesNode) {
            // Get the capsule information
            JsonNode capsulesNode = sectionNode.get("capsules");
            if (capsulesNode == null) continue;

            // Loop through capsule nodes, and sort them into includedApps, packages, and bundles
            for (JsonNode capsuleNode : capsulesNode) {
                String capsuleId = capsuleNode.get("id").asText();
                String capsuleType = capsuleNode.get("type").asText().toLowerCase();

                switch (capsuleType) {
                    case "game", "dlc" -> includedApps.add(capsuleId);
                    case "sub" -> includedPackages.add(capsuleId);
                    case "bundle" -> includedBundles.add(capsuleId);
                }
            }
        }

        // Set data in event builder
        eventBuilder.includedApps(includedApps);
        eventBuilder.includedPackages(includedPackages);
        eventBuilder.includedBundles(includedBundles);

    }

    /**
     * Finds the first event that contains the appId in includeApp that has an end time in the future
     *
     * @param appId The id of the app
     * @return The found event end time or {@link GameFinderConstants#NO_EXPIRATION_EPOCH}
     */
    public long getEventEndTimeByAppId(String appId) {
        for (SteamClanEvent event : events) {
            if (event.getIncludedApps().contains(appId)) {
                if (event.getEndEpoch() > System.currentTimeMillis()) return event.getEndEpoch();
            }
        }
        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }

    /**
     * Finds the first event that contains the packageId in includeApp that has an end time in the future
     *
     * @param packageId The id of the package
     * @return The found event end time or {@link GameFinderConstants#NO_EXPIRATION_EPOCH}
     */
    public long getEventEndTimeByPackageId(String packageId) {
        for (SteamClanEvent event : events) {
            if (event.getIncludedPackages().contains(packageId)) {
                if (event.getEndEpoch() > System.currentTimeMillis()) return event.getEndEpoch();
            }
        }
        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }

    /**
     * Finds the first event that contains the bundleId in includeApp that has an end time in the future
     *
     * @param bundleId The id of the bundle
     * @return The found event end time or {@link GameFinderConstants#NO_EXPIRATION_EPOCH}
     */
    public long getEventEndTimeByBundleId(String bundleId) {
        for (SteamClanEvent event : events) {
            if (event.getIncludedBundles().contains(bundleId)) {
                if (event.getEndEpoch() > System.currentTimeMillis()) return event.getEndEpoch();
            }
        }
        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }

}
