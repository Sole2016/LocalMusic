package com.zy.ppmusic;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.squareup.leakcanary.LeakCanary;
import com.zy.ppmusic.utils.CrashHandler;
import com.zy.ppmusic.utils.PrintLog;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * @author ZhiTouPC
 */
public class App extends Application {
    public static final String LOCAL_DATA_TABLE_NAME = "CACHE_PATH_LIST";
    private static LinkedHashMap<String, WeakReference<AppCompatActivity>> mActivityLists;

    public static App getInstance() {
        mActivityLists = new LinkedHashMap<>();
        return mAppInstance;
    }

    private static App mAppInstance;

    public static int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    @Override
    public Resources getResources() {
        Resources resources = super.getResources();
        Configuration configuration = resources.getConfiguration();
        float defaultValue = 1.0f;
        if (configuration.fontScale != defaultValue) {
            configuration.fontScale = defaultValue;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                createConfigurationContext(configuration);
            }
            return resources;
        }
        return resources;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAppInstance = this;
        StrictMode.enableDefaults();
        LeakCanary.install(this);
//        CrashHandler handler = new CrashHandler(this);
//        handler.attach();
    }

    public Context getContext() {
        return this.getApplicationContext();
    }

    public void createActivity(AppCompatActivity activity) {
        mActivityLists.put(activity.getLocalClassName(), new WeakReference<>(activity));
    }

    public void destroyActivity(AppCompatActivity activity) {
        if (!mActivityLists.containsKey(activity.getLocalClassName())) {
            PrintLog.e("not found this activity " + activity.getLocalClassName());
            return;
        }
        WeakReference<AppCompatActivity> activityWeakReference = mActivityLists.get(activity.getLocalClassName());
        if (activityWeakReference.get() != null) {
            activityWeakReference.clear();
        }
    }

    public void killSelf() {
        Iterator<WeakReference<AppCompatActivity>> iterator = mActivityLists.values().iterator();
        while (iterator.hasNext()) {
            WeakReference<AppCompatActivity> activityWeakReference = iterator.next();
            if (activityWeakReference.get() != null) {
                activityWeakReference.get().finish();
            }
            activityWeakReference.clear();
            iterator.remove();
        }
        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);
        String command = "kill -9 " + pid;
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
