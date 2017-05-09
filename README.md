## VideoRecorder
一个简单的视频录制示例程序，可选择两种录制方式：
* AndroidRecorder
Android原生的视频录制功能——MediaRecorder，封装了基本的参数设置。

* MediaCodecRecorder
顾名思义采用了MediaCodec的编码方式，也就是常说的硬件编码。


### 开发中遇到的问题
* MediaCodec的音频参数配置错误

```
Failed to set AAC encoder parameters
05-09 17:34:36.411 21127-21271/io.github.leonhover.videorecorder.samples E/ACodec: [OMX.google.aac.encoder] configureCodec returning error -2147483648
05-09 17:34:36.411 21127-21271/io.github.leonhover.videorecorder.samples E/MediaCodec: Codec reported an error. (omx error 0x80001001, internalError -2147483648)
05-09 17:34:36.421 21127-21270/io.github.leonhover.videorecorder.samples E/AndroidRuntime: FATAL EXCEPTION: audio_encoding_thread
                                                                                           Process: io.github.leonhover.videorecorder.samples, PID: 21127
                                                                                           java.lang.IllegalStateException
                                                                                               at android.media.MediaCodec.native_configure(Native Method)
                                                                                               at android.media.MediaCodec.configure(MediaCodec.java:262)
                                                                                               at io.github.leonhover.videorecorder.recorder.mediacodec.encode.AudioEncoder.handlePrepare(AudioEncoder.java:198)
                                                                                               at io.github.leonhover.videorecorder.recorder.mediacodec.encode.AudioEncoder.handleMessage(AudioEncoder.java:156)
                                                                                               at android.os.Handler.dispatchMessage(Handler.java:98)
                                                                                               at android.os.Looper.loop(Looper.java:136)
                                                                                               at android.os.HandlerThread.run(HandlerThread.java:61)
```
这个问题是由于设置的Audio的SampleRate不支持造成，改为了常见的44100解决此问题。

* 预览画面有放大的现象

```
//官网说专门给录制提供的方法，提高录制的效率，但是会发生画面变形的问题
Camera.Parameters.setRecordingHint(boolean hint);
```

* IllegalStateException

MediaCodec的IllegalStateException,我遇到的情况基本就是由于启动和停止时候发生错误。

例如：
1、start后立即调用signalEndOfStream，在getBuffer或者dequeueOutputBuffer时候会抛出IllegalStateException；
2、MediaCodec在参数配置后prepare的时，日志就显示出现了错误——`storeMetaDatainBuffer fails`。我理解的也是上一个MediaCodec状态没有管理好造成的。如果正确处理了MediaCodec状态控制，这个问题就没见过了。

* MediaMuxer的错误

视频通常是带有视频与音频的，如果只调用了MediaMuxer.addTrack()一次，只添加了AudioFormat或VideoFormat，接着start()就会出错。


## 感谢

感谢[bigflake](http://www.bigflake.com/mediacodec/)上面提供的MediaCodec的使用示例。
