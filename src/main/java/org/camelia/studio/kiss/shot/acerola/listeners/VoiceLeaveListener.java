package org.camelia.studio.kiss.shot.acerola.listeners;

import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

public class VoiceLeaveListener extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();
        AudioManager audioManager = guild.getAudioManager();
        
        // Vérifie si le bot est connecté à un canal vocal
        if (!audioManager.isConnected()) {
            return;
        }

        VoiceChannel botChannel = audioManager.getConnectedChannel().asVoiceChannel();
        
        // Compte le nombre de membres dans le canal (excluant les bots)
        long realMembersCount = botChannel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        // Si plus personne dans le salon
        if (realMembersCount == 0) {
            // Arrête la musique
            PlayerManager.getInstance().getMusicManager(guild).audioPlayer.stopTrack();
            
            // Déconnecte le bot
            audioManager.closeAudioConnection();
        }
    }
}