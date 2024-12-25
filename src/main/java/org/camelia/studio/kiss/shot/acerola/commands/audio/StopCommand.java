package org.camelia.studio.kiss.shot.acerola.commands.audio;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

public class StopCommand implements ISlashCommand {

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getDescription() {
        return "Permet de stopper la musique en cours";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Vérifier si l'utilisateur est dans un canal vocal
        GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (!voiceState.inAudioChannel()) {
            event.reply("Vous devez être dans un canal vocal pour utiliser cette commande !").queue();
            return;
        }

        // Vérifier si le bot est dans le même canal vocal
        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            event.reply("Je ne suis pas connecté à un canal vocal !").queue();
            return;
        }

        if (voiceState.getChannel() != audioManager.getConnectedChannel()) {
            event.reply("Vous devez être dans le même canal vocal que moi !").queue();
            return;
        }

        // Arrêter la musique
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        musicManager.audioPlayer.stopTrack();

        // Déconnecter le bot
        audioManager.closeAudioConnection();

        event.reply("Musique arrêtée et déconnexion du canal vocal.").queue();
    }

}
