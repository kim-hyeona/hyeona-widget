package com.hyeona.widget;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.json.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class TodayTasksTest {
 List<JSONObject> requests;String day;NotionClient.Transport original;
 JSONObject row(String id,String date)throws Exception{return new JSONObject().put("id",id).put("properties",new JSONObject().put("할 일",new JSONObject().put("title",new JSONArray().put(new JSONObject().put("plain_text","같은 제목")))).put("날짜",new JSONObject().put("date",date.isEmpty()?JSONObject.NULL:new JSONObject().put("start",date))).put("완료",new JSONObject().put("checkbox",false)));}
 @Before public void setup()throws Exception{original=NotionClient.transport;day=TaskDates.today();requests=new ArrayList<>();NotionClient.transport=(token,method,path,body)->{if(method.equals("GET"))return new JSONObject().put("properties",new JSONObject().put("할 일",new JSONObject().put("type","title")).put("날짜",new JSONObject().put("type","date")).put("완료",new JSONObject().put("type","checkbox")));requests.add(body==null?new JSONObject():body);return new JSONObject().put("results",new JSONArray().put(row("today",day)).put(row("tomorrow",LocalDate.parse(day).plusDays(1).toString())).put(row("yesterday",LocalDate.parse(day).minusDays(1).toString())).put(row("undated",""))).put("has_more",false);};}
 @After public void cleanup(){NotionClient.transport=original;}
 @Test public void sameTitleTomorrowYesterdayAndUndatedAreExcludedEvenIfApiReturnsThem()throws Exception{List<NotionClient.Item> all=NotionClient.query("fake","todo");assertEquals(1,all.size());assertEquals("today",all.get(0).id);assertEquals(day,requests.get(0).getJSONObject("filter").getJSONArray("or").getJSONObject(0).getJSONObject("date").getString("equals"));}
 @Test public void allViewKeepsFutureAndUndatedAccessible()throws Exception{List<NotionClient.Item> all=NotionClient.allTodos("fake");assertEquals(4,all.size());assertFalse(requests.get(0).has("filter"));assertEquals("",all.get(3).date);assertEquals(LocalDate.parse(day).plusDays(1).toString(),all.get(1).date);}
 @Test public void createAndUpdateSendChosenDateToTodoDatabase()throws Exception{
  NotionClient.Item item=new NotionClient.Item();item.title="날짜 선택 테스트";item.date=LocalDate.parse(day).plusDays(1).toString();
  NotionClient.transport=(token,method,path,body)->{if(method.equals("GET"))return new JSONObject().put("properties",new JSONObject().put("할 일",new JSONObject().put("type","title")).put("완료",new JSONObject()));requests.add(body);return new JSONObject().put("id","new");};
  NotionClient.create("fake","todo",item);assertEquals(item.date,requests.get(0).getJSONObject("properties").getJSONObject("날짜").getJSONObject("date").getString("start"));assertEquals("3cd946f45bc280599404f0b86f771142",requests.get(0).getJSONObject("parent").getString("database_id"));
  item.date=day;NotionClient.update("fake","todo",item);assertEquals(day,requests.get(1).getJSONObject("properties").getJSONObject("날짜").getJSONObject("date").getString("start"));
 }
 @Test public void widgetCacheRejectsAnotherDayLegacyUndatedAndFutureRows(){WidgetUpdater.Dashboard d=new WidgetUpdater.Dashboard();d.todosDay=LocalDate.parse(day).minusDays(1).toString();d.todos.add(new WidgetUpdater.Event("old","old",day,false));WidgetUpdater.sanitizeTodos(d);assertTrue(d.todos.isEmpty());d.todosDay=day;d.todos.add(new WidgetUpdater.Event("today","same",day,false));d.todos.add(new WidgetUpdater.Event("future","same",LocalDate.parse(day).plusDays(1).toString(),false));d.todos.add(new WidgetUpdater.Event("unknown","same","",false));WidgetUpdater.sanitizeTodos(d);assertEquals(1,d.todos.size());assertEquals("today",d.todos.get(0).id);}
 @Test public void timezoneAndMidnightNeverUseRawUtcPrefix(){assertEquals("2026-11-26",TaskDates.dayOf("2026-11-25T15:00:00Z"));assertTrue(TaskDates.onDay("2026-11-25T23:30:00-05:00","2026-11-26"));assertFalse(TaskDates.onDay("2026-11-26","2026-11-25"));assertFalse(TaskDates.onDay("bad-date",day));}
 @Test public void phoneEditorOffersDatePickerAndKeepsChosenTomorrowDate()throws Exception{
  android.content.Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("prefs",0).edit().clear().commit();
  org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity activity=controller.get();
  java.lang.reflect.Field section=MainActivity.class.getDeclaredField("section");section.setAccessible(true);section.set(activity,"todo");
  java.lang.reflect.Method edit=MainActivity.class.getDeclaredMethod("edit",NotionClient.Item.class);edit.setAccessible(true);edit.invoke(activity,(Object)null);
  android.app.AlertDialog form=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();android.widget.Button pick=(android.widget.Button)findText(form.getWindow().getDecorView(),"날짜 선택");assertNotNull(pick);pick.performClick();
  android.app.DatePickerDialog picker=(android.app.DatePickerDialog)org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();LocalDate tomorrow=LocalDate.parse(day).plusDays(1);picker.getDatePicker().updateDate(tomorrow.getYear(),tomorrow.getMonthValue()-1,tomorrow.getDayOfMonth());org.robolectric.Shadows.shadowOf(picker).getOnDateSetListenerCallback().onDateSet(picker.getDatePicker(),tomorrow.getYear(),tomorrow.getMonthValue()-1,tomorrow.getDayOfMonth());picker.dismiss();
  java.util.List<android.widget.EditText> fields=new ArrayList<>();collectEditors(form.getWindow().getDecorView(),fields);boolean found=false;for(android.widget.EditText f:fields)if(tomorrow.toString().equals(f.getText().toString()))found=true;assertTrue(found);form.dismiss();controller.pause().stop().destroy();
 }
 static android.view.View findText(android.view.View view,String text){if(view instanceof android.widget.TextView&&text.equals(((android.widget.TextView)view).getText().toString()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View result=findText(group.getChildAt(i),text);if(result!=null)return result;}}return null;}
 static void collectEditors(android.view.View view,List<android.widget.EditText> out){if(view instanceof android.widget.EditText)out.add((android.widget.EditText)view);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectEditors(group.getChildAt(i),out);}}

}
