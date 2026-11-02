package com.hyeona.widget;
import android.graphics.Color;
import org.json.JSONObject;
final class EventColors {
 static final String PROPERTY="일정 색상";
 static final String[] NAMES={"기본","핑크","보라","파랑","민트","주황","노랑"};
 private static final String[] BACK={"#DCECEE","#F8DCE6","#E9DFF7","#DCE9FC","#D6EFE4","#F9E1CE","#F5EDC7"};
 private static final String[] INK={"#324F58","#7F294B","#553579","#254F87","#225E49","#7C451E","#63531B"};
 static int index(String name){for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(name))return i;return 0;}
 static int background(String name){return Color.parseColor(BACK[index(name)]);}
 static int foreground(String name){return Color.parseColor(INK[index(name)]);}
 static String read(JSONObject props){JSONObject p=props.optJSONObject(PROPERTY);JSONObject s=p==null?null:p.optJSONObject("select");return s==null?NAMES[0]:NAMES[index(s.optString("name"))];}
}
