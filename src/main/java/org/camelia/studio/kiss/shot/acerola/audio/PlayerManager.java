package org.camelia.studio.kiss.shot.acerola.audio;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private static volatile PlayerManager INSTANCE;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;

    public PlayerManager() {
        this.musicManagers = new ConcurrentHashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();
        this.audioPlayerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
    }

    public static PlayerManager getInstance() {
        PlayerManager instance = INSTANCE;
        if (instance == null) {
            synchronized (PlayerManager.class) {
                instance = INSTANCE;
                if (instance == null) {
                    instance = new PlayerManager();
                    INSTANCE = instance;
                }
            }
        }
        return instance;
    }

    public static boolean isMusicPlayingIfInitialized(Guild guild) {
        return INSTANCE != null && INSTANCE.isMusicPlaying(guild);
    }

    public static void cleanupGuildIfInitialized(Guild guild) {
        if (INSTANCE != null) {
            INSTANCE.cleanupGuild(guild);
        }
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            final GuildMusicManager guildMusicManager = new GuildMusicManager(audioPlayerManager, guildId);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public boolean isMusicPlaying(Guild guild) {
        GuildMusicManager musicManager = musicManagers.get(guild.getIdLong());
        return musicManager != null && musicManager.audioPlayer.getPlayingTrack() != null;
    }

    public void cleanupGuild(Guild guild) {
        GuildMusicManager musicManager = musicManagers.remove(guild.getIdLong());
        if (musicManager != null) {
            synchronized (musicManager) {
                musicManager.destroy();
            }
        }

        var audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(null);
        if (!RecordingService.hasActiveRecordingIfInitialized(guild.getIdLong())) {
            audioManager.closeAudioConnection();
        }
    }

    public void loadAndPlay(GuildMessageChannel channel, String url) {
        final GuildMusicManager musicManager = getMusicManager(channel.getGuild());

        audioPlayerManager.loadItemOrdered(musicManager, url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                withCurrentManager(channel.getGuild(), musicManager, () -> {
                    musicManager.scheduler.queue(track);
                    channel.sendMessage("Ajout à la file d'attente: `" + track.getInfo().title + "`").queue();
                });
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                withCurrentManager(channel.getGuild(), musicManager, () -> {
                    List<AudioTrack> tracks = playlist.getTracks();
                    channel.sendMessage(
                            "Ajout à la file d'attente: `" + playlist.getName() + "` - "
                                    + tracks.size() + " musiques.")
                            .queue();
                    for (AudioTrack track : tracks) {
                        musicManager.scheduler.queue(track);
                    }
                });
            }

            @Override
            public void noMatches() {
                withCurrentManager(
                        channel.getGuild(),
                        musicManager,
                        () -> channel.sendMessage("Aucun résultat trouvé pour: " + url).queue());
            }

            @Override
            public void loadFailed(FriendlyException e) {
                withCurrentManager(
                        channel.getGuild(),
                        musicManager,
                        () -> channel.sendMessage("Erreur lors du chargement: " + e.getMessage()).queue());
            }
        });
    }

    private void withCurrentManager(Guild guild, GuildMusicManager musicManager, Runnable action) {
        synchronized (musicManager) {
            if (musicManagers.get(guild.getIdLong()) == musicManager) {
                action.run();
            }
        }
    }
}
