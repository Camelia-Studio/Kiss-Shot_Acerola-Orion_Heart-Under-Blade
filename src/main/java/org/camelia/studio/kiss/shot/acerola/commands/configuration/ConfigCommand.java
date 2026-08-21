package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class ConfigCommand implements ISlashCommand {
    private static final Logger logger = LoggerFactory.getLogger(ConfigCommand.class);
    private static final String STATUS = "status";
    private static final String ACTIVATE = "activate";
    private static final String DISABLE = "disable";
    private static final String REVALIDATE = "revalidate";
    private static final String LOG_CHANNEL = "log-channel";
    private static final String CLEAR_LOG_CHANNEL = "clear-log-channel";

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public String getDescription() {
        return "Configure les modules du serveur";
    }

    @Override
    public DefaultMemberPermissions defaultPermissions() {
        return DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR);
    }

    @Override
    public List<SubcommandData> getSubcommands() {
        return List.of(
                new SubcommandData(STATUS, "Affiche l'état des modules")
                        .addOptions(moduleOption(false)),
                new SubcommandData(ACTIVATE, "Valide puis active un module")
                        .addOptions(
                                moduleOption(true),
                                new OptionData(OptionType.ROLE, "role", "Rôle requis par le module", false),
                                new OptionData(OptionType.CHANNEL, "channel", "Salon requis par le module", false)
                                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData(DISABLE, "Désactive un module sans perdre ses réglages")
                        .addOptions(moduleOption(true)),
                new SubcommandData(REVALIDATE, "Relance la validation d'un module suspendu")
                        .addOptions(moduleOption(true)),
                new SubcommandData(LOG_CHANNEL, "Définit le salon de logs du serveur")
                        .addOptions(new OptionData(
                                OptionType.CHANNEL,
                                "channel",
                                "Salon de logs",
                                true).setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                new SubcommandData(CLEAR_LOG_CHANNEL, "Supprime le salon de logs configuré"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (event.getGuild() == null || member == null) {
            event.reply("Cette commande ne peut être utilisée que sur un serveur.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            reply(event, "Action de configuration inconnue.");
            return;
        }

        ModuleConfigurationService service = ModuleConfigurationService.getInstance();
        try {
            switch (subcommand) {
                case STATUS -> showStatus(event, service);
                case ACTIVATE -> reply(event, service.activate(
                        event.getGuild(),
                        selectedModule(event),
                        member.getId(),
                        optionId(event, "role"),
                        optionId(event, "channel")).message());
                case DISABLE -> reply(event, service.disable(
                        event.getGuild(), selectedModule(event), member.getId()).message());
                case REVALIDATE -> reply(event, service.revalidate(
                        event.getGuild(), selectedModule(event), member.getId()).message());
                case LOG_CHANNEL -> reply(event, service.setLogChannel(
                        event.getGuild(),
                        event.getOption("channel").getAsChannel().getId(),
                        member.getId()).message());
                case CLEAR_LOG_CHANNEL -> reply(event, service.setLogChannel(
                        event.getGuild(), null, member.getId()).message());
                default -> reply(event, "Action de configuration inconnue.");
            }
        } catch (RuntimeException exception) {
            logger.error("Configuration indisponible pour le serveur {}", event.getGuild().getId(), exception);
            reply(event, "La configuration est temporairement indisponible. Réessayez plus tard.");
        }
    }

    private void showStatus(SlashCommandInteractionEvent event, ModuleConfigurationService service) {
        OptionMapping selected = event.getOption("module");
        List<ModuleConfiguration> configurations = service.list(event.getGuild());
        if (selected != null) {
            ModuleType module = ModuleType.valueOf(selected.getAsString());
            configurations = configurations.stream()
                    .filter(configuration -> configuration.module() == module)
                    .toList();
        }

        StringBuilder response = new StringBuilder("**Configuration des modules**\n");
        for (ModuleConfiguration configuration : configurations) {
            response.append(statusIcon(configuration.status()))
                    .append(' ')
                    .append(label(configuration.module()))
                    .append(" — ")
                    .append(statusLabel(configuration.status()));
            if (configuration.status() == ModuleStatus.SUSPENDED) {
                response.append(" (").append(configuration.suspensionReason()).append(')');
            }
            response.append('\n');
        }
        reply(event, response.toString());
    }

    private OptionData moduleOption(boolean required) {
        OptionData option = new OptionData(OptionType.STRING, "module", "Module concerné", required);
        Arrays.stream(ModuleType.values()).forEach(module -> option.addChoice(label(module), module.name()));
        return option;
    }

    private ModuleType selectedModule(SlashCommandInteractionEvent event) {
        return ModuleType.valueOf(event.getOption("module").getAsString());
    }

    private String optionId(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        if (option == null) {
            return null;
        }
        return switch (option.getType()) {
            case ROLE -> option.getAsRole().getId();
            case CHANNEL -> option.getAsChannel().getId();
            default -> null;
        };
    }

    private String label(ModuleType module) {
        return switch (module) {
            case WARNINGS -> "Avertissements";
            case AUTO_ROLE -> "Rôle automatique";
            case MUSIC -> "Musique";
            case VOICE_RECORDING -> "Enregistrement vocal";
            case BOT_MESSAGES -> "Messages du bot";
            case LINK_ENRICHMENT -> "Enrichissement des liens";
            case INTEGRATION_REMOVAL -> "Suppression des intégrations";
            case ANTI_RAID -> "Anti-raid";
            case AUTO_SANCTION_CHANNEL -> "Sanction automatique par salon";
            case AUTO_SANCTION_ROLE -> "Sanction automatique par rôle";
        };
    }

    private String statusIcon(ModuleStatus status) {
        return switch (status) {
            case DISABLED -> "⚪";
            case ACTIVE -> "🟢";
            case SUSPENDED -> "🟠";
        };
    }

    private String statusLabel(ModuleStatus status) {
        return switch (status) {
            case DISABLED -> "désactivé";
            case ACTIVE -> "actif";
            case SUSPENDED -> "suspendu";
        };
    }

    private void reply(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }
}
