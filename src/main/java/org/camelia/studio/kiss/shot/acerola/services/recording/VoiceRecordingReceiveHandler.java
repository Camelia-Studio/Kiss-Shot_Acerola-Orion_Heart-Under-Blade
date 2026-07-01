package org.camelia.studio.kiss.shot.acerola.services.recording;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class VoiceRecordingReceiveHandler implements AudioReceiveHandler {
    private static final Logger logger = LoggerFactory.getLogger(VoiceRecordingReceiveHandler.class);

    private final VoiceRecordingSession session;
    private final String selfUserId;

    public VoiceRecordingReceiveHandler(VoiceRecordingSession session, String selfUserId) {
        this.session = session;
        this.selfUserId = selfUserId;
    }

    @Override
    public boolean canReceiveCombined() {
        return session.mode() == RecordingMode.MIX;
    }

    @Override
    public boolean canReceiveUser() {
        return session.mode() == RecordingMode.TRACKS;
    }

    @Override
    public void handleCombinedAudio(CombinedAudio combinedAudio) {
        try {
            session.recordCombinedAudio(combinedAudio.getAudioData(1.0));
        } catch (IOException e) {
            logger.error("Impossible d'écrire l'audio mixé Discord", e);
        }
    }

    @Override
    public void handleUserAudio(UserAudio userAudio) {
        User user = userAudio.getUser();
        if (user.getId().equals(selfUserId)) {
            return;
        }

        try {
            session.recordUserAudio(user.getId(), user.getEffectiveName(), userAudio.getAudioData(1.0));
        } catch (IOException e) {
            logger.error("Impossible d'écrire l'audio de {}", user.getAsTag(), e);
        }
    }

    @Override
    public boolean includeUserInCombinedAudio(User user) {
        return !user.getId().equals(selfUserId);
    }
}
