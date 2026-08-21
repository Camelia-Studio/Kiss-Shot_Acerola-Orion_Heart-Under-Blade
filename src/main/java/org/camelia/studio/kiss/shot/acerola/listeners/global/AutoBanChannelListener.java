package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.moderation.AutomaticSanctionExecutor;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ModerationSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AutoBanChannelListener extends ModuleAwareListener {

    private final AutomaticSanctionExecutor sanctionExecutor = new AutomaticSanctionExecutor();

    public AutoBanChannelListener() {
        super(ModuleType.AUTO_SANCTION_CHANNEL);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        var configuration = resolved.get();
        Set<String> watchedChannelIds = configuration.channels(ModuleResourcePurpose.WATCHED);
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;

        Member member = event.getMember();
        if (member == null) return;
        String channelMention = event.getChannel().getAsMention();
        ModerationSettings.AutomaticSanction settings = ModerationSettings.automaticSanction(configuration);
        settings.action().ifPresent(action -> sanctionExecutor.execute(
                member,
                event.getMessage(),
                configuration,
                action,
                settings.timeout(),
                settings.banHistoryDays(),
                settings.deleteMessage(),
                "Publication dans un salon déclencheur",
                "Salon : " + channelMention));
    }
}
