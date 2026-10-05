package org.camelia.studio.kiss.shot.acerola.services.moderation;

import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.SanctionAction;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationSettingsTest {
    @Test
    void aMissingSettingIsAnInconsistentApiResponse() {
        ModuleConfiguration withoutSettings = new ModuleConfiguration(
                ModuleType.ANTI_RAID, ModuleStatus.DISABLED, null, null, Map.of(), Map.of());

        assertThrows(IllegalArgumentException.class, () -> ModerationSettings.antiRaid(withoutSettings));
    }

    @Test
    void antiRaidDefaultsMatchTheSpecification() {
        ModerationSettings.AntiRaid settings = ModerationSettings.antiRaid(configuration(ModuleType.ANTI_RAID));

        assertTrue(settings.recentAccountEnabled());
        assertEquals(7, settings.recentAccountMaximumAgeDays());
        assertEquals(SanctionAction.KICK, settings.recentAccountAction());
        assertTrue(settings.mentionSpamEnabled());
        assertEquals(5, settings.mentionLimit());
        assertEquals(10, settings.mentionWindow().toSeconds());
        assertEquals(SanctionAction.TIMEOUT, settings.mentionAction());
        assertEquals(600, settings.mentionTimeout().toSeconds());
        assertFalse(settings.deleteMentionMessage());
    }

    @Test
    void automaticSanctionHasNoPreselectedAction() {
        ModerationSettings.AutomaticSanction settings = ModerationSettings.automaticSanction(
                configuration(ModuleType.AUTO_SANCTION_CHANNEL));

        assertTrue(settings.action().isEmpty());
        assertEquals(0, settings.banHistoryDays());
        assertFalse(settings.deleteMessage());
    }

    @Test
    void savedAutomaticSanctionValuesRoundTrip() {
        ModuleConfiguration configured = configuration(ModuleType.AUTO_SANCTION_CHANNEL).withSettings(
                ModerationSettings.automaticSanctionValues(SanctionAction.BAN, 900, 3, true));

        ModerationSettings.AutomaticSanction settings = ModerationSettings.automaticSanction(configured);

        assertEquals(SanctionAction.BAN, settings.action().orElseThrow());
        assertEquals(900, settings.timeout().toSeconds());
        assertEquals(3, settings.banHistoryDays());
        assertTrue(settings.deleteMessage());
    }

    @Test
    void memberAuditWatchesBothElementsByDefault() {
        ModerationSettings.MemberAudit settings = ModerationSettings.memberAudit(
                configuration(ModuleType.MEMBER_AUDIT));

        assertTrue(settings.watchNickname());
        assertTrue(settings.watchAvatar());
    }

    @Test
    void savedMemberAuditValuesRoundTrip() {
        ModuleConfiguration configured = configuration(ModuleType.MEMBER_AUDIT).withSettings(
                ModerationSettings.memberAuditValues(false, true));

        ModerationSettings.MemberAudit settings = ModerationSettings.memberAudit(configured);

        assertFalse(settings.watchNickname());
        assertTrue(settings.watchAvatar());
    }

    private ModuleConfiguration configuration(ModuleType module) {
        return new ModuleConfiguration(
                module,
                ModuleStatus.DISABLED,
                null,
                null,
                Map.of(),
                Map.of(),
                ApiDefaults.settings(module));
    }
}
