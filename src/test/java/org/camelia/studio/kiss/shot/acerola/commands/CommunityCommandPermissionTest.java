package org.camelia.studio.kiss.shot.acerola.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import org.camelia.studio.kiss.shot.acerola.commands.audio.NowPlayingCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.PauseCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.PlayCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.QueueCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.RecordCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.RepeatCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.ShuffleCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.SkipCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.StopCommand;
import org.camelia.studio.kiss.shot.acerola.commands.audio.VolumeCommand;
import org.camelia.studio.kiss.shot.acerola.commands.utils.MsgEditCommand;
import org.camelia.studio.kiss.shot.acerola.commands.utils.MsgSendCommand;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityCommandPermissionTest {
    @Test
    void musicCommandsStayOpenToEveryone() {
        List<ISlashCommand> musicCommands = List.of(
                new NowPlayingCommand(),
                new PauseCommand(),
                new PlayCommand(),
                new QueueCommand(),
                new RepeatCommand(),
                new ShuffleCommand(),
                new SkipCommand(),
                new StopCommand(),
                new VolumeCommand());

        assertTrue(musicCommands.stream().allMatch(command -> command.requiredPermissions().isEmpty()));
        assertTrue(musicCommands.stream().allMatch(
                command -> command.defaultPermissions() == DefaultMemberPermissions.ENABLED));
    }

    @Test
    void recordingUsesManageChannelAsItsDiscordDefaultAndCanBeRelaxedThere() {
        RecordCommand command = new RecordCommand();

        assertEquals(Permission.MANAGE_CHANNEL.getRawValue(), command.defaultPermissions().getPermissionsRaw());
        assertTrue(command.requiredPermissions().isEmpty());
    }

    @Test
    void botMessageCommandsRequireManageMessagesAtRuntime() {
        MsgSendCommand send = new MsgSendCommand();
        MsgEditCommand edit = new MsgEditCommand();

        assertEquals(Set.of(Permission.MESSAGE_MANAGE), send.requiredPermissions());
        assertEquals(Set.of(Permission.MESSAGE_MANAGE), edit.requiredPermissions());
        assertEquals(Permission.MESSAGE_MANAGE.getRawValue(), send.defaultPermissions().getPermissionsRaw());
        assertEquals(Permission.MESSAGE_MANAGE.getRawValue(), edit.defaultPermissions().getPermissionsRaw());
    }
}
