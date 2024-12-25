package org.camelia.studio.kiss.shot.acerola.audio;

import java.util.LinkedList;
import java.util.Queue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final Queue<AudioTrack> queue;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedList<>();
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }

    public void nextTrack() {
        player.startTrack(queue.poll(), false);
    }

    public void nextTrack(int nextTrack) {
        if (nextTrack < 1) {
            return;
        }
        for (int i = 0; i < nextTrack - 1; i++) {
            queue.poll();
        }
        player.startTrack(queue.poll(), false);
    }

    public Queue<AudioTrack> getQueue() {
        return queue;
    }
 
    public void clearQueue() {
        queue.clear();
    }
}