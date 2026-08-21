package org.camelia.studio.kiss.shot.acerola.services.moderation;

import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.SanctionAction;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class ModerationSettings {
    public static final int MAX_TIMEOUT_SECONDS = 28 * 24 * 60 * 60;
    public static final int MAX_BAN_HISTORY_DAYS = 7;

    private ModerationSettings() {
    }

    public static AntiRaid antiRaid(ModuleConfiguration configuration) {
        Map<ModuleSetting, String> values = configuration.settings();
        return new AntiRaid(
                bool(values, ModuleSetting.ANTI_RECENT_ENABLED, true),
                positiveInt(values, ModuleSetting.ANTI_RECENT_MAX_AGE_DAYS, 7),
                action(values, ModuleSetting.ANTI_RECENT_ACTION).orElse(SanctionAction.KICK),
                duration(values, ModuleSetting.ANTI_RECENT_TIMEOUT_SECONDS, 600),
                bool(values, ModuleSetting.ANTI_MENTION_ENABLED, true),
                positiveInt(values, ModuleSetting.ANTI_MENTION_LIMIT, 5),
                duration(values, ModuleSetting.ANTI_MENTION_WINDOW_SECONDS, 10),
                action(values, ModuleSetting.ANTI_MENTION_ACTION).orElse(SanctionAction.TIMEOUT),
                duration(values, ModuleSetting.ANTI_MENTION_TIMEOUT_SECONDS, 600),
                bool(values, ModuleSetting.ANTI_MENTION_DELETE_MESSAGE, false));
    }

    public static AutomaticSanction automaticSanction(ModuleConfiguration configuration) {
        Map<ModuleSetting, String> values = configuration.settings();
        return new AutomaticSanction(
                action(values, ModuleSetting.SANCTION_ACTION),
                duration(values, ModuleSetting.SANCTION_TIMEOUT_SECONDS, 600),
                nonNegativeInt(values, ModuleSetting.SANCTION_BAN_HISTORY_DAYS, 0),
                bool(values, ModuleSetting.SANCTION_DELETE_MESSAGE, false));
    }

    public static Map<ModuleSetting, String> recentAccountValues(
            boolean enabled,
            int maximumAgeDays,
            SanctionAction action,
            int timeoutSeconds
    ) {
        Map<ModuleSetting, String> values = new EnumMap<>(ModuleSetting.class);
        values.put(ModuleSetting.ANTI_RECENT_ENABLED, String.valueOf(enabled));
        values.put(ModuleSetting.ANTI_RECENT_MAX_AGE_DAYS, String.valueOf(maximumAgeDays));
        values.put(ModuleSetting.ANTI_RECENT_ACTION, action.name());
        values.put(ModuleSetting.ANTI_RECENT_TIMEOUT_SECONDS, String.valueOf(timeoutSeconds));
        return Map.copyOf(values);
    }

    public static Map<ModuleSetting, String> mentionSpamValues(
            boolean enabled,
            int mentionLimit,
            int windowSeconds,
            SanctionAction action,
            int timeoutSeconds,
            boolean deleteMessage
    ) {
        Map<ModuleSetting, String> values = new EnumMap<>(ModuleSetting.class);
        values.put(ModuleSetting.ANTI_MENTION_ENABLED, String.valueOf(enabled));
        values.put(ModuleSetting.ANTI_MENTION_LIMIT, String.valueOf(mentionLimit));
        values.put(ModuleSetting.ANTI_MENTION_WINDOW_SECONDS, String.valueOf(windowSeconds));
        values.put(ModuleSetting.ANTI_MENTION_ACTION, action.name());
        values.put(ModuleSetting.ANTI_MENTION_TIMEOUT_SECONDS, String.valueOf(timeoutSeconds));
        values.put(ModuleSetting.ANTI_MENTION_DELETE_MESSAGE, String.valueOf(deleteMessage));
        return Map.copyOf(values);
    }

    public static Map<ModuleSetting, String> automaticSanctionValues(
            SanctionAction action,
            int timeoutSeconds,
            int banHistoryDays,
            boolean deleteMessage
    ) {
        Map<ModuleSetting, String> values = new EnumMap<>(ModuleSetting.class);
        values.put(ModuleSetting.SANCTION_ACTION, action.name());
        values.put(ModuleSetting.SANCTION_TIMEOUT_SECONDS, String.valueOf(timeoutSeconds));
        values.put(ModuleSetting.SANCTION_BAN_HISTORY_DAYS, String.valueOf(banHistoryDays));
        values.put(ModuleSetting.SANCTION_DELETE_MESSAGE, String.valueOf(deleteMessage));
        return Map.copyOf(values);
    }

    private static Optional<SanctionAction> action(Map<ModuleSetting, String> values, ModuleSetting key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(SanctionAction.valueOf(raw));
    }

    private static boolean bool(Map<ModuleSetting, String> values, ModuleSetting key, boolean fallback) {
        String raw = values.get(key);
        if (raw == null) return fallback;
        if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Réglage booléen invalide : " + key);
        }
        return Boolean.parseBoolean(raw);
    }

    private static int positiveInt(Map<ModuleSetting, String> values, ModuleSetting key, int fallback) {
        int value = integer(values, key, fallback);
        if (value <= 0) {
            throw new IllegalArgumentException("Le réglage " + key + " doit être positif");
        }
        return value;
    }

    private static int nonNegativeInt(Map<ModuleSetting, String> values, ModuleSetting key, int fallback) {
        int value = integer(values, key, fallback);
        if (value < 0) {
            throw new IllegalArgumentException("Le réglage " + key + " ne peut pas être négatif");
        }
        return value;
    }

    private static Duration duration(Map<ModuleSetting, String> values, ModuleSetting key, int fallbackSeconds) {
        return Duration.ofSeconds(positiveInt(values, key, fallbackSeconds));
    }

    private static int integer(Map<ModuleSetting, String> values, ModuleSetting key, int fallback) {
        try {
            return Integer.parseInt(values.getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Réglage numérique invalide : " + key, exception);
        }
    }

    public record AntiRaid(
            boolean recentAccountEnabled,
            int recentAccountMaximumAgeDays,
            SanctionAction recentAccountAction,
            Duration recentAccountTimeout,
            boolean mentionSpamEnabled,
            int mentionLimit,
            Duration mentionWindow,
            SanctionAction mentionAction,
            Duration mentionTimeout,
            boolean deleteMentionMessage
    ) {
    }

    public record AutomaticSanction(
            Optional<SanctionAction> action,
            Duration timeout,
            int banHistoryDays,
            boolean deleteMessage
    ) {
    }
}
