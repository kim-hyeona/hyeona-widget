package com.hyeona.widget;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.net.Uri;
import java.security.MessageDigest;
final class RabbitSetup {
 static String token(Context c){try{ProviderInfo provider=c.getPackageManager().resolveContentProvider("com.hyeona.rabbit.widgetsetup",0);if(provider==null||!"com.hyeona.rabbit".equals(provider.packageName))return "";PackageInfo p=c.getPackageManager().getPackageInfo(provider.packageName,PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES);Signature[] signatures=Build.VERSION.SDK_INT>=28&&p.signingInfo!=null?p.signingInfo.getApkContentsSigners():p.signatures;if(signatures==null||signatures.length!=1)return "";StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!"ad229c7aff52d99f63b5dc207493f838e085e20d69245910b999ff17fbd159fe".equals(hex.toString()))return "";Bundle result=c.getContentResolver().call(Uri.parse("content://com.hyeona.rabbit.widgetsetup"),"connection",null,null);return result!=null&&result.getBoolean("connected")?result.getString("token",""):"";}catch(Exception ignored){return "";}}
}
