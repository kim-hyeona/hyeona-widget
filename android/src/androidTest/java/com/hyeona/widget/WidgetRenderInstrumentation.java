package com.hyeona.widget;
import android.app.*;
import android.content.Context;
import android.os.*;
import android.view.View;
import android.widget.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
public class WidgetRenderInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{final Throwable[] failure={null};runOnMainSync(()->{try{verify();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw failure[0];result.putString("stream","WIDGET_RENDER_OK\n");finish(-1,result);}catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
 private void verify()throws Exception{
  Context c=getTargetContext();Class<?> dashboard=Class.forName("com.hyeona.widget.WidgetUpdater$Dashboard");Constructor<?> dc=dashboard.getDeclaredConstructor();dc.setAccessible(true);
  Method render=WidgetUpdater.class.getDeclaredMethod("views",Context.class,dashboard,Calendar.class,int.class);render.setAccessible(true);
  Class<?> event=Class.forName("com.hyeona.widget.WidgetUpdater$Event");Constructor<?> ec=event.getDeclaredConstructor(String.class,String.class,String.class,boolean.class,String.class);ec.setAccessible(true);
  View previous=null;float density=c.getResources().getDisplayMetrics().density;
  for(int height:new int[]{520,600,720,800})for(int offset=0;offset<12;offset++)for(int populated=0;populated<2;populated++){
   Object d=dc.newInstance();Calendar month=Calendar.getInstance();month.set(2026,Calendar.NOVEMBER,1);month.add(Calendar.MONTH,offset);
   if(populated==1){for(String name:new String[]{"events","todos","memos","routines"}){Field f=dashboard.getDeclaredField(name);f.setAccessible(true);List items=(List)f.get(d);for(int i=0;i<8;i++)items.add(ec.newInstance("test-"+i,name.equals("routines")?"네트워크 진도나가기":"브런치 카페 약속",new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(month.getTime()),i<2,EventColors.NAMES[i%7]));}}
   RemoteViews original=(RemoteViews)render.invoke(null,c,d,month,height);Parcel parcel=Parcel.obtain();original.writeToParcel(parcel,0);parcel.setDataPosition(0);RemoteViews remote=RemoteViews.CREATOR.createFromParcel(parcel);parcel.recycle();
   FrameLayout parent=new FrameLayout(c);View view=remote.apply(c,parent);remote.reapply(c,view);int widthPx=(int)(384*density),heightPx=(int)(height*density);view.measure(View.MeasureSpec.makeMeasureSpec(widthPx,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(heightPx,View.MeasureSpec.EXACTLY));view.layout(0,0,widthPx,heightPx);
   if(previous!=null)remote.reapply(c,previous);previous=view;
   android.appwidget.AppWidgetHostView host=new android.appwidget.AppWidgetHostView(c);for(android.appwidget.AppWidgetProviderInfo info:android.appwidget.AppWidgetManager.getInstance(c).getInstalledProviders())if(info.provider.getPackageName().equals(c.getPackageName())){host.setAppWidget(100,info);break;}host.updateAppWidget(remote);if(host.findViewById(R.id.day_cell_0)==null)throw new AssertionError("Host entered error view");
   int meal=c.getResources().getIdentifier("meal_breakfast","id",c.getPackageName());if(meal!=0&&view.findViewById(meal)!=null)throw new AssertionError("Unexpected meal panel");
   TextView number=view.findViewById(R.id.day_number_0),memo=view.findViewById(R.id.memo_text_0),routine=view.findViewById(R.id.routine_text_0);
   float scaled=c.getResources().getDisplayMetrics().scaledDensity;
   if(Math.abs(number.getTextSize()/scaled-15)>0.1||Math.abs(memo.getTextSize()/scaled-14)>0.1)throw new AssertionError("Incorrect font size");
   if(populated==1){
    if(routine.getHeight()>((View)routine.getParent()).getHeight())throw new AssertionError("Routine clipped at height "+height);
    if(view.findViewById(R.id.routine_more).getBottom()>view.findViewById(R.id.detail_panels).getHeight())throw new AssertionError("Footer clipped");
    int first=month.get(Calendar.DAY_OF_WEEK)-1;TextView chip=view.findViewById(c.getResources().getIdentifier("day_event_"+first,"id",c.getPackageName()));
    if(!chip.hasOnClickListeners())throw new AssertionError("Event is not clickable");
    TextView second=view.findViewById(c.getResources().getIdentifier("day_event_second_"+first,"id",c.getPackageName()));
    if(height>=620&&((ColorDrawable)second.getBackground()).getColor()!=EventColors.background("핑크"))throw new AssertionError("Color not applied");
   }
   if(offset==0&&populated==1&&(height==600||height==720)){Bitmap bitmap=Bitmap.createBitmap(widthPx,heightPx,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream out=c.openFileOutput("widget-"+height+".png",0)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
  }
  NotionClient.Item item=new NotionClient.Item();item.date="2026-11-16T15:00:00+09:00";item.dateEnd="2026-11-16T16:00:00+09:00";item.color="보라";
  if(!NotionClient.calendarDate(item.copy()).getJSONObject("date").getString("end").equals(item.dateEnd))throw new AssertionError("Time range lost");
  if(!item.copy().color.equals("보라"))throw new AssertionError("Color lost");
  Notification n=NotificationKeeper.build(c,NotificationKeeper.ROUTINE_ID,"오늘 루틴 · 1/2","✓ 공부\n○ 준비");
  if((n.flags&Notification.FLAG_ONGOING_EVENT)==0||(n.flags&Notification.FLAG_NO_CLEAR)==0||n.contentIntent==null)throw new AssertionError("Routine notification not ongoing");
  if(!n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("○ 준비"))throw new AssertionError("Routine list missing");
 }
}
