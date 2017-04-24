package io.github.leonhover.videorecorder.opengl.filter;

import android.annotation.TargetApi;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import java.nio.FloatBuffer;

import io.github.leonhover.videorecorder.opengl.GLUtil;

/**
 * Created by wangzongliang on 17-3-29.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GLSurfaceFilter {

    private static final String TAG = "GLDrawer";

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                    "}\n";
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";
    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,   // 0 bottom left
            1.0f, -1.0f,   // 1 bottom right
            -1.0f, 1.0f,   // 2 top left
            1.0f, 1.0f,   // 3 top right
    };
    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 0.0f,     // 0 bottom left
            1.0f, 0.0f,     // 1 bottom right
            0.0f, 1.0f,     // 2 top left
            1.0f, 1.0f      // 3 top right*/
    };


    private static final FloatBuffer FULL_RECTANGLE_BUF =
            GLUtil.createFloatBuffer(FULL_RECTANGLE_COORDS);
    private static final FloatBuffer FULL_RECTANGLE_TEX_BUF =
            GLUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);

    private int hProgram;
    private int mCoordsPerVertex;
    private int mVertexCount;
    private int mVertexStride;
    private int mTexCoordStride;
    private int mTextureTarget;

    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;

    private float[] mMvpMatrix;

    private static final int SIZEOF_FLOAT = Float.SIZE / 8;

    /**
     * Constructor
     * this should be called in GL context
     */
    public GLSurfaceFilter() {
        mCoordsPerVertex = 2;
        mVertexStride = mCoordsPerVertex * SIZEOF_FLOAT; // 8
        mVertexCount = FULL_RECTANGLE_COORDS.length / mCoordsPerVertex; //4
        mTexCoordStride = 2 * SIZEOF_FLOAT; //8

        hProgram = GLUtil.loadProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

        // get locations of attributes and uniforms

        maPositionLoc = GLES20.glGetAttribLocation(hProgram, "aPosition");
        GLUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(hProgram, "aTextureCoord");
        GLUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix");
        GLUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uTexMatrix");
        GLUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
    }

    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    public int createTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        int texId = textures[0];
        GLES20.glBindTexture(mTextureTarget, texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);

        return texId;
    }

    public void setMvpMatrix(float[] mvpMatrix) {
        this.mMvpMatrix = mvpMatrix;
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    public void release() {
        Log.d(TAG, "deleting program " + hProgram);
        GLES20.glDeleteProgram(hProgram);
        hProgram = -1;
    }

    public void draw(final int textureId, final float[] texMatrix) {
        draw(textureId, mMvpMatrix, texMatrix);
    }

    /**
     * draw specific texture with specific texture matrix
     *
     * @param textureId texture ID
     * @param texMatrix texture matrix if this is null, the last one use(we don't check size of this array and needs at least 16 of float)
     */
    public void draw(final int textureId, final float[] mvpMatrix, final float[] texMatrix) {
        // Select the program.
        GLES20.glUseProgram(hProgram);

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);


        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, mCoordsPerVertex,
                GLES20.GL_FLOAT, false, mVertexStride, FULL_RECTANGLE_BUF);

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, mCoordsPerVertex,
                GLES20.GL_FLOAT, false, mTexCoordStride, FULL_RECTANGLE_TEX_BUF);

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mVertexCount);

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(mTextureTarget, 0);
    }

}
