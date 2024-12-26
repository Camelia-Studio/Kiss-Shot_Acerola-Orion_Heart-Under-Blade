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
    private boolean loop = false;
    private boolean repeat = false;

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
        AudioTrack track = queue.poll();

        if (loop) {
            queue.offer(track.makeClone());
        } else if (repeat) {
            // ON ajoute la track au début de la queue
            LinkedList<AudioTrack> list = new LinkedList<>(queue);
            queue.clear();
            queue.offer(track.makeClone());
            while (!list.isEmpty()) {
                queue.offer(list.poll());
            }
        }
        player.startTrack(track, false);
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

    public void shuffle() {
        LinkedList<AudioTrack> list = new LinkedList<>(queue);
        queue.clear();
        while (!list.isEmpty()) {
            int index = (int) (Math.random() * list.size());
            queue.offer(list.remove(index));
        }
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }
}