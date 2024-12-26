package org.camelia.studio.kiss.shot.acerola.commands.audio;

import java.util.List;

import org.camelia.studio.kiss.shot.acerola.audio.GuildMusicManager;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;

public class RepeatCommand implements ISlashCommand {

    @Override
    public String getName() {
        return "repeat";
    }

    @Override
    public String getDescription() {
        return "Permet de répéter la musique en cours";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, "mode", "Le mode de répétition").addChoice("Toute la queue", "all")
                        .addChoice("La musique actuelle", "one").addChoice("Désactiver la répétition", "off")
                        .setRequired(true));
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
            event.getHook()
                    .editOriginal("Vous devez être dans le même salon vocal que moi pour utiliser cette commande !")
                    .queue();
            return;
        }

        String mode = event.getOption("mode").getAsString();
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());

        switch (mode) {
            case "all":
                musicManager.scheduler.setLoop(true);
                musicManager.scheduler.setRepeat(false);

                event.getHook().editOriginal("Toute la queue sera répétée !").queue();
                break;
            case "one":
                musicManager.scheduler.setLoop(false);
                musicManager.scheduler.setRepeat(true);

                event.getHook().editOriginal("La musique actuelle sera répétée !").queue();
                break;
            case "off":
                musicManager.scheduler.setLoop(false);
                musicManager.scheduler.setRepeat(false);

                event.getHook().editOriginal("La répétition a été désactivée !").queue();
                break;
        }
    }

}
