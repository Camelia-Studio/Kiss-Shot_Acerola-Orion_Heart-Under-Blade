package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationChannelTypesTest {
    private static final Set<ChannelType> EXPECTED_TYPES = EnumSet.of(
            ChannelType.TEXT,
            ChannelType.NEWS,
            ChannelType.GUILD_NEWS_THREAD,
            ChannelType.GUILD_PUBLIC_THREAD,
            ChannelType.GUILD_PRIVATE_THREAD,
            ChannelType.VOICE,
            ChannelType.STAGE);

    @Test
    void includesEveryMessageCapableGuildChannel() {
        assertEquals(EXPECTED_TYPES, ConfigurationChannelTypes.guildMessageChannels());
    }

    @Test
    void slashLogChannelOptionUsesTheCentralChannelTypes() {
        SubcommandData logChannel = new ConfigCommand().getSubcommands().stream()
                .filter(subcommand -> subcommand.getName().equals("log-channel"))
                .findFirst()
                .orElseThrow();
        OptionData channel = logChannel.getOptions().getFirst();

        assertEquals(EXPECTED_TYPES, channel.getChannelTypes());
    }
}
