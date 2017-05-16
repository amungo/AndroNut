package com.amungo.andronut;

import android.content.res.Resources;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;

class NtConfig {
    private final String TAG = NtConfig.class.getSimpleName();

    private InputStream is = null;
    private ArrayList<RegisterInfo> config = new ArrayList<>();

    NtConfig( Resources resources) {
        //is = resources.openRawResource(R.raw.nt1065);
        is = resources.openRawResource(R.raw.nt1065_lvds);
        parse();
    }

    ArrayList<RegisterInfo> getConfig() {
        return config;
    }

    private void parse() {
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = r.readLine()) != null) {
                if ( !line.startsWith(";")) {
                    String[] tokens = line.replaceAll("Reg", "").split("[ \\r\\n\\t]+");
                    RegisterInfo reg = new RegisterInfo();
                    if ( tokens.length >= 2 ) {
                        try {
                            reg.address = Integer.parseInt(tokens[0]);
                        } catch (java.lang.NumberFormatException e) {
                            reg.address = 0;
                        }

                        try {
                            reg.value = Integer.parseInt(tokens[1], 16);
                        } catch (java.lang.NumberFormatException e) {
                            reg.value = 0;
                        }
                    }

                    config.add(reg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String getConfigInfo() {
        return "Nt1065 Config contains " + config.size() + " registers\n";
    }

    String printConfig() {
        String s = "Nt1065 Config:\n";
        for ( int i = 0; i < config.size(); i++ ) {
            RegisterInfo reg = config.get(i);
            s += String.format(Locale.ENGLISH,
                    "[%2d]=0x%02X\n",
                    reg.address, reg.value
            );
        }
        return s;
    }


}
