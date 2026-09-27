package org.camelia.studio.kiss.shot.acerola.commands.audio;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingDiscordUploader;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingMode;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingService;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingStatus;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingStopOutcome;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingStopResult;

import java.util.List;
import java.util.Optional;

public class RecordCommand implements ISlashCommand {
    private static final String ACTION_START = "start";
    private static final String ACTION_STOP = "stop";
    private static final String ACTION_STATUS = "status";

    private final RecordingService recordingService = RecordingService.getInstance();

    @Override
    public String getName() {
        return "record";
    }

    @Override
    public String getDescription() {
        return "Enregistre un salon vocal et envoie le résultat en MP3";
    }

    @Override
    public DefaultMemberPermissions defaultPermissions() {
        return DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, "action", "Action à effectuer", true)
                        .addChoice("Démarrer", ACTION_START)
                        .addChoice("Arrêter", ACTION_STOP)
                        .addChoice("Statut", ACTION_STATUS),
                new OptionData(OptionType.STRING, "mode", "Mode d'enregistrement", false)
                        .addChoice("Mix unique", RecordingMode.MIX.optionValue())
                        .addChoice("Une piste par voix", RecordingMode.TRACKS.optionValue()));
    }

    @Override
    public Optional<ModuleType> requiredModule() {
        return Optional.of(ModuleType.VOICE_RECORDING);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("Cette commande ne peut être utilisée que sur un serveur.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        switch (action) {
            case ACTION_START -> start(event, member);
            case ACTION_STOP -> stop(event, member);
            case ACTION_STATUS -> status(event);
            default -> event.reply("Action inconnue.").setEphemeral(true).queue();
        }
    }

    private void start(SlashCommandInteractionEvent event, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("Vous devez être connecté à un salon vocal pour démarrer l'enregistrement.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply().queue();
        OptionMapping modeOption = event.getOption("mode");
        RecordingMode mode = modeOption == null ? RecordingMode.MIX : RecordingMode.fromOption(modeOption.getAsString());
        AudioChannel channel = voiceState.getChannel();

        try {
            recordingService.startRecording(
                    event.getGuild(),
                    channel,
                    event.getChannel().asGuildMessageChannel(),
                    member,
                    mode);
            event.getHook().editOriginal(("Enregistrement démarré dans `%s` par %s en mode `%s`. "
                            + "Utilisez `/record action:status` pour le consulter ou "
                            + "`/record action:stop` pour l'arrêter.")
                            .formatted(channel.getName(), member.getAsMention(), label(mode)))
                    .queue();
        } catch (Exception e) {
            event.getHook().editOriginal("Impossible de démarrer l'enregistrement: " + e.getMessage()).queue();
        }
    }

    private void stop(SlashCommandInteractionEvent event, Member member) {
        event.deferReply().queue();
        try {
            RecordingStopOutcome outcome = recordingService.stopRecording(event.getGuild(), member);
            RecordingStopResult result = outcome.result();
            event.getHook().editOriginal("Enregistrement arrêté. Envoi des fichiers MP3 en cours...").queue();
            String message = "Enregistrement terminé pour `%s` en mode `%s`. Durée: %s."
                    .formatted(result.channelName(), label(result.mode()), RecordingService.formatDuration(result.duration()));
            RecordingDiscordUploader.upload(outcome.outputChannel(), result, message, failure ->
                    event.getHook().editOriginal("Enregistrement terminé, mais l'envoi Discord a échoué: "
                                    + failure.getMessage())
                            .queue());
        } catch (Exception e) {
            event.getHook().editOriginal("Impossible d'arrêter l'enregistrement: " + e.getMessage()).queue();
        }
    }

    private void status(SlashCommandInteractionEvent event) {
        Optional<RecordingStatus> status = recordingService.getStatus(event.getGuild().getIdLong());
        if (status.isEmpty()) {
            event.reply("Aucun enregistrement n'est en cours.").setEphemeral(true).queue();
            return;
        }

        RecordingStatus recordingStatus = status.get();
        event.reply("Enregistrement en cours dans `%s` en mode `%s` depuis %s. Fichiers ouverts: %d."
                        .formatted(
                                recordingStatus.channelName(),
                                label(recordingStatus.mode()),
                                RecordingService.formatDuration(recordingStatus.duration()),
                                recordingStatus.fileCount()))
                .setEphemeral(true)
                .queue();
    }

    private static String label(RecordingMode mode) {
        return mode == RecordingMode.TRACKS ? "pistes séparées" : "mix unique";
    }
}
