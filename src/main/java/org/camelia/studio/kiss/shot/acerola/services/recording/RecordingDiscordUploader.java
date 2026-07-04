package org.camelia.studio.kiss.shot.acerola.services.recording;

import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RecordingDiscordUploader {
    private static final Logger logger = LoggerFactory.getLogger(RecordingDiscordUploader.class);
    private static final int MAX_FILES_PER_MESSAGE = 10;

    private RecordingDiscordUploader() {
    }

    public static void upload(
            GuildMessageChannel channel,
            RecordingStopResult result,
            String message,
            Consumer<Throwable> failureHandler) {
        List<RecordingArtifact> uploadableFiles = result.files()
                .stream()
                .filter(file -> isUploadable(channel, file))
                .toList();

        if (uploadableFiles.isEmpty()) {
            String emptyMessage = result.files().isEmpty()
                    ? "Aucun fichier audio n'a été généré."
                    : "Aucun fichier audio ne respecte la limite d'upload Discord.";
            channel.sendMessage(message + "\n" + emptyMessage).queue(
                    success -> result.deleteFilesQuietly(),
                    failure -> {
                        result.deleteFilesQuietly();
                        failureHandler.accept(failure);
                    });
            return;
        }

        List<List<RecordingArtifact>> batches = batches(uploadableFiles);
        AtomicInteger remaining = new AtomicInteger(batches.size());

        for (int i = 0; i < batches.size(); i++) {
            List<FileUpload> uploads = batches.get(i)
                    .stream()
                    .map(file -> FileUpload.fromData(file.path(), file.fileName()))
                    .toList();
            String content = i == 0 ? message : "Suite de l'enregistrement.";

            channel.sendFiles(uploads).addContent(content).queue(
                    success -> closeUploads(uploads, result, remaining),
                    failure -> {
                        closeUploads(uploads, result, remaining);
                        failureHandler.accept(failure);
                    });
        }
    }

    private static boolean isUploadable(GuildMessageChannel channel, RecordingArtifact file) {
        try {
            long maxFileSize = channel.getGuild().getMaxFileSize();
            long size = Files.size(file.path());
            if (size <= maxFileSize) {
                return true;
            }
            channel.sendMessage(
                    "Le fichier `%s` dépasse la limite Discord de %d Mo après encodage MP3."
                            .formatted(file.fileName(), maxFileSize / 1024 / 1024))
                    .queue();
            return false;
        } catch (IOException e) {
            logger.warn("Impossible de vérifier la taille de {}", file.path(), e);
            return true;
        }
    }

    private static List<List<RecordingArtifact>> batches(List<RecordingArtifact> files) {
        List<List<RecordingArtifact>> batches = new ArrayList<>();
        for (int i = 0; i < files.size(); i += MAX_FILES_PER_MESSAGE) {
            batches.add(files.subList(i, Math.min(i + MAX_FILES_PER_MESSAGE, files.size())));
        }
        return batches;
    }

    private static void closeUploads(
            List<FileUpload> uploads,
            RecordingStopResult result,
            AtomicInteger remaining) {
        for (FileUpload upload : uploads) {
            try {
                upload.close();
            } catch (IOException e) {
                logger.warn("Impossible de fermer l'upload Discord {}", upload.getName(), e);
            }
        }
        if (remaining.decrementAndGet() == 0) {
            result.deleteFilesQuietly();
        }
    }
}
