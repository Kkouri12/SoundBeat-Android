package com.soundbeat.player;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MusicLibraryPlugin.class);
        registerPlugin(MediaControlPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
