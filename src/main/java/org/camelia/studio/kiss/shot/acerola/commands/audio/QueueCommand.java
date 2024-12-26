package org.camelia.studio.kiss.shot.acerola.commands.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;

import java.awt.Color;

public class QueueCommand implements ISlashCommand {
    final int TRACKS_PER_PAGE = 10;

    @Override
    public String getName() {
        return "queue";
    }

    @Override
    public String getDescription() {
        return "Permet de voir la file d'attente";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.INTEGER, "page", "Numéro de la page à visionner").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        OptionMapping option = event.getOption("page");
        int page = 1;

        if (option != null) {
            page = (int) option.getAsInt();
        }

        if (page < 1) {
            event.getHook().editOriginal("La page doit être supérieure à 0.").queue();
            return;
        }

        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            event.getHook().editOriginal("Je ne suis pas connecté à un canal vocal !").queue();
            return;
        }

        // On passe aux musiques suivantes
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        Queue<AudioTrack> queue = musicManager.scheduler.getQueue();

        if (queue.isEmpty()) {
            event.getHook().editOriginal("La file d'attente est vide.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎵 File d'attente");
        embed.setColor(Color.BLUE);

        // Calculer le nombre total de pages
        int totalPages = (int) Math.ceil((double) queue.size() / TRACKS_PER_PAGE);
        if (totalPages == 0)
            totalPages = 1;

        // Vérifier que la page demandée est valide
        if (page < 1 || page > totalPages) {
            event.getHook().editOriginal("❌ Page invalide ! (1-" + totalPages + ")").queue();
            return;
        }

        // Afficher la file d'attente pour la page demandée
        if (queue.isEmpty()) {
            embed.setDescription("Aucune musique dans la file d'attente");
        } else {
            List<AudioTrack> trackList = new ArrayList<>(queue);
            int startIndex = (page - 1) * TRACKS_PER_PAGE;
            int endIndex = Math.min(startIndex + TRACKS_PER_PAGE, trackList.size());

            StringBuilder queueList = new StringBuilder();
            for (int i = startIndex; i < endIndex; i++) {
                AudioTrack track = trackList.get(i);
                queueList.append(i + 1)
                        .append(". `")
                        .append(track.getInfo().title)
                        .append("` [")
                        .append(formatTime(track.getDuration()))
                        .append("]\n");
            }

            embed.setDescription(queueList.toString());
        }

        long totalDuration = queue.stream().mapToLong(AudioTrack::getDuration).sum();
        embed.setFooter(String.format("Page %d/%d • %d musiques • Durée totale: %s",
                page, totalPages, queue.size(), formatTime(totalDuration)));

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
