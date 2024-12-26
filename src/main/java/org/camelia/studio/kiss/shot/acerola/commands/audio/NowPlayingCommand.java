package org.camelia.studio.kiss.shot.acerola.commands.audio;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

import java.awt.Color;

public class NowPlayingCommand implements ISlashCommand {
    @Override
    public String getName() {
        return "nowplaying";
    }

    @Override
    public String getDescription() {
        return "Permet de voir la musique en cours de lecture";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            event.getHook().editOriginal("Je ne suis pas connecté à un canal vocal !").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioTrack playingTrack = musicManager.audioPlayer.getPlayingTrack();

        if (playingTrack == null) {
            event.getHook().editOriginal("La file d'attente est vide.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("En cours de lecture");
        embed.setColor(Color.ORANGE);
        embed.addField("Titre", playingTrack.getInfo().title, false);
        embed.addField("Durée", formatTime(playingTrack.getDuration()), false);
        embed.addField("Auteur", playingTrack.getInfo().author, false);
        embed.addField("Lien", playingTrack.getInfo().uri, false);
        embed.setImage(playingTrack.getInfo().artworkUrl);

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private String formatTime(long timeInMillis) {
        long hours = timeInMillis / 3600000;
        long minutes = (timeInMillis % 3600000) / 60000;
        long seconds = (timeInMillis % 60000) / 1000;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
