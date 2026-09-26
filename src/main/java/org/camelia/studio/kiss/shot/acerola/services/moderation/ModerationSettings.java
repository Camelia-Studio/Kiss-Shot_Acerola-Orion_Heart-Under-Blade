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
                bool(values, ModuleSetting.ANTI_RECENT_ENABLED),
                positiveInt(values, ModuleSetting.ANTI_RECENT_MAX_AGE_DAYS),
                requiredAction(values, ModuleSetting.ANTI_RECENT_ACTION),
                duration(values, ModuleSetting.ANTI_RECENT_TIMEOUT_SECONDS),
                bool(values, ModuleSetting.ANTI_MENTION_ENABLED),
                positiveInt(values, ModuleSetting.ANTI_MENTION_LIMIT),
                duration(values, ModuleSetting.ANTI_MENTION_WINDOW_SECONDS),
                requiredAction(values, ModuleSetting.ANTI_MENTION_ACTION),
                duration(values, ModuleSetting.ANTI_MENTION_TIMEOUT_SECONDS),
                bool(values, ModuleSetting.ANTI_MENTION_DELETE_MESSAGE));
    }

    public static AutomaticSanction automaticSanction(ModuleConfiguration configuration) {
        Map<ModuleSetting, String> values = configuration.settings();
        return new AutomaticSanction(
                optionalAction(values, ModuleSetting.SANCTION_ACTION),
                duration(values, ModuleSetting.SANCTION_TIMEOUT_SECONDS),
                nonNegativeInt(values, ModuleSetting.SANCTION_BAN_HISTORY_DAYS),
                bool(values, ModuleSetting.SANCTION_DELETE_MESSAGE));
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

    /**
     * L'API renvoie toujours les réglages avec leurs valeurs par défaut : seul {@code SANCTION_ACTION}
     * peut être absent. Toute autre clé manquante est une réponse incohérente.
     */
    private static String required(Map<ModuleSetting, String> values, ModuleSetting key) {
        String raw = values.get(key);
        if (raw == null) {
            throw new IllegalArgumentException("Réglage manquant : " + key);
        }
        return raw;
    }

    private static Optional<SanctionAction> optionalAction(Map<ModuleSetting, String> values, ModuleSetting key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(SanctionAction.valueOf(raw));
    }

    private static SanctionAction requiredAction(Map<ModuleSetting, String> values, ModuleSetting key) {
        return SanctionAction.valueOf(required(values, key));
    }

    private static boolean bool(Map<ModuleSetting, String> values, ModuleSetting key) {
        String raw = required(values, key);
        if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Réglage booléen invalide : " + key);
        }
        return Boolean.parseBoolean(raw);
    }

    private static int positiveInt(Map<ModuleSetting, String> values, ModuleSetting key) {
        int value = integer(values, key);
        if (value <= 0) {
            throw new IllegalArgumentException("Le réglage " + key + " doit être positif");
        }
        return value;
    }

    private static int nonNegativeInt(Map<ModuleSetting, String> values, ModuleSetting key) {
        int value = integer(values, key);
        if (value < 0) {
            throw new IllegalArgumentException("Le réglage " + key + " ne peut pas être négatif");
        }
        return value;
    }

    private static Duration duration(Map<ModuleSetting, String> values, ModuleSetting key) {
        return Duration.ofSeconds(positiveInt(values, key));
    }

    private static int integer(Map<ModuleSetting, String> values, ModuleSetting key) {
        try {
            return Integer.parseInt(required(values, key));
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
