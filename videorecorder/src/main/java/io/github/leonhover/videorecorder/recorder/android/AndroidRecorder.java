package io.github.leonhover.videorecorder.recorder.android;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

import io.github.leonhover.videorecorder.recorder.VideoRecorder;

/**
 * Created by wangzongliang on 17-3-23.
 */

public class AndroidRecorder extends VideoRecorder implements MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {

    private static final String TAG = "AndroidRecorder";

    private MediaRecorder mMediaRecorder;

    public AndroidRecorder() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
    }

    public void setCamera(Camera camera) {
        mMediaRecorder.setCamera(camera);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setInputSurface(Surface surface) {
        mMediaRecorder.setInputSurface(surface);
    }

    @Override
    public void reset() {
        Log.d(TAG, "reset");
        mMediaRecorder.reset();
    }

    @Override
    public void prepare() {
        Log.d(TAG, "prepare");
        if (mProfile == null || TextUtils.isEmpty(mPath)) {
            throw new IllegalStateException("Recorder profile is null! You should prepare after setProfile & setOutputFile");
        }

        try {

            mMediaRecorder.setOutputFile(mPath);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //配置音频
            mMediaRecorder.setAudioChannels(mProfile.audioChannelCount);
            mMediaRecorder.setAudioSamplingRate(mProfile.audioSamplingRate);
            mMediaRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);

            //配置视频
            mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
            mMediaRecorder.setVideoSize(mProfile.videoWidth, mProfile.videoHeight);
            mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate);

            //其他

            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

            mMediaRecorder.setOrientationHint(mProfile.orientationHint);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        Log.d(TAG, "start");
        mMediaRecorder.start();
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
        mMediaRecorder.stop();
    }

    @Override
    public void release() {
        Log.d(TAG, "release");
        mMediaRecorder.release();
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.d(TAG, "onError what:" + what + " extra:" + extra);
        switch (what) {
            case MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN:
                Log.d(TAG, "onInfo what:MEDIA_RECORDER_INFO_MAX_DURATION_REACHED" + " extra:" + extra);
                break;
            case MediaRecorder.MEDIA_ERROR_SERVER_DIED:
                Log.d(TAG, "onInfo what:MEDIA_RECORDER_INFO_UNKNOWN" + " extra:" + extra);
                break;
        }
        notifyRecorderInfo(what, extra);
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.d(TAG, "onInfo what:" + what + " extra:" + extra);
        switch (what) {
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                Log.d(TAG, "onInfo what:MEDIA_RECORDER_INFO_MAX_DURATION_REACHED" + " extra:" + extra);
                break;
            case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
                Log.d(TAG, "onInfo what:MEDIA_RECORDER_INFO_UNKNOWN" + " extra:" + extra);
                break;
        }
        notifyRecorderInfo(what, extra);
    }
}
