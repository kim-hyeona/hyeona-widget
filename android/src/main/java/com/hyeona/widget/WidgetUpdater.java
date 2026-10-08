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
    private static final java.util.concurrent.ExecutorService UPDATES = Executors.newSingleThreadExecutor();
    private static Dashboard lastDashboard;
    private static Calendar lastMonth;
    private static final String CALENDAR_DB = "3cd946f4-5bc2-803d-a355-faf7751fb866";
    private static final String BLEEDING_DB = "c07869fb-aaa7-46a5-9366-3a5ff92ebd22";
    private static final String WISHLIST_DB = "3cd946f4-5bc2-80cb-875c-fe998bc81776";
    private static final String MEMO_DB = "3cd946f4-5bc2-80db-afae-c68c50665447";
    private static final String ROUTINE_DB = "9d7cbaca-0027-4f12-a6d0-16927ffb0296";
    private static final String BRAIN_PAGE = "3ce946f4-5bc2-81ab-9e97-d2aa17504ea9";
    private static final String NOTION_DASHBOARD = "https://www.notion.so/35b946f45bc2806798f4fec2232a2dfd";
    private static final int[] WEEKS = {R.id.week1, R.id.week2, R.id.week3, R.id.week4, R.id.week5, R.id.week6};

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        update(c, m, m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class)));
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        Calendar month=Calendar.getInstance();month.set(Calendar.DAY_OF_MONTH,1);month.add(Calendar.MONTH,c.getSharedPreferences("prefs",0).getInt("widget_month_offset",0));
        Dashboard cached=new Dashboard();cached.careStart=careWeekStart(c);restore(c,cached,month);cached.care=loadLocalCareWeek(c,cached.careStart);publish(c,m,ids,cached,month);
        WidgetRefreshService.schedule(c);
    }

    static void runRefresh(Context c, Runnable finished) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
        UPDATES.execute(() -> {
          try {
            String token = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
            int monthOffset = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("widget_month_offset", 0);
            Calendar displayMonth = Calendar.getInstance();
            displayMonth.set(Calendar.DAY_OF_MONTH, 1);
            displayMonth.add(Calendar.MONTH, monthOffset);
            Dashboard d = new Dashboard();
            d.careStart = careWeekStart(c);
            restore(c,d,displayMonth);
            d.care = loadLocalCareWeek(c,d.careStart);
            publish(c,m,ids,d,displayMonth);
            if (token.isEmpty()) {
                d.error = "앱을 열어 Notion 토큰을 저장해 주세요";
            } else {
                try { d.events = fetchMonth(token, displayMonth); } catch (Exception e) { d.error = "캘린더 연결 실패 · 앱에서 새로고침"; }
                publish(c,m,ids,d,displayMonth);
                try { d.money = fetchMoneySummary(token); } catch (Exception e) { d.error="일부 데이터 연결 실패 · 앱에서 새로고침"; }
                try { d.wishes = fetchCount(token, WISHLIST_DB); } catch (Exception e) { d.error="일부 데이터 연결 실패 · 앱에서 새로고침"; }
                try { d.memos = fetchSimple(token, MEMO_DB, "제목", false); } catch (Exception e) { d.error="일부 데이터 연결 실패 · 앱에서 새로고침"; }
                try {
                    List<NotionClient.Item> items=NotionClient.query(token,"todo");d.todos.clear();
                    for (NotionClient.Item item : items)
                        if (!item.done) d.todos.add(new Event(item.id, item.title, "", false));
                } catch (Exception e) { d.error="일부 데이터 연결 실패 · 앱에서 새로고침"; }
                try {
                    List<NotionClient.Item> items=NotionClient.query(token,"routine");d.routines.clear();
                    for (NotionClient.Item item : items)
                        d.routines.add(new Event(item.id, item.title.isEmpty() ? "제목 없음" : item.title, "", item.done));
                } catch (Exception e) { d.routineError = "루틴 불러오기 실패 · 눌러 확인"; }
                publish(c,m,ids,d,displayMonth);
                d.care = loadLocalCareWeek(c, d.careStart);
                try {
                    Calendar end = (Calendar) d.careStart.clone(); end.add(Calendar.DAY_OF_MONTH, 7);
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Map<String, NotionClient.CareEntry> remoteCare = NotionClient.loadCareWeek(token, f.format(d.careStart.getTime()), f.format(end.getTime()));
                    for (Map.Entry<String, NotionClient.CareEntry> e : remoteCare.entrySet()) {
                        if (!c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("care_"+e.getKey()+"_pending",false)) { d.care.put(e.getKey(), e.getValue()); saveLocalCare(c, e.getValue()); }
                    }
                } catch (Exception e) { d.error="식단 연결 실패 · 앱에서 식단 확인"; }
            }
            lastDashboard = d; lastMonth = displayMonth;
            publish(c,m,ids,d,displayMonth);
          } finally { finished.run(); }
        });
    }

    private static String cacheKey(Calendar month){return "widget_cache_"+month.get(Calendar.YEAR)+"_"+month.get(Calendar.MONTH);}
    private static JSONArray encode(List<Event> items)throws Exception{JSONArray out=new JSONArray();for(Event e:items)out.put(new JSONObject().put("id",e.id).put("title",e.title).put("date",e.date).put("done",e.done));return out;}
    private static List<Event> decode(JSONArray a)throws Exception{List<Event> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);out.add(new Event(x.optString("id"),x.optString("title"),x.optString("date"),x.optBoolean("done")));}return out;}
    private static void restore(Context c,Dashboard d,Calendar month){try{JSONObject x=new JSONObject(c.getSharedPreferences("prefs",0).getString(cacheKey(month),"{}"));d.events=decode(x.optJSONArray("events"));d.todos=decode(x.optJSONArray("todos"));d.memos=decode(x.optJSONArray("memos"));d.routines=decode(x.optJSONArray("routines"));d.money=x.optString("money");d.wishes=x.optInt("wishes");}catch(Exception ignored){}}
    private static void publish(Context c,AppWidgetManager m,int[] ids,Dashboard d,Calendar month){
        lastDashboard=d;lastMonth=month;
        for(int id:ids)m.updateAppWidget(id,views(c,d,month));
        try{JSONObject x=new JSONObject().put("events",encode(d.events)).put("todos",encode(d.todos)).put("memos",encode(d.memos)).put("routines",encode(d.routines)).put("money",d.money).put("wishes",d.wishes);c.getSharedPreferences("prefs",0).edit().putString(cacheKey(month),x.toString()).apply();}catch(Exception ignored){}
    }

    private static RemoteViews views(Context c, Dashboard d, Calendar displayMonth) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_stable);
        Calendar now = Calendar.getInstance();
        int month = displayMonth.get(Calendar.MONTH) + 1;
        String monthTitle = displayMonth.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? month + "월" : displayMonth.get(Calendar.YEAR) + "년 " + month + "월";
        v.setTextViewText(R.id.month_title, monthTitle);
        Intent jumpToday = new Intent(c, WidgetProvider.class).setAction(WidgetProvider.ACTION_TODAY);
        v.setOnClickPendingIntent(R.id.today_button, PendingIntent.getBroadcast(c, 6, jumpToday, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setTextViewText(R.id.calendar_hint, d.error.isEmpty() ? "날짜 칸을 누르면 일정 추가" : d.error);

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
        List<Event> today = d.todos;

        for (int week = 0; week < 6; week++) {
            v.setViewVisibility(WEEKS[week], week < weeksNeeded ? View.VISIBLE : View.GONE);

            for (int day = 0; day < 7; day++) {
                int index=week*7+day;
                int cellId=viewId(c,"day_cell_"+index), numberId=viewId(c,"day_number_"+index), eventId=viewId(c,"day_event_"+index);
                String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(grid.getTime());
                Intent addEvent = new Intent(c, MainActivity.class).setData(android.net.Uri.parse("hyeona://calendar/" + key)).putExtra("section", "calendar").putExtra("calendar_date", key).putExtra("add_event", true);
                PendingIntent addPi = PendingIntent.getActivity(c, 5000, addEvent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                v.setOnClickPendingIntent(cellId, addPi);
                v.setOnClickPendingIntent(numberId, addPi);
                v.setOnClickPendingIntent(eventId, addPi);
                v.setContentDescription(cellId, key + " 일정 추가");
                boolean inMonth = grid.get(Calendar.MONTH) == displayMonth.get(Calendar.MONTH) && grid.get(Calendar.YEAR) == displayMonth.get(Calendar.YEAR);
                v.setTextViewText(numberId, String.valueOf(grid.get(Calendar.DAY_OF_MONTH)));
                int color = inMonth ? Color.rgb(38, 62, 67) : Color.rgb(178, 188, 190);
                if (day == 0 && inMonth) color = Color.rgb(201, 104, 115);
                if (day == 6 && inMonth) color = Color.rgb(79, 148, 175);
                if (key.equals(todayKey)) color = Color.WHITE;
                v.setTextColor(numberId, color);
                v.setInt(numberId, "setBackgroundColor", key.equals(todayKey)?Color.rgb(29,36,40):Color.TRANSPARENT);
                List<Event> events = byDate.get(key);
                StringBuilder names = new StringBuilder();
                if (events != null && inMonth) {
                    for (int i = 0; i < events.size() && i < 2; i++) {
                        if (i > 0) names.append(" · ");
                        names.append(events.get(i).title);
                    }
                    if (events.size() > 2) names.append(" +").append(events.size() - 2);
                }
                v.setTextViewText(eventId, names.toString());
                v.setViewVisibility(eventId, names.length() == 0 ? View.INVISIBLE : View.VISIBLE);

                grid.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        v.setTextViewText(R.id.today_title, "TODAY · " + today.size());
        int routineDone = 0; for (Event item : d.routines) if (item.done) routineDone++;
        v.setTextViewText(R.id.routine_title, "ROUTINE · " + routineDone + "/" + d.routines.size());
        bindCheckList(c, v, R.id.today_list, today, 100);
        bindTextList(c, v, R.id.memo_list, d.memos, "메모가 없어요");
        bindCheckList(c, v, R.id.routine_list, d.routines, 200);
        if (!d.routineError.isEmpty()) bindTextList(c, v, R.id.routine_list, new ArrayList<>(), d.routineError);
        setSectionLink(c, v, R.id.today_list, "todo", 10);
        setSectionLink(c, v, R.id.memo_list, "memo", 11);
        setSectionLink(c, v, R.id.routine_list, "routine", 12);
        v.setTextViewText(R.id.wish_summary, "♡ WISH · " + d.wishes);
        v.setTextViewText(R.id.money_summary, "◇ BLEEDING · " + (d.money.isEmpty() ? "0" : d.money));
        v.setTextViewText(R.id.packaging_summary, "◇ PACKAGING");
        setSectionLink(c, v, R.id.wish_summary, "wishlist", 20);
        setSectionLink(c, v, R.id.money_summary, "bleeding", 21);
        v.setOnClickPendingIntent(R.id.packaging_summary, PendingIntent.getActivity(c, 23, notion, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        bindCareWeek(c, v, d);
        return v;
    }

    private static int viewId(Context c,String name){return c.getResources().getIdentifier(name,"id",c.getPackageName());}
    private static String listName(int container){return container==R.id.today_list?"today":container==R.id.memo_list?"memo":"routine";}
    private static void bindCheckList(Context c,RemoteViews v,int container,List<Event> items,int requestBase){
        String name=listName(container);int count=Math.min(5,items.size());
        for(int i=0;i<5;i++){
            int row=viewId(c,name+"_row_"+i),check=viewId(c,name+"_check_"+i),text=viewId(c,name+"_text_"+i);
            v.setViewVisibility(row,i<Math.max(1,count)?View.VISIBLE:View.GONE);
            v.setViewVisibility(check,i<count?View.VISIBLE:View.GONE);
            v.setTextViewText(text,i<count?items.get(i).title:(i==0?"· 비어 있어요":""));
            v.setOnClickPendingIntent(check,null);
            if(i<count){Event item=items.get(i);v.setTextViewText(check,item.done?"✓":"○");Intent toggle=new Intent(c,WidgetProvider.class).setAction(WidgetProvider.ACTION_TOGGLE).putExtra("page",item.id).putExtra("done",item.done);v.setOnClickPendingIntent(check,PendingIntent.getBroadcast(c,requestBase+i,toggle,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));}
        }
    }
    private static void bindTextList(Context c,RemoteViews v,int container,List<Event> items,String empty){
        String name=listName(container);int count=Math.min(5,items.size());
        for(int i=0;i<5;i++){
            v.setViewVisibility(viewId(c,name+"_row_"+i),i<Math.max(1,count)?View.VISIBLE:View.GONE);
            v.setViewVisibility(viewId(c,name+"_check_"+i),View.GONE);
            v.setTextViewText(viewId(c,name+"_text_"+i),i<count?"· "+items.get(i).title:(i==0?"· "+empty:""));
        }
    }

    private static void setSectionLink(Context c, RemoteViews v, int viewId, String section, int requestCode) {
        Intent open = new Intent(c, MainActivity.class).putExtra("section", section);
        v.setOnClickPendingIntent(viewId, PendingIntent.getActivity(c, requestCode, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static Calendar careWeekStart(Context c) {
        int offset = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("care_week_offset", 0);
        Calendar day = Calendar.getInstance(); day.set(Calendar.HOUR_OF_DAY, 12);
        int mondayDelta = day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day.get(Calendar.DAY_OF_WEEK);
        day.add(Calendar.DAY_OF_MONTH, mondayDelta + offset * 7);
        return day;
    }

    private static Map<String, NotionClient.CareEntry> loadLocalCareWeek(Context c, Calendar start) {
        Map<String, NotionClient.CareEntry> out = new HashMap<>(); Calendar day = (Calendar) start.clone(); SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        android.content.SharedPreferences p = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        for (int i = 0; i < 7; i++) { String date = f.format(day.getTime()); NotionClient.CareEntry x = new NotionClient.CareEntry(); x.date=date; x.breakfast=p.getString("care_"+date+"_breakfast",""); x.lunch=p.getString("care_"+date+"_lunch",""); x.dinner=p.getString("care_"+date+"_dinner",""); x.snack=p.getString("care_"+date+"_snack",""); x.note=p.getString("care_"+date+"_note",""); if (!x.breakfast.trim().isEmpty()||!x.lunch.trim().isEmpty()||!x.dinner.trim().isEmpty()||!x.snack.trim().isEmpty()||!x.note.trim().isEmpty()) out.put(date,x); day.add(Calendar.DAY_OF_MONTH,1); }
        return out;
    }
    private static void saveLocalCare(Context c, NotionClient.CareEntry x) {
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("care_"+x.date+"_breakfast",x.breakfast).putString("care_"+x.date+"_lunch",x.lunch).putString("care_"+x.date+"_dinner",x.dinner).putString("care_"+x.date+"_snack",x.snack).putString("care_"+x.date+"_note",x.note).apply();
    }

    private static String compactMeal(NotionClient.CareEntry entry) {
        String[][] fields = {
                {"아", entry.breakfast}, {"점", entry.lunch},
                {"저", entry.dinner}, {"간", entry.snack}
        };
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (String[] field : fields) {
            String clean = field[1] == null ? "" : field[1].trim().replaceAll("\\s+", " ");
            if (clean.isEmpty()) continue;
            if (clean.length() > 7) clean = clean.substring(0, 7) + "…";
            if (out.length() > 0) out.append("\n");
            out.append(field[0]).append(" ").append(clean);
            shown++;
            if (shown == 2) break;
        }
        return out.toString();
    }

    private static void bindCareWeek(Context c, RemoteViews v, Dashboard d) {
        Calendar day = (Calendar) d.careStart.clone();
        int defaultDay = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7;
        int selected = Math.max(0,Math.min(6,c.getSharedPreferences("prefs",Context.MODE_PRIVATE).getInt("care_selected_day",defaultDay)));
        day.add(Calendar.DAY_OF_MONTH,selected);
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(day.getTime());
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        v.setTextViewText(R.id.care_title,new SimpleDateFormat("M월 d일 E요일",Locale.KOREAN).format(day.getTime())+(date.equals(today)?" · 오늘":""));
        NotionClient.CareEntry entry=d.care.get(date);
        int[] ids={R.id.meal_breakfast,R.id.meal_lunch,R.id.meal_dinner,R.id.meal_snack};
        String[] labels={"아침","점심","저녁","간식"},fields={"breakfast","lunch","dinner","snack"};
        String[] values=entry==null?new String[]{"","","",""}:new String[]{entry.breakfast,entry.lunch,entry.dinner,entry.snack};
        for(int i=0;i<4;i++){
            v.setTextViewText(ids[i],labels[i]+"　"+(values[i].trim().isEmpty()?"기록하기":values[i].trim().replaceAll("\\s+"," ")));
            Intent edit=new Intent(c,MainActivity.class).setData(android.net.Uri.parse("hyeona://meal/"+date+"/"+fields[i])).putExtra("section","care").putExtra("care_date",date).putExtra("care_field",fields[i]);
            v.setOnClickPendingIntent(ids[i],PendingIntent.getActivity(c,4300+i,edit,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
            v.setContentDescription(ids[i],date+" "+labels[i]+" 수정");
        }
        Intent prev=new Intent(c,WidgetProvider.class).setAction(WidgetProvider.ACTION_PREV_CARE_WEEK);
        Intent next=new Intent(c,WidgetProvider.class).setAction(WidgetProvider.ACTION_NEXT_CARE_WEEK);
        v.setOnClickPendingIntent(R.id.care_prev,PendingIntent.getBroadcast(c,30,prev,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.care_next,PendingIntent.getBroadcast(c,31,next,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
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
            out.add(new Event(p.getString("id"), title.getJSONObject(0).optString("plain_text", "할 일"), dateValue, (props.optJSONObject("완료") != null && props.optJSONObject("완료").optBoolean("checkbox"))));
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

    static void today(Context c) {
        c.getSharedPreferences("prefs",Context.MODE_PRIVATE).edit().putInt("widget_month_offset",0).putInt("care_week_offset",0).remove("care_selected_day").apply();
        updateAll(c);
    }

    static void selectCare(Context c, int day) {
        c.getSharedPreferences("prefs",Context.MODE_PRIVATE).edit().putInt("care_selected_day",Math.max(0,Math.min(6,day))).apply();
        if(lastDashboard == null || lastMonth == null) {updateAll(c); return;}
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        manager.updateAppWidget(new ComponentName(c,WidgetProvider.class),views(c,lastDashboard,lastMonth));
    }

    static void moveCareWeek(Context c, int amount) {
        android.content.SharedPreferences p=c.getSharedPreferences("prefs",Context.MODE_PRIVATE);
        int weekday=p.getInt("care_selected_day",(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7)+amount;
        int week=p.getInt("care_week_offset",0);
        if(weekday<0){weekday=6;week--;}else if(weekday>6){weekday=0;week++;}
        p.edit().putInt("care_week_offset",week).putInt("care_selected_day",weekday).apply();
        if(lastDashboard!=null&&lastMonth!=null&&careWeekStart(c).get(Calendar.DAY_OF_YEAR)==lastDashboard.careStart.get(Calendar.DAY_OF_YEAR)&&careWeekStart(c).get(Calendar.YEAR)==lastDashboard.careStart.get(Calendar.YEAR)){
            AppWidgetManager.getInstance(c).updateAppWidget(new ComponentName(c,WidgetProvider.class),views(c,lastDashboard,lastMonth));
        }else updateAll(c);
    }

    private static JSONObject request(String token, String method, String url, JSONObject body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod(method);
        h.setRequestProperty("Authorization", "Bearer " + token);
        h.setRequestProperty("Notion-Version", "2022-06-28");
        h.setRequestProperty("Content-Type", "application/json");
        h.setConnectTimeout(10000); h.setReadTimeout(15000);
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
        Calendar careStart;
        Map<String, NotionClient.CareEntry> care = new HashMap<>();
        String careError = "", routineError = "";
        List<Event> events = new ArrayList<>(), todos = new ArrayList<>(), memos = new ArrayList<>(), routines = new ArrayList<>(); String error = "", money = "", brain = ""; int wishes = 0;
    }
    private static final class Event {
        final String id, title, date; final boolean done;
        Event(String id, String title, String date, boolean done) { this.id = id; this.title = title; this.date = date; this.done = done; }
    }
}
