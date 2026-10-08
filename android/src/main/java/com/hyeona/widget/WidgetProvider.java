package com.hyeona.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class WidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TODAY = "com.hyeona.widget.TODAY";
    public static final String ACTION_SELECT_CARE = "com.hyeona.widget.SELECT_CARE";
    public static final String ACTION_TOGGLE = "com.hyeona.widget.TOGGLE";
    public static final String ACTION_REFRESH = "com.hyeona.widget.REFRESH";
    public static final String ACTION_PREV_MONTH = "com.hyeona.widget.PREV_MONTH";
    public static final String ACTION_NEXT_MONTH = "com.hyeona.widget.NEXT_MONTH";
    public static final String ACTION_PREV_CARE_WEEK = "com.hyeona.widget.PREV_CARE_WEEK";
    public static final String ACTION_NEXT_CARE_WEEK = "com.hyeona.widget.NEXT_CARE_WEEK";
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { WidgetUpdater.update(c, m, ids); }
    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (ACTION_TODAY.equals(i.getAction())) WidgetUpdater.today(c);
        if (ACTION_SELECT_CARE.equals(i.getAction())) WidgetUpdater.selectCare(c,i.getIntExtra("day_index",0));
        if (ACTION_TOGGLE.equals(i.getAction())) WidgetUpdater.toggle(c, i.getStringExtra("page"), i.getBooleanExtra("done", false));
        if (ACTION_REFRESH.equals(i.getAction())) WidgetUpdater.updateAll(c);
        if (ACTION_PREV_MONTH.equals(i.getAction())) WidgetUpdater.moveMonth(c, -1);
        if (ACTION_NEXT_MONTH.equals(i.getAction())) WidgetUpdater.moveMonth(c, 1);
        if (ACTION_PREV_CARE_WEEK.equals(i.getAction())) WidgetUpdater.moveCareWeek(c, -1);
        if (ACTION_NEXT_CARE_WEEK.equals(i.getAction())) WidgetUpdater.moveCareWeek(c, 1);
    }
}
