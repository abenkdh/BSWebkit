package uc.benkkstudio.bswebkitsample;

import androidx.appcompat.app.AppCompatActivity;
import uc.benkkstudio.bswebkit.BSWebkit;

import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            BSWebkit.setProxy(MainActivity.class.getName(), this.getApplicationContext(), null, "localhost", 8118);
            Log.wtf("BENKKSTUDIO", "started");
        } catch (Exception e) {
            Log.wtf("BENKKSTUDIO", "Could not start WebkitProxy", e);
        }
    }
}
