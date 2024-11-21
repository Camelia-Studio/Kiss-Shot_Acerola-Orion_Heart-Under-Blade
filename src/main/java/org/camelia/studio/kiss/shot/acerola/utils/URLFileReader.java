package org.camelia.studio.kiss.shot.acerola.utils;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class URLFileReader {
    public static String readFileFromURL(String fileUrl) throws Exception {
        URL url = new URI(fileUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } finally {
            conn.disconnect();
        }

        return content.toString();
    }
}