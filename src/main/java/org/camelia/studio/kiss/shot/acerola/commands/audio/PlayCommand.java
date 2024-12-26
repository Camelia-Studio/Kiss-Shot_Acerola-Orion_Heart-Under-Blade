package org.camelia.studio.kiss.shot.acerola.commands.audio;

import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.List;

import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

public class PlayCommand implements ISlashCommand {
    @Override
    public String getName() {
        return "play";
    }

    @Override
    public String getDescription() {
        return "Permet de lancer une musique en .mp3 dans un salon vocal";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, "url", "URL de la musique à jouer", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String url = event.getOption("url").getAsString();
        Member member = event.getMember();
        GuildVoiceState voiceState = member.getVoiceState();

        if (!voiceState.inAudioChannel()) {
            event.getHook().editOriginal("Vous devez être connecté à un salon vocal pour utiliser cette commande !")
                    .queue();
            return;
        }

        AudioManager audioManager = event.getGuild().getAudioManager();

        audioManager.setConnectionListener(new ConnectionListener() {
            @Override
            public void onStatusChange(ConnectionStatus status) {
                if (status == ConnectionStatus.CONNECTED) {
                    if (voiceState.getChannel().getType() == ChannelType.STAGE) {
                        voiceState.getChannel().asStageChannel().requestToSpeak().queue(speakSuccess -> {
                            audioManager.setConnectionListener(null);
                        }, error -> {
                            audioManager.setConnectionListener(null);
                        });
                    } else {
                        audioManager.setConnectionListener(null);
                    }
                }
            }
        });

        if (!audioManager.isConnected()) {
            audioManager.openAudioConnection(voiceState.getChannel());
            PlayerManager.getInstance().getMusicManager(event.getGuild()).audioPlayer.setVolume(25);
        }

        PlayerManager.getInstance().loadAndPlay(event.getChannel().asGuildMessageChannel(), url);
        event.getHook().editOriginal("Chargement du fichier audio en cours...").queue();
    }
}