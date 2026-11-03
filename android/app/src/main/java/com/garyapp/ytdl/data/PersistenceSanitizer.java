package com.garyapp.ytdl.data;

import com.garyapp.ytdl.core.privacy.SensitiveText;

final class PersistenceSanitizer {
    private static final String HIDDEN = "[已隐藏]";

    private PersistenceSanitizer() {
    }

    static String clean(String value) {
        if (value == null) {
            return null;
        }

        return SensitiveText.INSTANCE.redact(value)
                .replaceAll("(?i)--cookies(?:=|\\s+)\"[^\"]*\"", HIDDEN)
                .replaceAll("(?i)--cookies(?:=|\\s+)'[^']*'", HIDDEN)
                .replaceAll("(?i)--cookies(?:=|\\s+)\\S+", HIDDEN)
                .replaceAll("(?i)--cookies-from-browser(?:=|\\s+)\"[^\"]*\"", HIDDEN)
                .replaceAll("(?i)--cookies-from-browser(?:=|\\s+)'[^']*'", HIDDEN)
                .replaceAll("(?i)--cookies-from-browser(?:=|\\s+)\\S+", HIDDEN);
    }
}
