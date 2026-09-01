package com.hyeona.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationReceiver extends BroadcastReceiver {
    static final String REFRESH = "com.hyeona.widget.REFRESH_NOTIFICATION";
    @Override public void onReceive(Context context, Intent intent) { NotificationKeeper.refresh(context); }
}
