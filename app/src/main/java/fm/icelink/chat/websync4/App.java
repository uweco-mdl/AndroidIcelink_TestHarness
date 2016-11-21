package fm.icelink.chat.websync4;

import android.content.*;
import android.view.*;

import java.lang.Exception;
import java.util.HashMap;

import fm.icelink.*;
import fm.icelink.android.*;
import fm.icelink.android.LayoutManager;
import fm.icelink.websync4.*;
import fm.websync.*;
import layout.TextChatFragment;
import layout.VideoChatFragment;

public class App {
    private OnReceivedTextListener textListener;

    private String sessionId;
    public String getSessionId() {
        return this.sessionId;
    }
    public void setSessionId(String sid) {
        this.sessionId = sid;
    }

    private String name;
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    private boolean enableAudioSend;
    public boolean getEnableAudioSend() {
        return this.enableAudioSend;
    }
    public void setEnableAudioSend(boolean enable) {
        this.enableAudioSend = enable;
    }

    private boolean enableAudioReceive;
    public boolean getEnableAudioReceive() {
        return this.enableAudioReceive;
    }
    public void setEnableAudioReceive(boolean enable) {
        this.enableAudioReceive = enable;
    }

    private boolean enableVideoSend;
    public boolean getEnableVideoSend() {
        return this.enableVideoSend;
    }
    public void setEnableVideoSend(boolean enable) {
        this.enableVideoSend = enable;
    }

    private boolean enableVideoReceive;
    public boolean getEnableVideoReceive() {
        return this.enableVideoReceive;
    }
    public void setEnableVideoReceive(boolean enable) {
        this.enableVideoReceive = enable;
    }

    private boolean enableDataChannel;
    public boolean getEnableDataChannel() {
        return this.enableDataChannel;
    }
    public void setEnableDataChannel(boolean enable) {
        this.enableDataChannel = enable;
    }

    public static final String default_WebSyncURL = "https://v4.websync.fm/websync.ashx";
    public static final String default_STUNserver = "stun:turn.icelink.fm:3478";
    public static final String default_TURNserver = "turn:turn.icelink.fm:443?transport=udp";

    public static final String default_TURNuser = "test";
    public static final String default_TURNpassword = "pa55w0rd!";

    public static String SessionID = null;
    public static String STUN_server = default_STUNserver,
                         WebSyncURL = default_WebSyncURL;

    public static String TURN_server = default_TURNserver;
    public static String TURN_user = default_TURNuser;
    public static String TURN_passwd = default_TURNpassword;

    public static String ProviderName = "";     // this will be set by the caller
    public static final String default_username = "DummyUser";

    private IceServer[] iceServers;

    private HashMap<View, RemoteMedia> mediaTable;
    private DataChannelCollection dataChannels;

    private String websyncServerUrl = WebSyncURL ;     //  "https://v4.websync.fm/websync.ashx"; // WebSync On-Demand

    private fm.websync.Client client = null;
    private LocalMedia localMedia = null;
    private LayoutManager layoutManager = null;

    private static Object channelLock = new Object();

    private fm.icelink.chat.websync4.AecContext aecContext;
    private boolean enableH264 = false;

    private Context context = null;

    private App(Context context) {
        this.context = context.getApplicationContext();

        mediaTable = new HashMap<>();
        dataChannels = new DataChannelCollection();

        enableAudioSend = true;
        enableAudioReceive = true;
        enableVideoSend = true;
        enableVideoReceive = true;

        enableDataChannel = true;

        // Log to the console.
        fm.icelink.Log.setProvider(new fm.icelink.android.LogProvider(LogLevel.Debug));
    }

    private static App app;

    public static synchronized App getInstance(Context context) {
        if (app == null) {
            app = new App(context);
        }
        return app;
    }

    public Future<fm.icelink.LocalMedia> startLocalMedia(final VideoChatFragment fragment) {
        return downloadOpenH264().then(new IFunction1<Object, Future<fm.icelink.LocalMedia>>() {
            public Future<fm.icelink.LocalMedia> invoke(Object o) {
                // Set up the local media.
                aecContext = new AecContext();

                localMedia = new LocalMedia(context, enableH264, !enableAudioSend, !enableVideoSend, aecContext);

                View localView = localMedia.getView();
                // Set up the layout manager.

                layoutManager = new LayoutManager(fragment.container);
                layoutManager.setLocalView(localView);

                fragment.registerForContextMenu(localView);
                localView.setOnTouchListener(fragment);

                // Start the local media.
                return localMedia.start();
            }
        });
    }

