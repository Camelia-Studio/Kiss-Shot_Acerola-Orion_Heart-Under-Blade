package org.camelia.studio.kiss.shot.acerola.commands.audio;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.List;

public class VolumeCommand implements ISlashCommand {

    @Override
    public String getName() {
        return "volume";
    }

    @Override
    public String getDescription() {
        return "Permet de changer le volume du bot";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.INTEGER, "volume", "Le volume souhaité").setRequired(true).setMinValue(0).setMaxValue(100)
        );
    }

    @Override
    public java.util.Optional<org.camelia.studio.kiss.shot.acerola.models.ModuleType> requiredModule() {
        return java.util.Optional.of(org.camelia.studio.kiss.shot.acerola.models.ModuleType.MUSIC);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (!voiceState.inAudioChannel()) {
            event.reply("Vous devez être dans un canal vocal pour utiliser cette commande !").queue();
            return;
        }

        AudioManager audioManager = event.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            event.getHook().editOriginal("Je ne suis pas connecté à un canal vocal !").queue();
            return;
        }

        if (voiceState.getChannel() != audioManager.getConnectedChannel()) {
            event.getHook().editOriginal("Vous devez être dans le même canal vocal que moi !").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());

        int volume = Integer.parseInt(event.getOption("volume").getAsString());

        musicManager.audioPlayer.setVolume(volume);

        event.getHook().editOriginal("Le volume a été changé à " + volume + "%").queue();
    }
}
