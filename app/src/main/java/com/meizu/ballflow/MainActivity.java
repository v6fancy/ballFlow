package com.meizu.ballflow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;

/**
 * Created by yuxuxin on 15-11-26.
 */
public class MainActivity extends Activity {
    enum ServerOrCilent{
        NONE,
        SERVICE,
        CILENT
    };
    private Context mContext;
    static String BlueToothAddress = "null";
    static ServerOrCilent serviceOrCilent = ServerOrCilent.NONE;
    static boolean isOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //mScreen是自定义的View
        mContext = this;
        setContentView(R.layout.main_page);
        Intent rebootActionIntent = new Intent(mContext, deviceActivity.class);
        rebootActionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(rebootActionIntent);
    }

    @Override
    protected void onDestroy() {
        /* unbind from the service */

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

}