    private Future<Object> downloadOpenH264() {
        return Promise.wrapPromise(new IFunction0<Future<Object>>() {
            public Future<Object> invoke() {
                if (Platform.getInstance().getArchitecture() == Architecture.Arm32) {
                    final String libraryPath = context.getFilesDir() + "/libopenh264.so";
                    if (!new FileStream(libraryPath).exists()) {
                        String downloadUrl = fm.icelink.openh264.Utility.getDownloadUrl();
                        Log.warn(String.format("OpenH264 library missing. Downloading now from: %s.", downloadUrl));
                        return HttpFileTransfer.downloadFile(downloadUrl, libraryPath).then(new IAction1<Object>() {
                            public void invoke(Object o) {
                                Log.info("OpenH264 library downloaded.");
                                System.load(libraryPath);
                                enableH264 = true;
                            }
                        });
                    }

                    System.load(libraryPath);
                    enableH264 = true;
                }
                return Promise.resolveNow(null);
            }
        });
    }

    public Future<fm.icelink.LocalMedia> stopLocalMedia() {
        return Promise.wrapPromise(new IFunction0<Future<fm.icelink.LocalMedia>>() {
            public Future<fm.icelink.LocalMedia> invoke() {
                if (localMedia == null) {
                    throw new RuntimeException("Local media has already been stopped.");
                }

                // Stop the local media.
                return localMedia.stop().then(new IAction1<fm.icelink.LocalMedia>() {
                    public void invoke(fm.icelink.LocalMedia o) {
                        // Tear down the layout manager.
                        if (layoutManager != null) {
                            layoutManager.removeRemoteViews();
                            layoutManager.unsetLocalView();
                            layoutManager = null;
                        }

                        // Tear down the local media.
                        if (localMedia != null) {
                            localMedia.destroy();
                            localMedia = null;
                        }
                    }
                });
            }
        });
    }

