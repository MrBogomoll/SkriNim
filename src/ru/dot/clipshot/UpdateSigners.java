package ru.dot.clipshot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

enum UpdateSigners {
    SAME, ROTATED, INCOMPATIBLE, UNKNOWN;

    // Android supplies verified certificates. For an ordinary forward update,
    // the downloaded APK must prove descent from the installed current signer.
    static <T> UpdateSigners compare(T[] installed, T[] downloaded, T[] downloadedHistory) {
        if (installed == null || installed.length == 0 || downloaded == null || downloaded.length == 0)
            return UNKNOWN;
        Set<T> current = new HashSet<>(Arrays.asList(installed));
        Set<T> next = new HashSet<>(Arrays.asList(downloaded));
        if (current.contains(null) || next.contains(null)
                || current.size() != installed.length || next.size() != downloaded.length) return UNKNOWN;
        if (current.equals(next)) return SAME;
        if (installed.length != 1 || downloaded.length != 1) return INCOMPATIBLE;
        if (downloadedHistory == null || downloadedHistory.length == 0
                || !downloaded[0].equals(downloadedHistory[downloadedHistory.length - 1])) return UNKNOWN;
        return Arrays.asList(downloadedHistory).contains(installed[0]) ? ROTATED : INCOMPATIBLE;
    }
}
