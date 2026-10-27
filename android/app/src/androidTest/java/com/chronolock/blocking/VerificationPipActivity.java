package com.chronolock.blocking;

import android.app.PictureInPictureParams;
import android.content.Intent;

/**
 * A target that moves itself into picture-in-picture, like a video app does when the person goes home.
 * Started again with the extra "close" it finishes, so a test leaves no small window behind for the next one.
 */
public class VerificationPipActivity extends VerificationTargetActivity {
    @Override public void onResume() {
        super.onResume();
        if (getIntent().getBooleanExtra("close", false)) { finish(); return; }
        if (!isInPictureInPictureMode()) enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
