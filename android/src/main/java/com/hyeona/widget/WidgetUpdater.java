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
    private static final String BRAIN_PAGE = "3ce946f4-5bc2-81ab-9e97-d2aa17504ea9";
    private static final int[] WEEKS = {R.id.week1, R.id.week2, R.id.week3, R.id.week4, R.id.week5, R.id.week6};

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        update(c, m, m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class)));
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        Executors.newSingleThreadExecutor().execute(() -> {
            String token = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
            Dashboard d = new Dashboard();
            if (token.isEmpty()) {
                d.error = "앱을 열어 Notion 토큰을 저장해 주세요";
            } else {
                try { d.events = fetchMonth(token); } catch (Exception e) { d.error = "캘린더 연결 확인"; }
                try { d.money = fetchMoneySummary(token); } catch (Exception ignored) { }
                try { d.wishes = fetchCount(token, WISHLIST_DB); } catch (Exception ignored) { }
                try { d.memos = fetchMemos(token); } catch (Exception ignored) { }
                try { d.brain = fetchBrain(token); } catch (Exception ignored) { }
            }
            for (int id : ids) m.updateAppWidget(id, views(c, d));
        });
    }

    private static RemoteViews views(Context c, Dashboard d) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH) + 1;
        v.setTextViewText(R.id.month_title, month + "월");

        Intent open = new Intent(c, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.container, openPi);
        v.setOnClickPendingIntent(R.id.add_button, openPi);
        Intent refresh = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.refresh_button, PendingIntent.getBroadcast(c, 2, refresh, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        Map<String, List<Event>> byDate = new HashMap<>();
        for (Event e : d.events) {
            if (!byDate.containsKey(e.date)) byDate.put(e.date, new ArrayList<>());
            byDate.get(e.date).add(e);
        }
        Calendar grid = Calendar.getInstance();
        grid.set(Calendar.DAY_OF_MONTH, 1);
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
                boolean inMonth = grid.get(Calendar.MONTH) == now.get(Calendar.MONTH);
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
        int[] rows = {R.id.row1, R.id.row2, R.id.row3};
        int[] texts = {R.id.text1, R.id.text2, R.id.text3};
        int[] checks = {R.id.check1, R.id.check2, R.id.check3};
        for (int i = 0; i < 3; i++) {
            if (i < today.size()) {
                Event t = today.get(i);
                v.setViewVisibility(rows[i], View.VISIBLE);
                v.setTextViewText(texts[i], t.title);
                v.setTextViewText(checks[i], t.done ? "✓" : "○");
                Intent toggle = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_TOGGLE).putExtra("page", t.id).putExtra("done", t.done);
                v.setOnClickPendingIntent(checks[i], PendingIntent.getBroadcast(c, 100 + i, toggle, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else v.setViewVisibility(rows[i], View.GONE);
        }
        String empty = !d.error.isEmpty() ? d.error : "오늘 할 일이 없어요 ♡";
        v.setTextViewText(R.id.empty, empty);
        v.setViewVisibility(R.id.empty, today.isEmpty() || !d.error.isEmpty() ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.memo_summary, d.memos.isEmpty() ? "메모가 없어요" : d.memos);
        v.setTextViewText(R.id.wish_summary, "♡  WISH\n" + d.wishes + "개");
        v.setTextViewText(R.id.money_summary, "₩  BLEEDING\n" + (d.money.isEmpty() ? month + "월 0만원" : d.money));
        v.setTextViewText(R.id.brain_summary, "⌁  BRAIN DUMP\n" + (d.brain.isEmpty() ? "빠른 메모" : d.brain));
        return v;
    }

    private static List<Event> fetchMonth(String token) throws Exception {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
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

    private static String fetchMemos(String token) throws Exception {
        JSONObject body = new JSONObject().put("page_size", 3).put("sorts", new JSONArray().put(new JSONObject().put("timestamp", "last_edited_time").put("direction", "descending")));
        JSONArray results = request(token, "POST", "https://api.notion.com/v1/databases/" + MEMO_DB + "/query", body).getJSONArray("results");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < results.length(); i++) {
            JSONArray title = results.getJSONObject(i).getJSONObject("properties").getJSONObject("제목").getJSONArray("title");
            if (title.length() == 0) continue;
            if (out.length() > 0) out.append("\n");
            out.append("· ").append(title.getJSONObject(0).optString("plain_text", ""));
        }
        return out.toString();
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
        List<Event> events = new ArrayList<>(); String error = "", money = "", memos = "", brain = ""; int wishes = 0;
    }
    private static final class Event {
        final String id, title, date; final boolean done;
        Event(String id, String title, String date, boolean done) { this.id = id; this.title = title; this.date = date; this.done = done; }
    }
}
