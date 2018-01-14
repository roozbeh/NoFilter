package net.ipronto.nofilterisgreatvpn;

import android.app.Activity;
import android.app.Application;

/**
 * Created by roozbeh on 1/3/18.
 */

public class MyApp extends Application {
    public void onCreate() {
        super.onCreate();
    }

    private Activity mCurrentActivity = null;

    public Activity getCurrentActivity(){
        return mCurrentActivity;
    }
    public void setCurrentActivity(Activity mCurrentActivity){
        this.mCurrentActivity = mCurrentActivity;
    }
}
