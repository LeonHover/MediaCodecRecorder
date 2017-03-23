package io.github.leonhover.videorecorder.samples;

import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.io.IOException;

import io.github.leonhover.videorecorder.pub.Profile;
import io.github.leonhover.videorecorder.recorder.VideoRecorder;
import io.github.leonhover.videorecorder.recorder.android.AndroidRecorder;

public class RecordingActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "RecordingActivity";

    public static final String TEST_VIDEO_RECORDER_OUTPUT = "/sdcard/videorecorder.mp4";

    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private CheckBox mRecordingControl;

    private AndroidRecorder mVideoRecorder;

    private boolean isSurfaceReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(this);
        mRecordingControl = (CheckBox) findViewById(R.id.recording_control);
        mRecordingControl.setOnCheckedChangeListener(mControlCheck);
        mVideoRecorder = new AndroidRecorder();
        mVideoRecorder.setOutputFile(TEST_VIDEO_RECORDER_OUTPUT);

        Profile.Builder builder = new Profile.Builder();

        mVideoRecorder.setProfile(builder.build());

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
        mCamera = Camera.open();
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(640, 480);
        parameters.setRecordingHint(true);
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mVideoRecorder.setCamera(mCamera);
        mVideoRecorder.prepare();
        isSurfaceReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged format:" + format + " width:" + width + " height:" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        isSurfaceReady = false;
        mCamera.stopPreview();
        mCamera.release();
    }

    private void startRecording() {
        if (isSurfaceReady) {
            mCamera.unlock();
            mVideoRecorder.start();
        }
    }

    private void stopRecording() {
        mVideoRecorder.stop();
        mCamera.lock();
    }

    private CheckBox.OnCheckedChangeListener mControlCheck = new CheckBox.OnCheckedChangeListener(){

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                startRecording();
            } else {
                stopRecording();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mVideoRecorder.stop();
        mVideoRecorder.release();
    }
}
