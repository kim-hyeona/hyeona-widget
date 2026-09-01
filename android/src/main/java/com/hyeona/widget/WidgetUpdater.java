package com.hyeona.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public final class WidgetUpdater {
    // 캘린더 데이터베이스 (오늘 할 일)
    private static final String CALENDAR_DB = "3cd946f4-5bc2-803d-a355-faf7751fb866";
    // 가계부 데이터베이스 (bleeding)
    private static final String BLEEDING_DB = "c07869fb-aaa7-46a5-9366-3a5ff92ebd22";

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        update(c, m, m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class)));
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        Executors.newSingleThreadExecutor().execute(() -> {
            String token = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
            List<Task> tasks = new ArrayList<>();
            String error = "";
            String moneyLine = "";
            if (token.isEmpty()) {
                error = "앱을 열어 Notion 토큰을 저장해 주세요";
            } else {
                try {
                    tasks = fetchTasks(token);
                } catch (Exception e) {
                    error = "Notion 연결을 확인해 주세요";
                }
                try {
                    moneyLine = fetchMoneySummary(token);
                } catch (Exception e) {
                    moneyLine = "";
                }
            }
            for (int id : ids) m.updateAppWidget(id, views(c, tasks, error, moneyLine));
        });
    }

    private static RemoteViews views(Context c, List<Task> tasks, String error, String moneyLine) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.date, new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(new Date()));
        Intent open = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.container, PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        int[] rows = {R.id.row1, R.id.row2, R.id.row3}, texts = {R.id.text1, R.id.text2, R.id.text3}, checks = {R.id.check1, R.id.check2, R.id.check3};
        for (int i = 0; i < 3; i++) {
            if (i < tasks.size()) {
                Task t = tasks.get(i);
                v.setViewVisibility(rows[i], View.VISIBLE);
                v.setTextViewText(texts[i], t.title);
                v.setTextViewText(checks[i], t.done ? "✓" : "○");
                Intent intent = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_TOGGLE).putExtra("page", t.id).putExtra("done", t.done);
                v.setOnClickPendingIntent(checks[i], PendingIntent.getBroadcast(c, 100 + i, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                v.setViewVisibility(rows[i], View.GONE);
            }
        }
        v.setTextViewText(R.id.empty, error.isEmpty() ? (tasks.isEmpty() ? "오늘 할 일이 없어요 ♡" : "") : error);
        v.setViewVisibility(R.id.empty, (!error.isEmpty() || tasks.isEmpty()) ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.money_summary, moneyLine);
        v.setViewVisibility(R.id.money_summary, moneyLine.isEmpty() ? View.GONE : View.VISIBLE);
        return v;
    }

    // 캘린더 DB: 이름(title) 날짜(date) 완료(checkbox) — 오늘 날짜 항목만
    private static List<Task> fetchTasks(String token) throws Exception {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        JSONObject body = new JSONObject()
                .put("page_size", 20)
                .put("filter", new JSONObject().put("property", "날짜").put("date", new JSONObject().put("equals", today)));
        JSONObject data = request(token, "POST", "https://api.notion.com/v1/databases/" + CALENDAR_DB + "/query", body);
        JSONArray results = data.getJSONArray("results");
        List<Task> out = new ArrayList<>();
        for (int i = 0; i < results.length() && out.size() < 3; i++) {
            JSONObject p = results.getJSONObject(i), props = p.getJSONObject("properties");
            JSONArray title = props.getJSONObject("이름").getJSONArray("title");
            if (title.length() == 0) continue;
            out.add(new Task(p.getString("id"), title.getJSONObject(0).optString("plain_text", "할 일"), props.getJSONObject("완료").optBoolean("checkbox")));
        }
        return out;
    }

    // 가계부 DB: 월(select) 금액(만원)(number) — 이번 달 항목 합계, 최대 100개
    private static String fetchMoneySummary(String token) throws Exception {
        String monthLabel = (Calendar.getInstance().get(Calendar.MONTH) + 1) + "월";
        JSONObject body = new JSONObject()
                .put("page_size", 100)
                .put("filter", new JSONObject().put("property", "월").put("select", new JSONObject().put("equals", monthLabel)));
        JSONObject data = request(token, "POST", "https://api.notion.com/v1/databases/" + BLEEDING_DB + "/query", body);
        JSONArray results = data.getJSONArray("results");
        double sum = 0;
        for (int i = 0; i < results.length(); i++) {
            JSONObject props = results.getJSONObject(i).getJSONObject("properties");
            JSONObject amount = props.optJSONObject("금액(만원)");
            if (amount != null && !amount.isNull("number")) sum += amount.optDouble("number", 0);
        }
        return monthLabel + " " + (sum >= 0 ? "+" : "") + (int) sum + "만원";
    }

    static void toggle(Context c, String id, boolean done) {
        if (id == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String token = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
                JSONObject props = new JSONObject().put("완료", new JSONObject().put("checkbox", !done));
                request(token, "PATCH", "https://api.notion.com/v1/pages/" + id, new JSONObject().put("properties", props));
            } catch (Exception ignored) {
            }
            updateAll(c);
        });
    }

    private static JSONObject request(String token, String method, String url, JSONObject body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod(method);
        h.setRequestProperty("Authorization", "Bearer " + token);
        h.setRequestProperty("Notion-Version", "2022-06-28");
        h.setRequestProperty("Content-Type", "application/json");
        h.setDoOutput(true);
        try (OutputStream o = h.getOutputStream()) {
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(h.getResponseCode() < 400 ? h.getInputStream() : h.getErrorStream()));
        StringBuilder s = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) s.append(line);
        if (h.getResponseCode() >= 400) throw new Exception(s.toString());
        return new JSONObject(s.toString());
    }

    private static final class Task {
        final String id, title;
        final boolean done;

        Task(String i, String t, boolean d) {
            id = i;
            title = t;
            done = d;
        }
    }
}
