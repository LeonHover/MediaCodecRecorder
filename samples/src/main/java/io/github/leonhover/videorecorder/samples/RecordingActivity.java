package io.github.leonhover.videorecorder.samples;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.io.IOException;

import io.github.leonhover.videorecorder.camera.CameraView;
import io.github.leonhover.videorecorder.pub.Profile;
import io.github.leonhover.videorecorder.recorder.mediacodec.MediaCodecRecorder;

public class RecordingActivity extends AppCompatActivity implements CameraView.CameraSurfaceListener {

    private static final String TAG = "RecordingActivity";

    public static final String TEST_VIDEO_RECORDER_OUTPUT = "/sdcard/videorecorder_%d.mp4";
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int VIDEO_WIDTH = 480;
    private static final int VIDEO_HEIGHT = 480;
    private static final int VIDEO_BIT_RATE = 1024 * 1024;
    private static final int VIDEO_I_FRAME_INTERVAL = 3;

    private Camera mCamera;
    private CameraView mCameraView;
    private CheckBox mRecordingControl;

    private MediaCodecRecorder mVideoRecorder;

    private boolean isSurfaceReady = false;

    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);
        mCameraView = (CameraView) findViewById(R.id.surfaceView);
        mCameraView.setRatio(1.0f);
        mCameraView.setCameraSurfaceListener(this);
        mRecordingControl = (CheckBox) findViewById(R.id.recording_control);
        mRecordingControl.setOnCheckedChangeListener(mControlCheck);
        mVideoRecorder = new MediaCodecRecorder();
    }

    private void startRecording() {
        Log.d(TAG, "startRecording");
        if (isSurfaceReady) {
            mVideoRecorder.setOutputFile(String.format(TEST_VIDEO_RECORDER_OUTPUT, count));
            Profile.Builder builder = new Profile.Builder();
            builder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
            builder.setVideoBitRate(VIDEO_BIT_RATE);
            builder.setVideoIFrameInterval(VIDEO_I_FRAME_INTERVAL);
            mVideoRecorder.setProfile(builder.build());
            mVideoRecorder.prepare();
            mCamera.unlock();
            mVideoRecorder.start();
        }
    }

    private void stopRecording() {
        Log.d(TAG, "stopRecording");
        mVideoRecorder.stop();
        mCamera.lock();
        count++;
    }

    private CheckBox.OnCheckedChangeListener mControlCheck = new CheckBox.OnCheckedChangeListener() {

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
    protected void onResume() {
        super.onResume();
        this.mCameraView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.mCameraView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mVideoRecorder.release();
    }

    @Override
    public void onCameraSurfaceCreate(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onCameraSurfaceCreate");
        mCamera = Camera.open();
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        mVideoRecorder.createInputSurfaceWindow(EGL14.eglGetCurrentContext());
        try {
            parameters.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
            mCameraView.setPreviewSize(PREVIEW_HEIGHT, PREVIEW_WIDTH);
            mVideoRecorder.setPreviewSize(PREVIEW_HEIGHT, PREVIEW_WIDTH);
            mCamera.setParameters(parameters);
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setDisplayOrientation(Profile.ORIENTATION_90);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        isSurfaceReady = true;
    }

    @Override
    public void onCameraSurfaceChanged(SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, "onCameraSurfaceChanged");
    }

    @Override
    public void onCameraSurfaceDestroy(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onCameraSurfaceDestroy");
        isSurfaceReady = false;
        mCamera.stopPreview();
        mCamera.release();
        if (mVideoRecorder.isRecording()) {
            mVideoRecorder.stop();
        }
    }

    @Override
    public void onCameraSurfaceUpdate(SurfaceTexture surfaceTexture, int textureId) {
        mVideoRecorder.updateInputSurfaceWindow(textureId, surfaceTexture);
    }
}
