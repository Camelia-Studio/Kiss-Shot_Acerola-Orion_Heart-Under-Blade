package org.camelia.studio.kiss.shot.acerola.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingService;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class AudioPlayerSendHandler implements AudioSendHandler {
    private final AudioPlayer audioPlayer;
    private final long guildId;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;

    public AudioPlayerSendHandler(AudioPlayer audioPlayer, long guildId) {
        this.audioPlayer = audioPlayer;
        this.guildId = guildId;
        this.buffer = ByteBuffer.allocate(4096);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        return audioPlayer.provide(frame);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        ((Buffer) buffer).flip();
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] pcm = new byte[copy.remaining()];
        copy.get(pcm);
        RecordingService.getInstance().recordBotAudio(guildId, pcm);
        return buffer;
    }

    @Override
    public boolean isOpus() {
        return false;
    }
}
