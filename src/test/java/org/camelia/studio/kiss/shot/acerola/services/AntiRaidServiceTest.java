package org.camelia.studio.kiss.shot.acerola.services;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiRaidServiceTest {

    @Test
    void triggersWhenMentionTotalReachesLimitInsideWindow() {
        AntiRaidService service = new AntiRaidService(5, Duration.ofSeconds(10), Duration.ofDays(7));
        Instant now = Instant.parse("2026-06-21T12:00:00Z");

        AntiRaidService.MentionSpamResult firstMessage =
                service.recordMentions("guild-1", "user-1", 2, now);
        AntiRaidService.MentionSpamResult secondMessage =
                service.recordMentions("guild-1", "user-1", 3, now.plusSeconds(9));

        assertFalse(firstMessage.thresholdReached());
        assertTrue(secondMessage.thresholdReached());
        assertEquals(5, secondMessage.totalMentions());
    }

    @Test
    void ignoresMentionsOlderThanWindow() {
        AntiRaidService service = new AntiRaidService(5, Duration.ofSeconds(10), Duration.ofDays(7));
        Instant now = Instant.parse("2026-06-21T12:00:00Z");

        service.recordMentions("guild-1", "user-1", 4, now);
        AntiRaidService.MentionSpamResult result =
                service.recordMentions("guild-1", "user-1", 1, now.plusSeconds(11));

        assertFalse(result.thresholdReached());
        assertEquals(1, result.totalMentions());
    }

    @Test
    void detectsAccountYoungerThanMinimumAge() {
        AntiRaidService service = new AntiRaidService(5, Duration.ofSeconds(10), Duration.ofDays(7));
        Instant now = Instant.parse("2026-06-21T12:00:00Z");

        assertTrue(service.isAccountTooYoung(now.minus(Duration.ofDays(6)), now));
    }

    @Test
    void allowsAccountAtMinimumAge() {
        AntiRaidService service = new AntiRaidService(5, Duration.ofSeconds(10), Duration.ofDays(7));
        Instant now = Instant.parse("2026-06-21T12:00:00Z");

        assertFalse(service.isAccountTooYoung(now.minus(Duration.ofDays(7)), now));
    }

    @Test
    void countsUserAndRoleMentionTokensFromRawContent() {
        String rawContent = "Salut <@123> <@!456> <@&789> @everyone @here <#111>";

        int mentionCount = AntiRaidService.countMentionTokens(rawContent);

        assertEquals(3, mentionCount);
    }

    @Test
    void dynamicThresholdsRemainIsolatedBetweenServers() {
        AntiRaidService service = new AntiRaidService();
        Instant now = Instant.parse("2026-06-21T12:00:00Z");

        service.recordMentions("guild-1", "same-user", 4, now, 5, Duration.ofSeconds(10));
        AntiRaidService.MentionSpamResult otherGuild = service.recordMentions(
                "guild-2",
                "same-user",
                1,
                now.plusSeconds(1),
                5,
                Duration.ofSeconds(10));

        assertFalse(otherGuild.thresholdReached());
        assertEquals(1, otherGuild.totalMentions());
    }

    @Test
    void reachingAThresholdStartsANewDetectionWindow() {
        AntiRaidService service = new AntiRaidService();
        Instant now = Instant.parse("2026-06-21T12:00:00Z");

        assertTrue(service.recordMentions(
                "guild-1", "user-1", 5, now, 5, Duration.ofSeconds(10)).thresholdReached());
        AntiRaidService.MentionSpamResult next = service.recordMentions(
                "guild-1", "user-1", 1, now.plusSeconds(1), 5, Duration.ofSeconds(10));

        assertFalse(next.thresholdReached());
        assertEquals(1, next.totalMentions());
    }

    @Test
    void leavingAGuildOnlyClearsThatGuildMentionWindow() {
        AntiRaidService service = new AntiRaidService();
        Instant now = Instant.parse("2026-06-21T12:00:00Z");
        service.recordMentions("guild-1", "same-user", 2, now, 3, Duration.ofSeconds(10));
        service.recordMentions("guild-2", "same-user", 2, now, 3, Duration.ofSeconds(10));

        service.evictGuild("guild-1");

        AntiRaidService.MentionSpamResult leftGuild = service.recordMentions(
                "guild-1", "same-user", 1, now.plusSeconds(1), 3, Duration.ofSeconds(10));
        AntiRaidService.MentionSpamResult retainedGuild = service.recordMentions(
                "guild-2", "same-user", 1, now.plusSeconds(1), 3, Duration.ofSeconds(10));
        assertFalse(leftGuild.thresholdReached());
        assertEquals(1, leftGuild.totalMentions());
        assertTrue(retainedGuild.thresholdReached());
    }
}
