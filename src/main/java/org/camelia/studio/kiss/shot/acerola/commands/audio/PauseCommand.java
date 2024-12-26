package org.camelia.studio.kiss.shot.acerola.commands.audio;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

public class PauseCommand implements ISlashCommand {
    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public String getDescription() {
        return "Permet de mettre en pause la musique en cours de lecture";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        Member member = event.getMember();
        GuildVoiceState voiceState = member.getVoiceState();

        if (!voiceState.inAudioChannel()) {
            event.getHook().editOriginal("Vous devez être connecté à un salon vocal pour utiliser cette commande !")
                    .queue();
            return;
        }

        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            event.getHook().editOriginal("Je ne suis pas connecté à un canal vocal !").queue();
            return;
        }

        if (member.getVoiceState().getChannel() != audioManager.getConnectedChannel()) {
            event.getHook().editOriginal("Vous devez être dans le même salon vocal que moi pour utiliser cette commande !")
                .queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());

        boolean isPaused = musicManager.audioPlayer.isPaused();

        musicManager.audioPlayer.setPaused(!isPaused);

        if (isPaused) {
            event.getHook().editOriginal("La musique a été reprise !").queue();
            return;
        }

        event.getHook().editOriginal("La musique est en pause !").queue();
    }
}