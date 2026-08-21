package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDashboardTest {
    @Test
    void dashboardSelectsOneModuleAndShowsItsContextualActions() {
        List<ModuleConfiguration> configurations = configurations(
                ModuleType.AUTO_ROLE,
                ModuleStatus.ACTIVE);

        ConfigDashboard.View view = ConfigDashboard.create(
                configurations,
                ModuleType.AUTO_ROLE,
                null);

        StringSelectMenu selector = (StringSelectMenu) view.components().getFirst().getComponents().getFirst();
        List<Button> buttons = view.components().get(1).getButtons();

        assertEquals(ModuleType.values().length, selector.getOptions().size());
        assertEquals(1, selector.getOptions().stream().filter(option -> option.isDefault()).count());
        assertEquals(
                List.of("Configurer", "Rattraper", "Désactiver"),
                buttons.stream().map(Button::getLabel).toList());
    }

    @Test
    void suspendedModuleOffersCorrectionRevalidationAndDisableActions() {
        ConfigDashboard.View view = ConfigDashboard.create(
                configurations(ModuleType.AUTO_SANCTION_CHANNEL, ModuleStatus.SUSPENDED),
                ModuleType.AUTO_SANCTION_CHANNEL,
                "Réglages enregistrés");

        List<String> labels = view.components().get(1).getButtons().stream()
                .map(Button::getLabel)
                .toList();

        assertEquals(
                List.of("Déclencheurs", "Action", "Ressources", "Revalider", "Désactiver"),
                labels);
        assertEquals("Réglages enregistrés", view.embed().getFooter().getText());
    }

    @Test
    void disabledModuleCanBeConfiguredBeforeActivation() {
        ConfigDashboard.View view = ConfigDashboard.create(
                configurations(ModuleType.AUTO_ROLE, ModuleStatus.DISABLED),
                ModuleType.AUTO_ROLE,
                null);

        assertEquals(
                List.of("Configurer", "Activer"),
                view.components().get(1).getButtons().stream().map(Button::getLabel).toList());
    }

    @Test
    void activeLinkEnrichmentOffersItsOptionalConfiguration() {
        ConfigDashboard.View view = ConfigDashboard.create(
                configurations(ModuleType.LINK_ENRICHMENT, ModuleStatus.ACTIVE),
                ModuleType.LINK_ENRICHMENT,
                null);

        assertEquals(
                List.of("Configurer", "Désactiver"),
                view.components().get(1).getButtons().stream().map(Button::getLabel).toList());
    }

    @Test
    void suspendedModuleWithoutEditableResourcesDoesNotOfferConfiguration() {
        ConfigDashboard.View view = ConfigDashboard.create(
                configurations(ModuleType.MUSIC, ModuleStatus.SUSPENDED),
                ModuleType.MUSIC,
                null);

        assertEquals(
                List.of("Revalider", "Désactiver"),
                view.components().get(1).getButtons().stream().map(Button::getLabel).toList());
    }

    @Test
    void actionIdResolvesTheRequestedModule() {
        ConfigDashboard.Action action = ConfigDashboard
                .actionFrom("config:dashboard:revalidate:ANTI_RAID")
                .orElseThrow();

        assertEquals(ConfigDashboard.ActionType.REVALIDATE, action.type());
        assertEquals(ModuleType.ANTI_RAID, action.module());
        assertTrue(ConfigDashboard.actionFrom("config:dashboard:unknown:ANTI_RAID").isEmpty());
    }

    @Test
    void disableActionRequiresAnExplicitConfirmation() {
        ConfigDashboard.View view = ConfigDashboard.confirmDisable(
                configurations(ModuleType.WARNINGS, ModuleStatus.ACTIVE),
                ModuleType.WARNINGS);

        assertEquals(
                List.of("Confirmer la désactivation", "Annuler"),
                view.components().get(1).getButtons().stream().map(Button::getLabel).toList());
    }

    @Test
    void autoRoleCatchUpRequiresExplicitConfirmation() {
        ConfigDashboard.View view = ConfigDashboard.confirmAutoRoleCatchUp(
                configurations(ModuleType.AUTO_ROLE, ModuleStatus.ACTIVE),
                ModuleType.AUTO_ROLE);

        assertEquals(
                List.of("Confirmer le rattrapage", "Annuler"),
                view.components().get(1).getButtons().stream().map(Button::getLabel).toList());
    }

    private List<ModuleConfiguration> configurations(ModuleType selected, ModuleStatus selectedStatus) {
        return Arrays.stream(ModuleType.values())
                .map(module -> new ModuleConfiguration(
                        module,
                        module == selected ? selectedStatus : ModuleStatus.DISABLED,
                        module == selected && selectedStatus == ModuleStatus.SUSPENDED
                                ? "Une ressource est invalide"
                                : null,
                        module == selected ? "123" : null,
                        module == selected
                                ? Map.of(ModuleResourcePurpose.PROTECTED, Set.of("456"))
                                : Map.of(),
                        Map.of()))
                .toList();
    }
}
