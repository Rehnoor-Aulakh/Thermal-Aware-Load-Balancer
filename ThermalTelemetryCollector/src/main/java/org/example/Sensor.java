package org.example;

import java.util.Locale;

/**
 * One sensor reported by LibreHardwareMonitor.
 */
public record Sensor(
        String id,
        String name,
        String type,
        double value
) {
    public boolean hasIdPrefix(String prefix) {
        return id.toLowerCase(Locale.ROOT)
                .startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    public boolean nameIs(String expected) {
        return name.equalsIgnoreCase(expected);
    }

    public boolean nameContains(String text) {
        return name.toLowerCase(Locale.ROOT)
                .contains(text.toLowerCase(Locale.ROOT));
    }

    public boolean typeIs(String expected) {
        return type.equalsIgnoreCase(expected);
    }
}
