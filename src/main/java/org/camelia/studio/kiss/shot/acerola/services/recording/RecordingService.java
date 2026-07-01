package org.camelia.studio.kiss.shot.acerola.services.recording;

import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RecordingService {
    private static RecordingService INSTANCE;
    private static final Logger logger = LoggerFactory.getLogger(RecordingService.class);

    private final RecordingConfig config;
    private final AudioTranscoder transcoder;
    private final Map<Long, ActiveRecording> recordings = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> emptyStopTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "recording-empty-channel-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private RecordingService(RecordingConfig config, AudioTranscoder transcoder) {
        this.config = config;
        this.transcoder = transcoder;
    }

    public static RecordingService getInstance() {
        if (INSTANCE == null) {
            RecordingConfig config = RecordingConfig.fromEnv();
            INSTANCE = new RecordingService(config, new FfmpegAudioTranscoder(config.ffmpegCommand()));
        }
        return INSTANCE;
    }

    public synchronized void startRecording(
            Guild guild,
            AudioChannel channel,
            GuildMessageChannel outputChannel,
            Member startedBy,
            RecordingMode mode) throws IOException {
        long guildId = guild.getIdLong();
        if (recordings.containsKey(guildId)) {
            throw new RecordingStateException("Un enregistrement est déjà en cours sur ce serveur.");
        }

        AudioManager audioManager = guild.getAudioManager();
        if (audioManager.isConnected() && audioManager.getConnectedChannel().getIdLong() != channel.getIdLong()) {
            throw new RecordingStateException("Je suis déjà connecté à un autre salon vocal.");
        }

        VoiceRecordingSession session = new VoiceRecordingSession(
                guild.getId(),
                channel.getName(),
                mode,
                sessionDirectory(guildId),
                Instant.now(),
                config.mp3BitrateKbps());
        ActiveRecording activeRecording = new ActiveRecording(
                session,
                channel.getIdLong(),
                channel.getName(),
                outputChannel,
                startedBy.getIdLong());

        recordings.put(guildId, activeRecording);
        audioManager.setSelfDeafened(false);
        audioManager.setReceivingHandler(new VoiceRecordingReceiveHandler(session, guild.getSelfMember().getId()));

        if (!audioManager.isConnected()) {
            audioManager.openAudioConnection(channel);
        }

        requestToSpeakOnStage(channel, audioManager);
        refreshEmptyChannelTimeout(guild);
    }

    public synchronized RecordingStopResult stopRecording(Guild guild) throws IOException {
        long guildId = guild.getIdLong();
        ActiveRecording activeRecording = recordings.remove(guildId);
        if (activeRecording == null) {
            throw new RecordingStateException("Aucun enregistrement n'est en cours sur ce serveur.");
        }

        cancelEmptyChannelTimeout(guildId);
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setReceivingHandler(null);

        RecordingStopResult result = activeRecording.session().stop(transcoder);
        if (!PlayerManager.getInstance().isMusicPlaying(guild)) {
            audioManager.closeAudioConnection();
        }

        return result;
    }

    public Optional<RecordingStatus> getStatus(long guildId) {
        ActiveRecording activeRecording = recordings.get(guildId);
        if (activeRecording == null) {
            return Optional.empty();
        }

        VoiceRecordingSession session = activeRecording.session();
        return Optional.of(new RecordingStatus(
                activeRecording.channelName(),
                session.mode(),
                session.startedAt(),
                Duration.between(session.startedAt(), Instant.now()),
                session.openFileCount()));
    }

    public boolean hasActiveRecording(long guildId) {
        return recordings.containsKey(guildId);
    }

    public boolean isRecordingChannel(long guildId, long channelId) {
        ActiveRecording activeRecording = recordings.get(guildId);
        return activeRecording != null && activeRecording.channelId() == channelId;
    }

    public void recordBotAudio(long guildId, byte[] pcm) {
        ActiveRecording activeRecording = recordings.get(guildId);
        if (activeRecording == null) {
            return;
        }

        try {
            activeRecording.session().recordBotAudio(pcm);
        } catch (IOException e) {
            logger.error("Impossible d'écrire la piste musique du bot", e);
        }
    }

    public void refreshEmptyChannelTimeout(Guild guild) {
        long guildId = guild.getIdLong();
        ActiveRecording activeRecording = recordings.get(guildId);
        if (activeRecording == null) {
            cancelEmptyChannelTimeout(guildId);
            return;
        }

        AudioManager audioManager = guild.getAudioManager();
        if (!audioManager.isConnected() || audioManager.getConnectedChannel() == null) {
            return;
        }

        long realMembersCount = audioManager.getConnectedChannel().getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        if (realMembersCount > 0) {
            cancelEmptyChannelTimeout(guildId);
            return;
        }

        emptyStopTasks.computeIfAbsent(guildId, ignored -> scheduler.schedule(
                () -> stopAfterEmptyTimeout(guild),
                config.emptyChannelTimeout().toSeconds(),
                TimeUnit.SECONDS));
    }

    private void stopAfterEmptyTimeout(Guild guild) {
        long guildId = guild.getIdLong();
        ActiveRecording activeRecording = recordings.get(guildId);
        if (activeRecording == null) {
            return;
        }

        AudioManager audioManager = guild.getAudioManager();
        if (audioManager.isConnected() && audioManager.getConnectedChannel() != null) {
            long realMembersCount = audioManager.getConnectedChannel().getMembers().stream()
                    .filter(member -> !member.getUser().isBot())
                    .count();
            if (realMembersCount > 0) {
                cancelEmptyChannelTimeout(guildId);
                return;
            }
        }

        try {
            RecordingStopResult result = stopRecording(guild);
            String message = "Salon vocal vide depuis %d secondes, enregistrement arrêté. Durée: %s."
                    .formatted(config.emptyChannelTimeout().toSeconds(), formatDuration(result.duration()));
            RecordingDiscordUploader.upload(activeRecording.outputChannel(), result, message, failure ->
                    activeRecording.outputChannel()
                            .sendMessage("Impossible d'envoyer l'enregistrement: " + failure.getMessage())
                            .queue());
        } catch (Exception e) {
            logger.error("Impossible d'arrêter automatiquement l'enregistrement", e);
        }
    }

    private void cancelEmptyChannelTimeout(long guildId) {
        ScheduledFuture<?> task = emptyStopTasks.remove(guildId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private Path sessionDirectory(long guildId) {
        return config.tempDirectory().resolve(String.valueOf(guildId)).resolve(String.valueOf(System.currentTimeMillis()));
    }

    private void requestToSpeakOnStage(AudioChannel channel, AudioManager audioManager) {
        audioManager.setConnectionListener(new ConnectionListener() {
            @Override
            public void onStatusChange(ConnectionStatus status) {
                if (status != ConnectionStatus.CONNECTED) {
                    return;
                }
                if (channel.getType() == ChannelType.STAGE && channel instanceof StageChannel stageChannel) {
                    stageChannel.requestToSpeak().queue(
                            ignored -> audioManager.setConnectionListener(null),
                            error -> audioManager.setConnectionListener(null));
                } else {
                    audioManager.setConnectionListener(null);
                }
            }
        });
    }

    public static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) {
            return "%dh %02dm %02ds".formatted(hours, minutes, remainingSeconds);
        }
        return "%dm %02ds".formatted(minutes, remainingSeconds);
    }

    private record ActiveRecording(
            VoiceRecordingSession session,
            long channelId,
            String channelName,
            GuildMessageChannel outputChannel,
            long startedById) {
    }

    public static class RecordingStateException extends RuntimeException {
        public RecordingStateException(String message) {
            super(message);
        }
    }
}
