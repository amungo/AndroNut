package com.amungo.andronut;

import android.content.res.Resources;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

class Firmware {
    private final String TAG = Firmware.class.getSimpleName();

    private Resources resources = null;
    private DataInputStream is = null;
    private boolean parsed = false;

    private ArrayList<FirmwareData> sectionsList = new ArrayList<>();

    Firmware( Resources resources) {
        this.resources = resources;
        is = new DataInputStream( resources.openRawResource(R.raw.amungofw) );
    }

    private int readIntLE() throws IOException {
        byte b0 = is.readByte();
        byte b1 = is.readByte();
        byte b2 = is.readByte();
        byte b3 = is.readByte();
        return b3 << 24 | (b2 & 0xFF) << 16 | (b1 & 0xFF) << 8 | (b0 & 0xFF);

    }

    String parse() {
        String log = "Firmware.parse()\n";
        is = new DataInputStream( resources.openRawResource(R.raw.amungofw) );

        if ( parsed ) {
            log = "Firmware.parse(): already parsed\n";
            return log;
        }

        sectionsList = new ArrayList<>();

        try {

            byte b0 = is.readByte();
            byte b1 = is.readByte();
            if ( b0 != 0x43 || b1 != 0x59 ) {
                log = "Header contains illegal bytes\n";
                return log;
            }

            // skip two bytes
            is.readByte();
            is.readByte();

            boolean isDone = false;
            while ( !isDone ) {

                int sectionSize8 = 4 * readIntLE();
                int sectionDevAddr = readIntLE();

                Log.d(TAG, String.format(Locale.ENGLISH,
                        "devaddr 0x%08X size %d\n",
                        sectionDevAddr, sectionSize8
                ));

                FirmwareData fwData = new FirmwareData();
                fwData.buffer = new byte[ sectionSize8 ];
                fwData.devAddr = sectionDevAddr;

                if ( fwData.buffer.length == 0 ) {
                    isDone = true;
                } else {
                    int readLen = is.read( fwData.buffer, 0, fwData.buffer.length );
                    if ( readLen != fwData.buffer.length ) {
                        log += "Read only " + readLen + " of " + fwData.buffer.length + "\n";
                        return log;
                    }
                }

                sectionsList.add(fwData);

            }

        } catch (IOException ioe) {
            log += "\n IOException\n";
            return log;
        }

        parsed = true;
        return log;
    }

    void splitSections( int txSize ) {
        ArrayList<FirmwareData> newList = new ArrayList<>();

        for ( int i = 0; i < sectionsList.size(); i++ ) {
            FirmwareData fwData = sectionsList.get(i);
            if ( fwData.buffer.length <= txSize ) {
                newList.add(fwData);
            } else {
                int offset = 0;
                int remainLength = fwData.buffer.length;
                int curDevAddr = fwData.devAddr;
                while ( remainLength > 0 ) {
                    int len = remainLength > txSize ? txSize : remainLength;

                    FirmwareData newData = new FirmwareData();
                    newData.devAddr = curDevAddr;
                    newData.buffer = new byte[ len ];
                    System.arraycopy(
                            fwData.buffer, offset,
                            newData.buffer, 0,
                            len
                    );

                    curDevAddr += len;
                    remainLength -= len;
                    offset += len;

                    newList.add( newData );
                }
            }
        }
        sectionsList = newList;
        getSectionsDescription();
    }

    String getSectionsDescription() {
        String desc = "SectionsList\n";
        desc += "size " + sectionsList.size() + "\n";
        for ( int i = 0; i < sectionsList.size(); i++ ) {
            FirmwareData fwData = sectionsList.get(i);
            String s = String.format(Locale.ENGLISH,
                    "[%2d] 0x%08X %d\n",
                    i, fwData.devAddr, fwData.buffer.length );
            desc += s;
            Log.d(TAG, s);
        }

        return desc;
    }

    ArrayList<FirmwareData> getSectionsList() {
        return sectionsList;
    }
}
