package com.chronolock.blocking;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Runs in the test APK's own process, with no dependency on the target app's libraries. */
public class VerificationTargetActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView label = new TextView(this);
        label.setText("ChronoLock device verification target");
        label.setTextSize(24);
        setContentView(label);
    }
}
