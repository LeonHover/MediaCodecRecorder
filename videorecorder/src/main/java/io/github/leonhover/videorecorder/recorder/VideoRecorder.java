package io.github.leonhover.videorecorder.recorder;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

import io.github.leonhover.videorecorder.pub.Profile;

/**
 * Created by wangzongliang on 17-3-23.
 */

public abstract class VideoRecorder {

    private static final String TAG = "VideoRecorder";

    /**
     * 输出的Video文件路径
     */
    protected String mPath;
    protected Profile mProfile;

    public void setProfile(Profile profile) {
        this.mProfile = profile;
    }

    public void setOutputFile(String path) {
        this.mPath = path;
    }

    public abstract void reset();

    public abstract void prepare();

    public abstract void start();

    public abstract void stop();

    public abstract void release();

    protected final void notifyRecorderInfo(int info, int extra) {
        Log.d(TAG, "notifyRecorderInfo info:"+info+" extra:"+extra);
    }

    private static class EventHandler extends Handler {
        private WeakReference<VideoRecorder> mVideoRecorderRef;

        public EventHandler(VideoRecorder vr, Looper looper) {
            super(looper);
            mVideoRecorderRef = new WeakReference<VideoRecorder>(vr);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }
}
