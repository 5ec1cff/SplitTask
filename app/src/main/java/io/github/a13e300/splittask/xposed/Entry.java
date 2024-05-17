package io.github.a13e300.splittask.xposed;

import static io.github.a13e300.splittask.Constants.ACTION_REQUEST_MOVE_ACTIVITY;
import static io.github.a13e300.splittask.Constants.PERMISSION;
import static io.github.a13e300.splittask.Constants.STUB_ACTIVITY;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.a13e300.splittask.BuildConfig;

public class Entry extends BroadcastReceiver implements IXposedHookLoadPackage {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mHandler == null) return;
        mHandler.post(() -> {
            XposedBridge.log("try start activity");
            try {
                var top = getTopVisibleRootTask();
                if (top == null) {
                    XposedBridge.log("current top is null!");
                    return;
                }
                var taskId = XposedHelpers.getObjectField(top, "mTaskId");
                context.startActivity(
                        new Intent()
                                .setAction(ACTION_REQUEST_MOVE_ACTIVITY)
                                .setComponent(STUB_ACTIVITY)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                .setData(Uri.parse("splittask://" + taskId))
                );
            } catch (Throwable t) {
                XposedBridge.log("failed to start activity");
                XposedBridge.log(t);
            }
        });
    }

    private Handler mHandler;

    private void registerReceiver() {
        try {
            var thread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", ClassLoader.getSystemClassLoader()),
                    "currentActivityThread"
            );
            var context = (Context) XposedHelpers.getObjectField(thread, "mSystemContext");
            if (context != null) {
                var intentFilter = new IntentFilter();
                intentFilter.addAction(ACTION_REQUEST_MOVE_ACTIVITY);
                int flags = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    flags = Context.RECEIVER_EXPORTED;
                }
                context.registerReceiver(
                        this, intentFilter, PERMISSION, null, flags
                );
                XposedBridge.log("SplitTask: register success!");
                return;
            }
        } catch (Throwable ignore) {
        }
        mHandler.postDelayed(this::registerReceiver, 1000);
    }

    private Object mWMS;

    private Object getWMS() {
        if (mWMS == null) {
            mWMS = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.os.ServiceManager", ClassLoader.getSystemClassLoader()),
                    "getService", "window"
            );
        }
        return mWMS;
    }

    private Object mRWC;

    private Object getRWC() {
        if (mRWC == null) {
            mRWC = XposedHelpers.getObjectField(getWMS(), "mRoot");
        }
        return mRWC;
    }

    private Object getTopVisibleRootTask() {
        var rwc = getRWC();
        AtomicReference<Object> rootTask = new AtomicReference<>();
        Predicate callback = (task) -> {
            rootTask.set(task);
            XposedBridge.log("get root task " + task);
            return true;
        };
        XposedHelpers.callMethod(rwc, "forAllRootTasks", callback);
        return rootTask.get();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            XposedHelpers.callMethod(
                    XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass(
                                    "dalvik.system.VMRuntime", lpparam.classLoader
                            ), "getRuntime"
                    ),
                    "setHiddenApiExemptions",
                    (Object) new String[] {""}
            );
            return;
        }
        if (!"android".equals(lpparam.packageName) || !"android".equals(lpparam.processName)) return;
        HandlerThread mThread = new HandlerThread("");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mHandler.postDelayed(this::registerReceiver, 1000);

        var activityRecordClass = XposedHelpers.findClass(
                "com.android.server.wm.ActivityRecord", lpparam.classLoader
        );

        XposedBridge.hookAllMethods(
                XposedHelpers.findClass("com.android.server.wm.Task", lpparam.classLoader),
                "resetTaskIfNeeded",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        var task = XposedHelpers.callMethod(param.args[0] /*ActivityRecord taskTop*/, "getTask");
                        var intent = (Intent) XposedHelpers.getObjectField(task, "intent");
                        if (intent == null) return;
                        if (!ACTION_REQUEST_MOVE_ACTIVITY.equals(intent.getAction())) return;
                        var data = intent.getData();
                        if (data == null) return;
                        var targetTaskId = Integer.parseInt(data.getHost());
                        if (targetTaskId == -1) return;
                        Object targetTask;
                        // we can not get top root task here, because the root task is ourselves
                        var rwc = getRWC();
                        targetTask = XposedHelpers.callMethod(rwc, "getRootTask", targetTaskId);
                        if (targetTask == null) {
                            XposedBridge.log("target task not found (id=" + targetTaskId + ")");
                            return;
                        }
                        XposedBridge.log("try move task " + targetTaskId + " " + targetTask + " to " + task);

                        // we don't need to reparent if there is just one activity

                        var count = (int) XposedHelpers.callMethod(targetTask, "getChildCount");
                        if (count <= 1) {
                            XposedBridge.log("target task has task less or equal 1, abort");
                        }
                        var targetActivity = XposedHelpers.callMethod(targetTask, "getChildAt", count - 1);

                        // check if it is really an activity
                        // TODO: skip home activities
                        if (!activityRecordClass.isInstance(targetActivity)) return;

                        // FIXME: stub activity maybe still exist in task

                        var bottom = XposedHelpers.callMethod(targetTask, "getChildAt", 0);
                        if (activityRecordClass.isInstance(bottom)) {
                            if (STUB_ACTIVITY.equals(XposedHelpers.getObjectField(bottom, "mActivityComponent"))) {
                                // skip
                                return;
                            }
                        }

                        // fix taskAffinity to prevent from re-reparent
                        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/wm/ResetTargetTaskHelper.java;l=121;drc=337a2353aee7576b3aa637089852aa405c26bbef
                        var affinity = XposedHelpers.getObjectField(targetActivity, "taskAffinity");
                        XposedHelpers.setObjectField(task, "affinity", affinity);
                        XposedHelpers.setObjectField(task, "rootAffinity", affinity);

                        // then reparent it to our task !
                        XposedHelpers.callMethod(targetActivity, "reparent", task,
                                XposedHelpers.callMethod(task, "getChildCount"),
                                "SplitTask");
                }
            }
        );
    }
}
