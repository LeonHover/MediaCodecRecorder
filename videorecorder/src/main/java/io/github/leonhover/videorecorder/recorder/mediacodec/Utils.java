package io.github.leonhover.videorecorder.recorder.mediacodec;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

/**
 * Created by wangzongliang on 2017/4/20.
 */

public class Utils {

    private static final String TAG = "Utils";

    /**
     * 根据MimeType选择合适的编码器
     *
     * @param mimeType
     * @return MediaCodecInfo
     */
    public static MediaCodecInfo chooseSuitableMediaCodec(String mimeType) {
        MediaCodecInfo ret = null;
        int numCodecs = MediaCodecList.getCodecCount();
        LOOP:
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }

            String[] supportedTypes = info.getSupportedTypes();
            for (int j = 0; j < supportedTypes.length; j++) {
                if (supportedTypes[j].equalsIgnoreCase(mimeType)) {
                    ret = info;
                    break LOOP;
                }
            }
        }
        return ret;
    }

}
