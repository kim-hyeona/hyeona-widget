package com.hyeona.widget;
import java.time.*;
import java.util.*;
final class TaskDates {
 static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
 static String today(){return Instant.now().atZone(SEOUL).toLocalDate().toString();}
 static String dayOf(String raw){if(raw==null||raw.isEmpty())return "";try{return LocalDate.parse(raw).toString();}catch(Exception ignored){try{return OffsetDateTime.parse(raw).atZoneSameInstant(SEOUL).toLocalDate().toString();}catch(Exception invalid){return "";}}}
 static org.json.JSONObject filter(String day)throws org.json.JSONException{String next=LocalDate.parse(day).plusDays(1).toString();return new org.json.JSONObject().put("or",new org.json.JSONArray().put(new org.json.JSONObject().put("property","날짜").put("date",new org.json.JSONObject().put("equals",day))).put(new org.json.JSONObject().put("and",new org.json.JSONArray().put(new org.json.JSONObject().put("property","날짜").put("date",new org.json.JSONObject().put("on_or_after",day+"T00:00:00+09:00"))).put(new org.json.JSONObject().put("property","날짜").put("date",new org.json.JSONObject().put("before",next+"T00:00:00+09:00"))))));}
 static boolean onDay(String raw,String day){return !day.isEmpty()&&day.equals(dayOf(raw));}
 static List<NotionClient.Item> onlyToday(List<NotionClient.Item> items,String day){List<NotionClient.Item> out=new ArrayList<>();for(NotionClient.Item item:items)if(onDay(item.date,day))out.add(item);return out;}
}
