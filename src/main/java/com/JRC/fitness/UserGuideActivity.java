package com.JRC.fitness;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class UserGuideActivity extends Activity {

    private static final String GUIDE_TEXT =
            "1. CALIBRATE\n"
            + "The first time you pick an exercise, you'll be asked to calibrate. "
            + "Perform the requested number of reps at your normal pace so the app can "
            + "learn your motion or sound signature. For Jump Rope, you'll get a 3-second "
            + "countdown before it starts listening, so you have time to get into position.\n\n"

            + "2. TRAIN\n"
            + "Once calibrated, reps are counted automatically as soon as you start. "
            + "Tap the big rep count at any time to pause or resume counting — it turns "
            + "light yellow while paused. Tap Stop Session when you're done to save it.\n\n"

            + "3. RECALIBRATE\n"
            + "If counting feels off, use Recalibrate from the training screen to redo it.\n\n"

            + "4. STATS & GOALS\n"
            + "Swipe up from the home screen to see your stats: average reps per training "
            + "day, how this week compares to last week, average per session, and (for Jump "
            + "Rope) your best pace. Set a rep goal per exercise from Settings > Goals.\n\n"

            + "5. BEEPS\n"
            + "In Settings, set a number of reps for each exercise to get a short beep every "
            + "time you hit a multiple of that count — handy for keeping track without "
            + "watching the screen. Set it to 0 to turn beeps off.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_user_guide);
        ((TextView) findViewById(R.id.userGuideBody)).setText(GUIDE_TEXT);
    }
}
