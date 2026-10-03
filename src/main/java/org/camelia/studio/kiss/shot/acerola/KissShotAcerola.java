package org.camelia.studio.kiss.shot.acerola;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import org.camelia.studio.kiss.shot.acerola.api.ApiClient;
import org.camelia.studio.kiss.shot.acerola.listeners.bot.ReadyListener;
import org.camelia.studio.kiss.shot.acerola.managers.ListenerManager;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.camelia.studio.kiss.shot.acerola.utils.OpusNativeLibraryLoader;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KissShotAcerola {
    private static JDA jda;
    private static final Logger logger = LoggerFactory.getLogger(KissShotAcerola.class);

    public static void main(String[] args) {
        try {
            Configuration.getInstance();
            OpusNativeLibraryLoader.preloadFromEnvironment();
            ApiClient.getInstance();

            jda = JDABuilder.createDefault(Configuration.getInstance().getDotenv().get("BOT_TOKEN"))
                    .addEventListeners(new ReadyListener())
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setAudioModuleConfig(
                            new AudioModuleConfig()
                                    .withDaveSessionFactory(new JDaveSessionFactory()))
                    .build()
                    .awaitReady();

            new ListenerManager().registerListeners(jda);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> jda.shutdown()));
        } catch (Exception e) {
            logger.error("Une erreur est survenue lors de l'exécution du bot : {}", e.getMessage());
            System.exit(1);
        }
    }

    public static JDA getJda() {
        return jda;
    }

}