    public fm.icelink.Future<fm.icelink.LocalMedia> joinAsync(final VideoChatFragment fragment, final TextChatFragment textChat) {
        final fm.icelink.Promise<fm.icelink.LocalMedia> promise = new fm.icelink.Promise<>();

        // trace the current parameter settings ...
        setSessionId(SessionID);
        websyncServerUrl = WebSyncURL;
        android.util.Log.w("--ICELINK LIB--","\n------- SessionID = ["+SessionID+"]");
        android.util.Log.w("--ICELINK LIB--","\n------- STUN_server = ["+STUN_server+"]");
        android.util.Log.w("--ICELINK LIB--","\n------- TURN_server = ["+TURN_server+"]");
        android.util.Log.w("--ICELINK LIB--","\n------- WebSyncURL = ["+WebSyncURL+"]");
        android.util.Log.w("--ICELINK LIB--","\n------- TURN_user = ["+TURN_user+"]");
        android.util.Log.w("--ICELINK LIB--","\n------- TURN_passwd = ["+TURN_passwd+"]");

        iceServers = new IceServer[]
        {
            new IceServer(STUN_server),
            new IceServer(TURN_server, TURN_user, TURN_passwd),
        };

        try {
            textListener = textChat;

            // Create the signalling client and connect.
            client = new fm.websync.Client(websyncServerUrl);
            client.connect(new ConnectArgs() {{
                setOnSuccess(new fm.SingleAction<ConnectSuccessArgs>() {
                    public void invoke(ConnectSuccessArgs e) {
                        try {
                            android.util.Log.w("--ICELINK LIB--","\n------- About to Bind Client, new record [App.java]...");
                            client.bind(new BindArgs(new Record("name", Serializer.serializeString(name))));
                            // Join the signalling channel.
                            ClientExtensions.joinConference(client, new JoinConferenceArgs("/" + getSessionId()) {{
                                setOnRemoteClient(new IFunction1<PeerClient, Connection>() {
                                    public Connection invoke(final PeerClient remoteClient) {
                                        // Create connection to remote client.
                                        final RemoteMedia remoteMedia = new RemoteMedia(context, enableH264, !enableAudioReceive, !enableVideoReceive, aecContext);
                                        final AudioStream audioStream = new AudioStream(enableAudioSend ? localMedia.getAudioTrack().getOutputs() : null, enableAudioReceive ? remoteMedia.getAudioTrack().getInputs() : null);
                                        final VideoStream videoStream = new VideoStream(enableVideoSend ? localMedia.getVideoTrack().getOutputs() : null, enableVideoReceive ? remoteMedia.getVideoTrack().getInputs() : null);

                                        final Connection connection;

                                        // Add the remote view to the layout.
                                        layoutManager.addRemoteView(remoteMedia.getId(), remoteMedia.getView());

                                        mediaTable.put(remoteMedia.getView(), remoteMedia);
                                        fragment.registerForContextMenu(remoteMedia.getView());
                                        remoteMedia.getView().setOnTouchListener(fragment);

                                        if (enableDataChannel) {
                                            DataChannel channel = new DataChannel("mydatachannel") {{
                                                setOnReceive(new IAction1<DataChannelReceiveArgs>() {
                                                    @Override
                                                    public void invoke(DataChannelReceiveArgs dataChannelReceiveArgs) {
                                                        android.util.Log.w("--ICELINK LIB--","\n------- About to get 'name' bounds records 1 [App.java]...");
                                                        /*
                                                        String name = remoteClient.getBoundRecords().get("name").getValueJson();
                                                        name = name.substring(1, name.length() - 1);
                                                        */
                                                        textListener.onReceivedText(ProviderName, dataChannelReceiveArgs.getDataString());
                                                    }
                                                });

                                                addOnStateChange(new IAction1<DataChannel>() {
                                                    @Override
                                                    public void invoke(DataChannel dataChannel) {
                                                        android.util.Log.w("--ICELINK LIB--","\n------- About to get 'name' bounds records 2 [App.java]...");
                                                        /*
                                                        String name = remoteClient.getBoundRecords().get("name").getValueJson();
                                                        name = name.substring(1, name.length() - 1);
                                                        */
                                                        if (dataChannel.getState() == DataChannelState.Connected) {
                                                            synchronized (channelLock)
                                                            {
                                                                dataChannels.add(dataChannel);
                                                            }
                                                            textListener.onPeerJoined(ProviderName);
                                                        } else if (dataChannel.getState() == DataChannelState.Closed || dataChannel.getState() == DataChannelState.Failed) {
                                                            synchronized (channelLock)
                                                            {
                                                                dataChannels.remove(dataChannel);
                                                            }

                                                            textListener.onPeerLeft(ProviderName);
                                                        }
                                                    }
                                                });
                                            }};

                                            DataStream dataStream = new DataStream(channel);
                                            connection = new Connection(new Stream[]{audioStream, videoStream, dataStream});
                                        } else {
                                            connection = new Connection(new Stream[]{audioStream, videoStream});
                                        }

                                        connection.setIceServers(iceServers);

                                        connection.addOnStateChange(new fm.icelink.IAction1<Connection>() {
                                            public void invoke(Connection c) {
                                                // Remove the remote view from the layout.
                                                if (c.getState() == ConnectionState.Closing ||
                                                        c.getState() == ConnectionState.Failing) {
                                                    if (layoutManager.getRemoteView(remoteMedia.getId()) != null) {
                                                        layoutManager.removeRemoteView(remoteMedia.getId());
                                                        remoteMedia.destroy();
                                                    }
                                                }
                                            }
                                        });

                                        return connection;
                                    }
                                });
                                setOnFailure(new IAction1<JoinConferenceFailureArgs>() {
                                    public void invoke(JoinConferenceFailureArgs e) {
                                        promise.reject(e.getException());
                                    }
                                });
                                setOnSuccess(new IAction1<JoinConferenceSuccessArgs>() {
                                    public void invoke(JoinConferenceSuccessArgs e) {
                                        promise.resolve(null);
                                    }
                                });
                            }});
                        } catch (Exception ex) {
                            promise.reject(ex);
                        }
                    }
                });
                setOnFailure(new fm.SingleAction<ConnectFailureArgs>() {
                    public void invoke(ConnectFailureArgs e) {
                        promise.reject(e.getException());
                        e.setRetry(false);
                    }
                });
            }});
        } catch (Exception e) {
            promise.reject(e);
        }
        return promise;
    }

    public fm.icelink.Future<Object> leaveAsync() {
        final fm.icelink.Promise<Object> promise = new fm.icelink.Promise<>();
        try {
            // Leave the signalling channel.
            ClientExtensions.leaveConference(client, new LeaveConferenceArgs("/" + getSessionId()) {{
                setOnSuccess(new IAction1<LeaveConferenceSuccessArgs>() {
                    public void invoke(LeaveConferenceSuccessArgs leaveConferenceSuccessArgs) {
                        try {
                            // Disconnect the signalling client.
                            client.disconnect(new DisconnectArgs() {{
                                setOnComplete(new fm.SingleAction<DisconnectCompleteArgs>() {
                                    public void invoke(DisconnectCompleteArgs args) {
                                        promise.resolve(null);
                                    }
                                });
                            }});
                        } catch (Exception e) {
                            promise.reject(e);
                        }
                    }
                });
                setOnFailure(new IAction1<LeaveConferenceFailureArgs>() {
                    public void invoke(LeaveConferenceFailureArgs e) {
                        promise.reject(e.getException());
                    }
                });
            }});
        } catch (Exception e) {
            promise.reject(e);
        }
        return promise;
    }

