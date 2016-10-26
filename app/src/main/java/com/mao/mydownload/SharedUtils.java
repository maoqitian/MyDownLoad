package com.mao.mydownload;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by 毛麒添 on 2016/10/25 0025.
 * 将断点文件位置记录到sharedPreferences
 */

public class SharedUtils {

    //获取最后下载位置
    public static int getLastPosition(Context context,int threadID){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return  sharedPreferences.getInt("lastPosition"+threadID,-1);
    }

    //设置最后下载位置
    public static void setLastPosition(Context context,int position,int threadID){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putInt("lastPosition"+threadID,position).commit();
    }

}
