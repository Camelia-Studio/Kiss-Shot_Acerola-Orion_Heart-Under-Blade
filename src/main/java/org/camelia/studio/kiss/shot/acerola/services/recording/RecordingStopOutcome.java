package org.camelia.studio.kiss.shot.acerola.services.recording;

import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

public record RecordingStopOutcome(RecordingStopResult result, GuildMessageChannel outputChannel) {
}
