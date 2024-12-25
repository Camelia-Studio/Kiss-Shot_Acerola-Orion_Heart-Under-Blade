package org.camelia.studio.kiss.shot.acerola.commands.audio;

import java.util.List;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;

public class SkipCommand implements ISlashCommand {

    @Override
    public String getName() {
        return "skip";
    }

    @Override
    public String getDescription() {
        return "Permet de passer à la musique suivante";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.INTEGER, "tracknumber", "Nombre de musique à passer").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        OptionMapping option = event.getOption("tracknumber");
        int skipAmount = 1;

        if (option != null) {
            skipAmount = (int) option.getAsInt();
        }

        // Vérifier si l'utilisateur est dans un canal vocal
        GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (!voiceState.inAudioChannel()) {
            event.getHook().editOriginal("Vous devez être dans un canal vocal pour utiliser cette commande !").queue();
            return;
        }

        // Vérifier si le bot est dans le même canal vocal
        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            event.getHook().editOriginal("Je ne suis pas connecté à un canal vocal !").queue();
            return;
        }

        if (voiceState.getChannel() != audioManager.getConnectedChannel()) {
            event.getHook().editOriginal("Vous devez être dans le même canal vocal que moi !").queue();
            return;
        }

        // On passe aux musiques suivantes
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        musicManager.scheduler.nextTrack(skipAmount);

        event.getHook().editOriginal("Passage de %d musiques.".formatted(skipAmount)).queue();
    }

}
