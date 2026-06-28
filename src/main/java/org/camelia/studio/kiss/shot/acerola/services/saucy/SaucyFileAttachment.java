package org.camelia.studio.kiss.shot.acerola.services.saucy;

public record SaucyFileAttachment(String fileName, byte[] data, String contentType) {
    public SaucyFileAttachment {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public long size() {
        return data.length;
    }
}
