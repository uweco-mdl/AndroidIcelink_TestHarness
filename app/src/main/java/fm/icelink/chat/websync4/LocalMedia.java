package fm.icelink.chat.websync4;

import android.content.Context;
import android.graphics.Camera;
import android.media.MediaCodecInfo;
import android.view.View;

import fm.icelink.*;
import fm.icelink.android.*;

public class LocalMedia extends fm.icelink.RtcLocalMedia<View> {

    private boolean enableSoftwareH264;
    private Context context;
    private CameraPreview viewSink;;

    private VideoConfig videoConfig = new VideoConfig(640, 480, 30);

    @Override
    protected AudioSink createAudioRecorder(AudioFormat audioFormat) {
        android.util.Log.w("--ICELINK LIB--","\n------- About to CREATE AUDIO RECORDER [LocalMedia.java]...");
        return new fm.icelink.matroska.AudioSink(getId() + "-local-audio-" + audioFormat.getName().toLowerCase() + ".mkv");
    }

    @Override
    protected VideoSink createVideoRecorder(VideoFormat videoFormat) {
        android.util.Log.w("--ICELINK LIB--","\n------- About to CREATE VIDEO RECORDER [LocalMedia.java]...");
        return new fm.icelink.matroska.VideoSink(getId() + "-local-video-" + videoFormat.getName().toLowerCase() + ".mkv");
    }

    @Override
    protected VideoPipe createImageScaler() {
        return new fm.icelink.yuv.ImageScaler(1.0);
    }

    @Override
    protected VideoPipe createImageConverter(VideoFormat videoFormat) {
        return new fm.icelink.yuv.ImageConverter(videoFormat);
    }

    @Override
    protected AudioSource createAudioSource(AudioConfig audioConfig) {
        android.util.Log.w("--ICELINK LIB--","\n------- About to CREATE AUDIO RECORD SOURCE [LocalMedia.java]...");
        return new AudioRecordSource(context, audioConfig);
    }

    @Override
    protected ViewSink<View> createViewSink() {
        return null;
    }

    @Override
    protected AudioEncoder createOpusEncoder(AudioConfig audioConfig) {
        return new fm.icelink.opus.Encoder(audioConfig);
    }

    @Override
    protected VideoEncoder createH264Encoder() {
        if (enableSoftwareH264) {
            return new fm.icelink.openh264.Encoder();
        } else {
            return null;
        }
    }

    @Override
    protected VideoEncoder createVp8Encoder() {
        return new fm.icelink.vp8.Encoder();
    }

    @Override
    protected VideoEncoder createVp9Encoder() {
        return null;//new fm.icelink.vp9.Encoder();
    }

    @Override
    protected VideoSource createVideoSource() {
        return new CameraSource(viewSink, videoConfig);
    }

    public LocalMedia(Context context, boolean enableSoftwareH264, boolean disableAudio, boolean disableVideo, AecContext aecContext) {
        super(disableAudio, disableVideo, aecContext);
        this.enableSoftwareH264 = enableSoftwareH264;
        this.context = context;
        viewSink = new CameraPreview(context, LayoutScale.Contain);

        super.initialize();
    }

    public View getView()
    {
        return viewSink.getView();
    }
}
