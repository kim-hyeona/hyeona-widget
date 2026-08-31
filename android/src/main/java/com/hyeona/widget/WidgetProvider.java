package com.hyeona.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class WidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE="com.hyeona.widget.TOGGLE";
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids){WidgetUpdater.update(c,m,ids);}
    @Override public void onReceive(Context c, Intent i){super.onReceive(c,i);if(ACTION_TOGGLE.equals(i.getAction()))WidgetUpdater.toggle(c,i.getStringExtra("page"),i.getBooleanExtra("done",false));}
}