    private boolean usingFrontVideoDevice = true;

    public void useNextVideoDevice() {
        if (localMedia != null) {
            VideoTrack videoTrack = localMedia.getVideoTrack();
            if (videoTrack != null) {
                CameraSource cameraSource = (CameraSource) videoTrack.getSource();
                if (cameraSource != null) {
                    usingFrontVideoDevice = !usingFrontVideoDevice;
                    cameraSource.changeInput(usingFrontVideoDevice ? cameraSource.getFrontInput() : cameraSource.getBackInput());
                }
            }
        }
    }

    public Future<Object> pauseLocalVideo() {
        if (localMedia != null) {
            VideoTrack videoTrack = localMedia.getVideoTrack();
            if (videoTrack != null) {
                VideoSource videoSource = videoTrack.getSource();
                if (videoSource != null) {
                    if (videoSource.getState() == MediaSourceState.Started) {
                        return videoSource.stop();
                    }
                }
            }
        }
        return Promise.resolveNow();
    }

    public Future<Object> resumeLocalVideo() {
        if (localMedia != null) {
            VideoTrack videoTrack = localMedia.getVideoTrack();
            if (videoTrack != null) {
                VideoSource videoSource = videoTrack.getSource();
                if (videoSource != null) {
                    if (videoSource.getState() == MediaSourceState.Stopped) {
                        return videoSource.start();
                    }
                }
            }
        }
        return Promise.resolveNow();
    }

    public void setIsRecordingAudio(View v, boolean record)
    {
        if (localMedia.getView() == v) {
            if (localMedia.getIsRecordingAudio() != record) {
                android.util.Log.w("--ICELINK LIB--","\n------- SET - About to toggle LOCAL Audio Recording [App.java]...");
                localMedia.toggleAudioRecording();
            }
        } else {
            RemoteMedia remote = mediaTable.get(v);
            if (remote.getIsRecordingAudio() != record) {
                android.util.Log.w("--ICELINK LIB--","\n------- About to toggle REMOTE Audio Recording [App.java]...");
                remote.toggleAudioRecording();
            }
        }
    }

    public boolean getIsRecordingAudio(View v)
    {
        if (localMedia.getView() == v) {
            android.util.Log.w("--ICELINK LIB--","\n------- GET - About to toggle LOCAL Audio Recording [App.java]...");
            return localMedia.getIsRecordingAudio();
        } else {
            return mediaTable.get(v).getIsRecordingAudio();
        }
    }

    public void setIsRecordingVideo(View v, boolean record)
    {
        if (localMedia.getView() == v) {
            if (localMedia.getIsRecordingVideo() != record) {
                android.util.Log.w("--ICELINK LIB--","\n------- GET - About to toggle LOCAL Video Recording [App.java]...");
                localMedia.toggleVideoRecording();
            }
        } else {
            RemoteMedia remote = mediaTable.get(v);
            if (remote.getIsRecordingVideo() != record) {
                remote.toggleVideoRecording();
            }
        }
    }

    public boolean getIsRecordingVideo(View v)
    {
        if (localMedia.getView() == v) {
            android.util.Log.w("--ICELINK LIB--","\n------- GET - About to get LOCAL Video Recording [App.java]...");
            return localMedia.getIsRecordingVideo();
        } else {
            return mediaTable.get(v).getIsRecordingVideo();
        }
    }

    public void setAudioMuted(View v, boolean mute)
    {
        if (localMedia.getView() == v) {
            localMedia.setAudioMuted(mute);
        } else {
            mediaTable.get(v).setAudioMuted(mute);
        }
    }

    public boolean getAudioMuted(View v)
    {
        if (localMedia.getView() == v) {
            return localMedia.getAudioMuted();
        } else {
            return mediaTable.get(v).getAudioMuted();
        }
    }

    public void setVideoMuted(View v, boolean mute)
    {
        if (localMedia.getView() == v) {
            localMedia.setVideoMuted(mute);
        } else {
            mediaTable.get(v).setVideoMuted(mute);
        }
    }

    public boolean getVideoMuted(View v)
    {
        if (localMedia.getView() == v) {
            return localMedia.getVideoMuted();
        } else {
            return mediaTable.get(v).getVideoMuted();
        }
    }

    public void writeLine(String message)
    {
        synchronized (channelLock)
        {
            for (DataChannel channel : dataChannels.getValues())
            {
                if (channel.getState() == DataChannelState.Connected)
                {
                    channel.sendDataString(message);
                }
            }
        }
    }

    public interface OnReceivedTextListener {
        void onReceivedText(String name, String message);
        void onPeerJoined(String name);
        void onPeerLeft(String name);
    }
}
