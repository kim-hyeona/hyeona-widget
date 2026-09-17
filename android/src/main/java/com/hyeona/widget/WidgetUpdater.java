package com.hyeona.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public final class WidgetUpdater {
    private static final String CALENDAR_DB = "3cd946f4-5bc2-803d-a355-faf7751fb866";
    private static final String BLEEDING_DB = "c07869fb-aaa7-46a5-9366-3a5ff92ebd22";
    private static final String WISHLIST_DB = "3cd946f4-5bc2-80cb-875c-fe998bc81776";
    private static final String MEMO_DB = "3cd946f4-5bc2-80db-afae-c68c50665447";
    private static final String ROUTINE_DB = "9d7cbaca-0027-4f12-a6d0-16927ffb0296";
    private static final String BRAIN_PAGE = "3ce946f4-5bc2-81ab-9e97-d2aa17504ea9";
    private static final String NOTION_DASHBOARD = "https://www.notion.so/35b946f45bc280d393f0ee3c366a283b";
    private static final int[] WEEKS = {R.id.week1, R.id.week2, R.id.week3, R.id.week4, R.id.week5, R.id.week6};

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        update(c, m, m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class)));
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        Executors.newSingleThreadExecutor().execute(() -> {
            String token = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
            int monthOffset = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("widget_month_offset", 0);
            Calendar displayMonth = Calendar.getInstance();
            displayMonth.set(Calendar.DAY_OF_MONTH, 1);
            displayMonth.add(Calendar.MONTH, monthOffset);
            Dashboard d = new Dashboard();
            if (token.isEmpty()) {
                d.error = "앱을 열어 Notion 토큰을 저장해 주세요";
            } else {
                try { d.events = fetchMonth(token, displayMonth); } catch (Exception e) { d.error = "캘린더 연결 확인"; }
                try { d.money = fetchMoneySummary(token); } catch (Exception ignored) { }
                try { d.wishes = fetchCount(token, WISHLIST_DB); } catch (Exception ignored) { }
                try { d.memos = fetchSimple(token, MEMO_DB, "제목", false); } catch (Exception ignored) { }
                try { d.routines = fetchSimple(token, ROUTINE_DB, "항목", true); } catch (Exception ignored) { }
                try { d.brain = fetchBrain(token); } catch (Exception ignored) { }
            }
            for (int id : ids) m.updateAppWidget(id, views(c, d, displayMonth));
        });
    }

    private static RemoteViews views(Context c, Dashboard d, Calendar displayMonth) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        Calendar now = Calendar.getInstance();
        int month = displayMonth.get(Calendar.MONTH) + 1;
        String monthTitle = displayMonth.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? month + "월" : displayMonth.get(Calendar.YEAR) + "년 " + month + "월";
        v.setTextViewText(R.id.month_title, monthTitle);

        Intent open = new Intent(c, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.container, openPi);
        Intent prev = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_PREV_MONTH);
        Intent next = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_NEXT_MONTH);
        v.setOnClickPendingIntent(R.id.add_button, PendingIntent.getBroadcast(c, 3, prev, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.refresh_button, PendingIntent.getBroadcast(c, 4, next, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        Intent notion = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(NOTION_DASHBOARD));
        v.setOnClickPendingIntent(R.id.notion_button, PendingIntent.getActivity(c, 5, notion, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        Map<String, List<Event>> byDate = new HashMap<>();
        for (Event e : d.events) {
            if (!byDate.containsKey(e.date)) byDate.put(e.date, new ArrayList<>());
            byDate.get(e.date).add(e);
        }
        Calendar grid = (Calendar) displayMonth.clone();
        int first = grid.get(Calendar.DAY_OF_WEEK) - 1;
        int weeksNeeded = (int) Math.ceil((first + grid.getActualMaximum(Calendar.DAY_OF_MONTH)) / 7.0);
        grid.add(Calendar.DAY_OF_MONTH, -first);
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<Event> today = byDate.containsKey(todayKey) ? byDate.get(todayKey) : new ArrayList<>();

        for (int week = 0; week < 6; week++) {
            v.setViewVisibility(WEEKS[week], week < weeksNeeded ? View.VISIBLE : View.GONE);
            v.removeAllViews(WEEKS[week]);
            for (int day = 0; day < 7; day++) {
                RemoteViews cell = new RemoteViews(c.getPackageName(), R.layout.widget_day);
                String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(grid.getTime());
                boolean inMonth = grid.get(Calendar.MONTH) == displayMonth.get(Calendar.MONTH) && grid.get(Calendar.YEAR) == displayMonth.get(Calendar.YEAR);
                cell.setTextViewText(R.id.day_number, String.valueOf(grid.get(Calendar.DAY_OF_MONTH)));
                int color = inMonth ? Color.rgb(38, 62, 67) : Color.rgb(178, 188, 190);
                if (day == 0 && inMonth) color = Color.rgb(201, 104, 115);
                if (day == 6 && inMonth) color = Color.rgb(79, 148, 175);
                if (key.equals(todayKey)) color = Color.WHITE;
                cell.setTextColor(R.id.day_number, color);
                if (key.equals(todayKey)) cell.setInt(R.id.day_number, "setBackgroundColor", Color.rgb(29, 36, 40));
                List<Event> events = byDate.get(key);
                StringBuilder names = new StringBuilder();
                if (events != null && inMonth) {
                    for (int i = 0; i < events.size() && i < 2; i++) {
                        if (i > 0) names.append(" · ");
                        names.append(events.get(i).title);
                    }
                    if (events.size() > 2) names.append(" +").append(events.size() - 2);
                }
                cell.setTextViewText(R.id.day_event, names.toString());
                cell.setViewVisibility(R.id.day_event, names.length() == 0 ? View.INVISIBLE : View.VISIBLE);
                v.addView(WEEKS[week], cell);
                grid.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        v.setTextViewText(R.id.today_title, "TODAY · " + today.size());
        int routineDone = 0; for (Event item : d.routines) if (item.done) routineDone++;
        v.setTextViewText(R.id.routine_title, "ROUTINE · " + routineDone + "/" + d.routines.size());
        bindCheckList(c, v, R.id.today_list, today, 100);
        bindTextList(c, v, R.id.memo_list, d.memos, "메모가 없어요");
        bindCheckList(c, v, R.id.routine_list, d.routines, 200);
        setSectionLink(c, v, R.id.today_list, "calendar", 10);
        setSectionLink(c, v, R.id.memo_list, "memo", 11);
        setSectionLink(c, v, R.id.routine_list, "routine", 12);
        v.setTextViewText(R.id.wish_summary, "♡ WISH · " + d.wishes);
        v.setTextViewText(R.id.money_summary, "◇ BLEEDING · " + (d.money.isEmpty() ? "0" : d.money));
        v.setTextViewText(R.id.brain_summary, "⌁ BRAIN DUMP · " + (d.brain.isEmpty() ? "…" : d.brain));
        v.setTextViewText(R.id.packaging_summary, "◇ PACKAGING");
        setSectionLink(c, v, R.id.wish_summary, "wishlist", 20);
        setSectionLink(c, v, R.id.money_summary, "bleeding", 21);
        setSectionLink(c, v, R.id.brain_summary, "brain", 22);
        v.setOnClickPendingIntent(R.id.packaging_summary, PendingIntent.getActivity(c, 23, notion, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        bindCareWeek(c, v);
        return v;
    }

    private static void bindCheckList(Context c, RemoteViews parent, int container, List<Event> items, int requestBase) {
        parent.removeAllViews(container);
        int count = Math.min(5, items.size());
        for (int i = 0; i < count; i++) {
            Event item = items.get(i);
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_check_item);
            row.setTextViewText(R.id.item_check, item.done ? "✓" : "○");
            row.setTextViewText(R.id.item_text, item.title);
            Intent toggle = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_TOGGLE).putExtra("page", item.id).putExtra("done", item.done);
            row.setOnClickPendingIntent(R.id.item_check, PendingIntent.getBroadcast(c, requestBase + i, toggle, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            parent.addView(container, row);
        }
        if (count == 0) {
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_text_item);
            row.setTextViewText(R.id.item_text, "· 비어 있어요");
            parent.addView(container, row);
        }
    }

    private static void bindTextList(Context c, RemoteViews parent, int container, List<Event> items, String empty) {
        parent.removeAllViews(container);
        int count = Math.min(5, items.size());
        for (int i = 0; i < count; i++) {
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_text_item);
            row.setTextViewText(R.id.item_text, "· " + items.get(i).title);
            parent.addView(container, row);
        }
        if (count == 0) {
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_text_item);
            row.setTextViewText(R.id.item_text, "· " + empty);
            parent.addView(container, row);
        }
    }

    private static void setSectionLink(Context c, RemoteViews v, int viewId, String section, int requestCode) {
        Intent open = new Intent(c, MainActivity.class).putExtra("section", section);
        v.setOnClickPendingIntent(viewId, PendingIntent.getActivity(c, requestCode, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static void bindCareWeek(Context c, RemoteViews v) {
        int offset = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("care_week_offset", 0);
        Calendar day = Calendar.getInstance(); day.set(Calendar.HOUR_OF_DAY, 12);
        int mondayDelta = day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day.get(Calendar.DAY_OF_WEEK);
        day.add(Calendar.DAY_OF_MONTH, mondayDelta + offset * 7);
        int[] ids = {R.id.care1,R.id.care2,R.id.care3,R.id.care4,R.id.care5,R.id.care6,R.id.care7};
        String[] names = {"월","화","수","목","금","토","일"};
        String start = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("period_start", "");
        String end = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("period_end", "");
        int cycle = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("cycle_length", 28);
        for (int i = 0; i < 7; i++) {
            String icon = careIcon(day.getTime(), start, end, cycle);
            v.setTextViewText(ids[i], names[i] + "\n" + day.get(Calendar.DAY_OF_MONTH) + (icon.isEmpty() ? "" : "\n" + icon));
            Intent care = new Intent(c, MainActivity.class).putExtra("section", "care");
            v.setOnClickPendingIntent(ids[i], PendingIntent.getActivity(c, 40 + i, care, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        Intent prev = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_PREV_CARE_WEEK);
        Intent next = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_NEXT_CARE_WEEK);
        v.setOnClickPendingIntent(R.id.care_prev, PendingIntent.getBroadcast(c, 30, prev, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.care_next, PendingIntent.getBroadcast(c, 31, next, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static String careIcon(Date value, String startValue, String endValue, int cycle) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US); f.setLenient(false);
            Date start = f.parse(startValue), end = f.parse(endValue); if (start == null || end == null || cycle < 21 || cycle > 45) return "";
            long dayMs = 86400000L, delta = (value.getTime() - start.getTime()) / dayMs;
            int periodDays = Math.max(1, (int)((end.getTime() - start.getTime()) / dayMs) + 1);
            int day = (int)((delta % cycle + cycle) % cycle), ovulation = cycle - 14;
            if (day < periodDays) return "🫧";
            if (day < ovulation) return "💊";
            if (day == ovulation) return "✦";
            return "🍦";
        } catch (Exception ignored) { return ""; }
    }

    private static List<Event> fetchMonth(String token, Calendar selectedMonth) throws Exception {
        Calendar start = (Calendar) selectedMonth.clone();
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        JSONObject date = new JSONObject().put("and", new JSONArray()
                .put(new JSONObject().put("property", "날짜").put("date", new JSONObject().put("on_or_after", f.format(start.getTime()))))
                .put(new JSONObject().put("property", "날짜").put("date", new JSONObject().put("before", f.format(end.getTime())))));
        JSONObject body = new JSONObject().put("page_size", 100).put("filter", date);
        JSONArray results = request(token, "POST", "https://api.notion.com/v1/databases/" + CALENDAR_DB + "/query", body).getJSONArray("results");
        List<Event> out = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject p = results.getJSONObject(i), props = p.getJSONObject("properties");
            JSONArray title = props.getJSONObject("이름").getJSONArray("title");
            JSONObject dateObj = props.getJSONObject("날짜").optJSONObject("date");
            if (title.length() == 0 || dateObj == null) continue;
            String dateValue = dateObj.optString("start", "");
            if (dateValue.length() >= 10) dateValue = dateValue.substring(0, 10);
            out.add(new Event(p.getString("id"), title.getJSONObject(0).optString("plain_text", "할 일"), dateValue, props.getJSONObject("완료").optBoolean("checkbox")));
        }
        return out;
    }

    private static String fetchMoneySummary(String token) throws Exception {
        String monthLabel = (Calendar.getInstance().get(Calendar.MONTH) + 1) + "월";
        JSONObject body = new JSONObject().put("page_size", 100).put("filter", new JSONObject().put("property", "월").put("select", new JSONObject().put("equals", monthLabel)));
        JSONArray results = request(token, "POST", "https://api.notion.com/v1/databases/" + BLEEDING_DB + "/query", body).getJSONArray("results");
        double sum = 0;
        for (int i = 0; i < results.length(); i++) {
            JSONObject amount = results.getJSONObject(i).getJSONObject("properties").optJSONObject("금액(만원)");
            if (amount != null && !amount.isNull("number")) sum += amount.optDouble("number", 0);
        }
        return monthLabel + " " + (int) sum + "만원";
    }

    private static int fetchCount(String token, String db) throws Exception {
        return request(token, "POST", "https://api.notion.com/v1/databases/" + db + "/query", new JSONObject().put("page_size", 100)).getJSONArray("results").length();
    }

    private static List<Event> fetchSimple(String token, String database, String titleProperty, boolean withDone) throws Exception {
        JSONObject body = new JSONObject().put("page_size", 100).put("sorts", new JSONArray().put(new JSONObject().put("timestamp", "last_edited_time").put("direction", "descending")));
        JSONArray results = request(token, "POST", "https://api.notion.com/v1/databases/" + database + "/query", body).getJSONArray("results");
        List<Event> out = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject page = results.getJSONObject(i), props = page.getJSONObject("properties");
            JSONArray title = props.getJSONObject(titleProperty).getJSONArray("title");
            if (title.length() == 0) continue;
            boolean done = withDone && props.optJSONObject("완료") != null && props.optJSONObject("완료").optBoolean("checkbox");
            out.add(new Event(page.getString("id"), title.getJSONObject(0).optString("plain_text", ""), "", done));
        }
        return out;
    }

    private static String fetchBrain(String token) throws Exception {
        JSONObject props = request(token, "GET", "https://api.notion.com/v1/pages/" + BRAIN_PAGE, null).getJSONObject("properties");
        JSONObject content = props.optJSONObject("내용");
        if (content == null) return "";
        JSONArray rich = content.optJSONArray("rich_text");
        if (rich == null || rich.length() == 0) return "";
        String s = rich.getJSONObject(0).optString("plain_text", "").replace('\n', ' ');
        return s.length() > 16 ? s.substring(0, 16) + "…" : s;
    }

    static void toggle(Context c, String id, boolean done) {
        if (id == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String token = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
                JSONObject props = new JSONObject().put("완료", new JSONObject().put("checkbox", !done));
                request(token, "PATCH", "https://api.notion.com/v1/pages/" + id, new JSONObject().put("properties", props));
            } catch (Exception ignored) { }
            updateAll(c);
        });
    }

    static void moveMonth(Context c, int amount) {
        int current = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("widget_month_offset", 0);
        int next = Math.max(-24, Math.min(24, current + amount));
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putInt("widget_month_offset", next).apply();
        updateAll(c);
    }

    static void moveCareWeek(Context c, int amount) {
        int current = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("care_week_offset", 0);
        int next = Math.max(-52, Math.min(52, current + amount));
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putInt("care_week_offset", next).apply();
        updateAll(c);
    }

    private static JSONObject request(String token, String method, String url, JSONObject body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod(method);
        h.setRequestProperty("Authorization", "Bearer " + token);
        h.setRequestProperty("Notion-Version", "2022-06-28");
        h.setRequestProperty("Content-Type", "application/json");
        if (body != null) {
            h.setDoOutput(true);
            try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code = h.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(code < 400 ? h.getInputStream() : h.getErrorStream()));
        StringBuilder s = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) s.append(line);
        if (code >= 400) throw new Exception(s.toString());
        return new JSONObject(s.toString());
    }

    private static final class Dashboard {
        List<Event> events = new ArrayList<>(), memos = new ArrayList<>(), routines = new ArrayList<>(); String error = "", money = "", brain = ""; int wishes = 0;
    }
    private static final class Event {
        final String id, title, date; final boolean done;
        Event(String id, String title, String date, boolean done) { this.id = id; this.title = title; this.date = date; this.done = done; }
    }
}
