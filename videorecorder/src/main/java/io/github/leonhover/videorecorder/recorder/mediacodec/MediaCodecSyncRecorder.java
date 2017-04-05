package io.github.leonhover.videorecorder.recorder.mediacodec;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGLContext;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.github.leonhover.videorecorder.opengl.GLContext;
import io.github.leonhover.videorecorder.opengl.GLDrawer;
import io.github.leonhover.videorecorder.opengl.GLSurface;
import io.github.leonhover.videorecorder.recorder.VideoRecorder;

/**
 * Created by wangzongliang on 17-3-31.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaCodecSyncRecorder extends VideoRecorder implements Handler.Callback {

    private static final String TAG = "MediaCodecSyncRecorder";

    private static final String VIDEO_ENCODER_THREAD = "encoder_thread";

    public static final int MSG_ENCODER_PREPARE = 0x1000;
    public static final int MSG_ENCODER_START = 0x1001;
    public static final int MSG_ENCODER_STOP = 0x1002;
    public static final int MSG_ENCODER_INPUT_SURFACE_UPDATE = 0x1003;

    public static final String MIME_TYPE_AVC = "video/avc";

    //Ready?
    private boolean isPrepared = false;
    //转码中？
    private boolean isEncoding = false;

    //编码线程
    private HandlerThread mEncodingThread;
    private Handler mEncodingHandler;


    private MediaCodec mMediaCodec;
    private MediaMuxer mMediaMuxer;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mVideoTrackId;
    private Surface mInputSurface;

    public MediaCodecSyncRecorder() {
        Log.d(TAG, "MediaCodecSyncRecorder construct");

        //初始化编码线程
        mEncodingThread = new HandlerThread(VIDEO_ENCODER_THREAD);
        mEncodingThread.start();
        mEncodingHandler = new Handler(mEncodingThread.getLooper(), this);

        Log.d(TAG, "MediaCodecSyncRecorder construct end");
    }

    @Override
    public void reset() {

    }

    public void prepare() {
        mEncodingHandler.sendEmptyMessage(MSG_ENCODER_PREPARE);
    }

    @Override
    public void start() {
        mEncodingHandler.sendEmptyMessage(MSG_ENCODER_START);
    }

    @Override
    public void stop() {
        mEncodingHandler.sendEmptyMessage(MSG_ENCODER_STOP);

    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void updateInputSurface(SurfaceTexture surfaceTexture, int textureId) {
        Message updateSurfaceMsg = mEncodingHandler.obtainMessage(MSG_ENCODER_INPUT_SURFACE_UPDATE);
        updateSurfaceMsg.obj = surfaceTexture;
        updateSurfaceMsg.arg1 = textureId;
        updateSurfaceMsg.sendToTarget();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }


    public void release() {

    }

    private GLContext mGLContext;
    private GLDrawer mGLDrawer;
    private GLSurface mGLSurface;

    private EGLContext mShareGLContext;

    public void setShareGlContext(EGLContext eglContext) {
        this.mShareGLContext = eglContext;
    }

    @Override
    public boolean handleMessage(Message msg) {

        switch (msg.what) {
            case MSG_ENCODER_PREPARE:

                //组建VideoFormat
                MediaFormat mVideoFormat = MediaFormat.createVideoFormat(MIME_TYPE_AVC, 480, 480);
                mVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1024_000);
                mVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                mVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
                mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

                //选择合适的Codec
                String codecName = "";
                for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                    MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                    if (codecInfo.isEncoder()) {
                        try {
                            MediaCodecInfo.CodecCapabilities codecCapabilities = codecInfo.getCapabilitiesForType(MIME_TYPE_AVC);
                            if (codecCapabilities != null) {
                                codecName = codecInfo.getName();
                                Log.d(TAG, "codecName:" + codecName);
                            }
                        } catch (IllegalArgumentException e) {

                        }
                    }
                }

                if (TextUtils.isEmpty(codecName)) {
                    Log.e(TAG, "codecName is empty!!!");
                }

                try {
                    mMediaCodec = MediaCodec.createByCodecName(codecName);
                    mMediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    mMediaCodec.configure(mVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    mInputSurface = mMediaCodec.createInputSurface();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mBufferInfo = new MediaCodec.BufferInfo();
                try {
                    mMediaMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mGLContext = new GLContext(this.mShareGLContext);
                mGLSurface = new GLSurface(mGLContext);
                mGLSurface.createSurface(mInputSurface);
                mGLSurface.makeCurrent();
                mGLDrawer = new GLDrawer();
                isPrepared = true;
                break;
            case MSG_ENCODER_START:
                while (!isPrepared) {

                }
                mMediaCodec.start();
                isEncoding = true;
                break;
            case MSG_ENCODER_STOP:
                isPrepared = false;
                isEncoding = false;
                mMediaCodec.signalEndOfInputStream();
                mMediaMuxer.stop();
                mMediaMuxer.release();
                mMediaCodec.stop();
                mMediaCodec.release();
                break;
            case MSG_ENCODER_INPUT_SURFACE_UPDATE:
                if (isEncoding) {
                    SurfaceTexture surfaceTexture = (SurfaceTexture) msg.obj;
                    float[] mtx = new float[16];
                    int textureId = msg.arg1;
                    surfaceTexture.getTransformMatrix(mtx);
                    mGLSurface.makeCurrent();
                    Matrix.setIdentityM(mtx, 0);
                    mGLDrawer.draw(textureId, mvpMtx, mtx);
                    mGLSurface.setPresentationTime(System.nanoTime());
//                    try {
//                        File png = new File("/sdcard/xiaokaxiu/" + System.currentTimeMillis() + ".png");
//                        mGLSurface.saveFrame(png);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                    mGLSurface.swapBuffers();
                    encode();
                }
                break;
        }

        return true;
    }

    static float[] mvpMtx;

    static {
        mvpMtx = new float[16];
        Matrix.setIdentityM(mvpMtx, 0);
    }

    private void encode() {

        if (!isEncoding) {
            return;
        }


        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        while (isEncoding) {
            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
            Log.d(TAG, "outputBufferIndex=" + outputBufferIndex + " flags:" + mBufferInfo.flags);
            if (outputBufferIndex >= 0) {
                // outputBuffers[outputBufferId] is ready to be processed or rendered.

                ByteBuffer encodedData = outputBuffers[outputBufferIndex];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    try {
                        mMediaMuxer.writeSampleData(mVideoTrackId, encodedData, mBufferInfo);
                    } catch (Exception e) {

                    }
                    Log.d(TAG, "writeSampleData");
//                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }

                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);


                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "Encoding  end of stream");
                    isEncoding = false;
                    break;      // out of while
                }

            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat mediaFormat = mMediaCodec.getOutputFormat();
                Log.d(TAG, "video:" + mediaFormat.toString());
                mVideoTrackId = mMediaMuxer.addTrack(mediaFormat);
                mMediaMuxer.start();
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }

        }
    }

//    private long prevOutputPTSUs = 0;
//
//    /**
//     * get next encoding presentationTimeUs
//     *
//     * @return
//     */
//    protected long getPTSUs() {
//        long result = System.nanoTime() / 1000L;
//        // presentationTimeUs should be monotonic
//        // otherwise muxer fail to write
//        if (result < prevOutputPTSUs)
//            result = (prevOutputPTSUs - result) + result;
//        return result;
//    }
}
