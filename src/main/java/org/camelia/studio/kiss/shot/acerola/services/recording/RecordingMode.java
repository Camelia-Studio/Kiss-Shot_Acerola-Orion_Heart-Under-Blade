package org.camelia.studio.kiss.shot.acerola.services.recording;

public enum RecordingMode {
    MIX("mix"),
    TRACKS("tracks");

    private final String optionValue;

    RecordingMode(String optionValue) {
        this.optionValue = optionValue;
    }

    public String optionValue() {
        return optionValue;
    }

    public static RecordingMode fromOption(String value) {
        if (TRACKS.optionValue.equalsIgnoreCase(value)) {
            return TRACKS;
        }
        return MIX;
    }
}
