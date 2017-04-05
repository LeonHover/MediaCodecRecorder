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
import io.github.leonhover.videorecorder.recorder.mediacodec.MediaCodecSyncRecorder;

public class RecordingActivity extends AppCompatActivity implements CameraView.CameraSurfaceListener {

    private static final String TAG = "RecordingActivity";

    public static final String TEST_VIDEO_RECORDER_OUTPUT = "/sdcard/videorecorder.mp4";

    private Camera mCamera;
    private CameraView mCameraView;
    private CheckBox mRecordingControl;

//    private MediaCodecRecorder mVideoRecorder;
    private MediaCodecSyncRecorder mVideoRecorder;

    private boolean isSurfaceReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);
        mCameraView = (CameraView) findViewById(R.id.surfaceView);
        mCameraView.setCameraSurfaceListener(this);
        mRecordingControl = (CheckBox) findViewById(R.id.recording_control);
        mRecordingControl.setOnCheckedChangeListener(mControlCheck);
        mVideoRecorder = new MediaCodecSyncRecorder();

    }

    private void startRecording() {
        Log.d(TAG, "startRecording");
        if (isSurfaceReady) {
            mVideoRecorder.setOutputFile(TEST_VIDEO_RECORDER_OUTPUT);
            Profile.Builder builder = new Profile.Builder();
            mVideoRecorder.setProfile(builder.build());
//            mVideoRecorder.setCamera(mCamera);
            mVideoRecorder.prepare();
            mCamera.unlock();
            mCameraView.setOutputSurface(mVideoRecorder.getInputSurface());
            mVideoRecorder.start();
        }
    }

    private void stopRecording() {
        Log.d(TAG, "stopRecording");
        mVideoRecorder.stop();
        mCamera.lock();
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

        mVideoRecorder.stop();
        mVideoRecorder.release();
    }

    @Override
    public void onCameraSurfaceCreate(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onCameraSurfaceCreate");
        mVideoRecorder.setShareGlContext(EGL14.eglGetCurrentContext());
        mCamera = Camera.open();
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(640, 480);
        parameters.setRecordingHint(true);
        try {
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setDisplayOrientation(90);
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
    }

    @Override
    public void onCameraSurfaceUpdate(SurfaceTexture surfaceTexture,int textureId) {
        mVideoRecorder.updateInputSurface(surfaceTexture,textureId);
    }
}
