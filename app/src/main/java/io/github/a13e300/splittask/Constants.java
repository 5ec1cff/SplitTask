package io.github.a13e300.splittask;

import android.content.ComponentName;

public class Constants {
    public static final String ACTION_REQUEST_MOVE_ACTIVITY = "REQUEST_MOVE_ACTIVITY";
    public static final String PERMISSION = "io.github.a13e300.splittask.SPLIT_TASK";
    public static final ComponentName STUB_ACTIVITY = new ComponentName(BuildConfig.APPLICATION_ID, StubActivity.class.getName());
}
