package org.camelia.studio.kiss.shot.acerola.services.moderation;

import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.SanctionAction;

import java.util.EnumMap;
import java.util.Map;

/**
 * Réglages tels que l'API les renvoie pour un module qui n'a rien de configuré : valeurs par défaut incluses,
 * sauf {@code SANCTION_ACTION} qui n'en a pas.
 */
public final class ApiDefaults {
    private ApiDefaults() {
    }

    public static Map<ModuleSetting, String> settings(ModuleType module) {
        Map<ModuleSetting, String> values = new EnumMap<>(ModuleSetting.class);
        switch (module) {
            case ANTI_RAID -> {
                values.putAll(ModerationSettings.recentAccountValues(true, 7, SanctionAction.KICK, 600));
                values.putAll(ModerationSettings.mentionSpamValues(true, 5, 10, SanctionAction.TIMEOUT, 600, false));
            }
            case AUTO_SANCTION_CHANNEL, AUTO_SANCTION_ROLE -> {
                values.putAll(ModerationSettings.automaticSanctionValues(SanctionAction.LOG_ONLY, 600, 0, false));
                values.remove(ModuleSetting.SANCTION_ACTION);
            }
            default -> {
            }
        }
        return values;
    }
}
