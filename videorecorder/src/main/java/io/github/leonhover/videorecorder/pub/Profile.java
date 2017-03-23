package io.github.leonhover.videorecorder.pub;

import android.media.AudioFormat;

/**
 * Created by wangzongliang on 17-3-23.
 */

public class Profile {

    public int videoBitRate;
    public int videoFrameRate;
    public int videoWidth;
    public int videoHeight;

    public int audioBitRate;
    public int audioSamplingRate;
    public int audioChannels;

    public int rotation;

    public static class Builder {

        private int videoBitRate = 1024000;
        private int videoFrameRate = 15;
        private int videoWidth = 640;
        private int videoHeight = 480;

        private int audioBitRate = 128000;
        private int audioSamplingRate = 44100;
        private int audioChannels = 2;

        private int rotation = 0;

        public Builder setVideoBitRate(int bitRate) {
            this.videoBitRate = bitRate;
            return this;
        }

        public Builder setVideoWidth(int videoWidth) {
            this.videoWidth = videoWidth;
            return this;
        }

        public Builder setVideoHeight(int videoHeight) {
            this.videoHeight = videoHeight;
            return this;
        }

        public Builder setAudioBitRate(int audioBitRate) {
            this.audioBitRate = audioBitRate;
            return this;
        }

        public Builder setAudioSamplingRate(int audioSamplingRate) {
            this.audioSamplingRate = audioSamplingRate;
            return this;
        }

        public Builder setAudioChannels(int audioChannels) {
            this.audioChannels = audioChannels;
            return this;
        }

        public Builder setVideoFrameRate(int videoFrameRate) {
            this.videoFrameRate = videoFrameRate;
            return this;
        }

        public Builder setRotation(int rotation) {
            this.rotation = rotation;
            return this;
        }

        public Profile build() {
            Profile profile = new Profile();
            profile.audioBitRate = this.audioBitRate;
            profile.audioChannels = this.audioChannels;
            profile.audioSamplingRate = this.audioSamplingRate;

            profile.videoBitRate = this.videoBitRate;
            profile.videoFrameRate = this.videoFrameRate;
            profile.videoWidth = this.videoWidth;
            profile.videoHeight = this.videoHeight;

            profile.rotation = this.rotation;
            return profile;
        }
    }

}
