package org.camelia.studio.kiss.shot.acerola.commands.audio;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

public class ShuffleCommand implements ISlashCommand {

    @Override
    public String getName() {
        return "shuffle";
    }

    @Override
    public String getDescription() {
        return "Permet de mélanger la file d'attente";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

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
        musicManager.scheduler.shuffle();

        event.getHook().editOriginal("La file d'attente a été mélangée !").queue();
    }

}
