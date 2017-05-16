package com.amungo.andronut;

import java.util.Locale;

public class FirmwareDescription {
    byte[] buffer = new byte[32];
    int getVersion() {
        return buffer[3] << 24 | (buffer[2] & 0xFF) << 16 |
                (buffer[1] & 0xFF) << 8 | (buffer[0] & 0xFF);
    }
    String getVersionString() {
        return String.format(Locale.ENGLISH,
                "Firmware Version: %08X",
                getVersion()
        );
    }
}
