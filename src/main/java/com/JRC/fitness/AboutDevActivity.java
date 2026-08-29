package com.JRC.fitness;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class AboutDevActivity extends Activity {

    private static final String ABOUT_BODY =
            "Built by JRC as a small, no-frills companion for squats and jump rope — "
            + "just calibrate, train, and see your numbers.\n\n"
            + "No accounts, no ads, no data leaving your device. "
            + "Thanks for using it!";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_about);

        ((TextView) findViewById(R.id.aboutBody)).setText(ABOUT_BODY);

        TextView versionText = findViewById(R.id.aboutVersion);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText.setText("Version " + info.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("");
        }
    }
}
