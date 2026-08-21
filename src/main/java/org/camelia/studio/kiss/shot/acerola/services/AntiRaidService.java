package org.camelia.studio.kiss.shot.acerola.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiRaidService {

    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?\\d+>");
    private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&\\d+>");

    private final Integer defaultMentionLimit;
    private final Duration defaultMentionWindow;
    private final Duration defaultMinimumAccountAge;
    private final Map<UserKey, Deque<MentionEvent>> mentionEvents = new HashMap<>();

    public AntiRaidService() {
        this.defaultMentionLimit = null;
        this.defaultMentionWindow = null;
        this.defaultMinimumAccountAge = null;
    }

    public AntiRaidService(int mentionLimit, Duration mentionWindow, Duration minimumAccountAge) {
        if (mentionLimit <= 0) {
            throw new IllegalArgumentException("mentionLimit must be positive");
        }
        if (mentionWindow.isZero() || mentionWindow.isNegative()) {
            throw new IllegalArgumentException("mentionWindow must be positive");
        }
        if (minimumAccountAge.isNegative()) {
            throw new IllegalArgumentException("minimumAccountAge must not be negative");
        }

        this.defaultMentionLimit = mentionLimit;
        this.defaultMentionWindow = mentionWindow;
        this.defaultMinimumAccountAge = minimumAccountAge;
    }

    public synchronized MentionSpamResult recordMentions(String guildId, String userId, int mentionCount, Instant occurredAt) {
        if (defaultMentionLimit == null || defaultMentionWindow == null) {
            throw new IllegalStateException("Les seuils anti-raid doivent être fournis par le serveur");
        }
        return recordMentions(guildId, userId, mentionCount, occurredAt, defaultMentionLimit, defaultMentionWindow);
    }

    public synchronized MentionSpamResult recordMentions(
            String guildId,
            String userId,
            int mentionCount,
            Instant occurredAt,
            int mentionLimit,
            Duration mentionWindow
    ) {
        if (mentionCount <= 0) {
            return new MentionSpamResult(false, 0);
        }

        UserKey key = new UserKey(guildId, userId);
        Deque<MentionEvent> events = mentionEvents.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        events.addLast(new MentionEvent(occurredAt, mentionCount));

        Instant cutoff = occurredAt.minus(mentionWindow);
        while (!events.isEmpty() && events.peekFirst().occurredAt().isBefore(cutoff)) {
            events.removeFirst();
        }

        int totalMentions = events.stream()
                .mapToInt(MentionEvent::mentionCount)
                .sum();

        boolean thresholdReached = totalMentions >= mentionLimit;
        if (thresholdReached) {
            mentionEvents.remove(key);
        }
        return new MentionSpamResult(thresholdReached, totalMentions);
    }

    public boolean isAccountTooYoung(Instant accountCreatedAt, Instant now) {
        if (defaultMinimumAccountAge == null) {
            throw new IllegalStateException("L'âge minimum doit être fourni par le serveur");
        }
        return isAccountTooYoung(accountCreatedAt, now, defaultMinimumAccountAge);
    }

    public boolean isAccountTooYoung(Instant accountCreatedAt, Instant now, Duration minimumAccountAge) {
        return accountCreatedAt.plus(minimumAccountAge).isAfter(now);
    }

    public static int countMentionTokens(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return 0;
        }

        return countMatches(USER_MENTION_PATTERN.matcher(rawContent))
                + countMatches(ROLE_MENTION_PATTERN.matcher(rawContent));
    }

    private static int countMatches(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public record MentionSpamResult(boolean thresholdReached, int totalMentions) {
    }

    private record UserKey(String guildId, String userId) {
    }

    private record MentionEvent(Instant occurredAt, int mentionCount) {
    }
}
