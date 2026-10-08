package com.hyeona.widget;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class NotificationReceiver extends BroadcastReceiver {
 static final String REFRESH="com.hyeona.widget.REFRESH_NOTIFICATION";
 @Override public void onReceive(Context context,Intent intent){
  if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())||Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))WidgetUpdater.updateAll(context);
  NotificationKeeper.refresh(context);
 }
}
