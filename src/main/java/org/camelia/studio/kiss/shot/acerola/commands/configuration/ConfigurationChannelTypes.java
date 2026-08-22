package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.entities.channel.ChannelType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

final class ConfigurationChannelTypes {
    private static final Set<ChannelType> GUILD_MESSAGE_CHANNELS = Collections.unmodifiableSet(
            ChannelType.guildTypes().stream()
                    .filter(ChannelType::isMessage)
                    .collect(() -> EnumSet.noneOf(ChannelType.class), EnumSet::add, EnumSet::addAll));

    private ConfigurationChannelTypes() {
    }

    static Set<ChannelType> guildMessageChannels() {
        return GUILD_MESSAGE_CHANNELS;
    }
}
