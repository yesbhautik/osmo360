package com.osmo.preview;

import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.ParcelUuid;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "OsmoPreview";
    private static final String EXTRA_LOCAL_PORT = "local_port";
    private static final String PREFS_NAME = "osmo_camera_config";
    private static final String PREF_CAMERA_SSID = "camera_ssid";
    private static final String PREF_CAMERA_BLE_NAME = "camera_ble_name";
    private static final String PREF_CAMERA_BSSID = "camera_bssid";
    private static final String PREF_CAMERA_PASSPHRASE = "camera_passphrase";
    private static final String PREF_CAMERA_HOST = "camera_host";
    private static final String PREF_USER_CONFIRMED = "user_confirmed";
    private static final String DEFAULT_CAMERA_HOST = "192.168.2.1";
    private static final int REQUEST_ENABLE_BLUETOOTH = 9;
    private static final int CAMERA_PORT = 9004;
    private static final int CAMERA_TCP_CONTROL_PORT = 7001;
    private static final int PREFERRED_REPLAY_PORT = 58350;
    private static final int PREFERRED_92EC_PORT = 58382;
    private static final int WIFI_REQUEST_TIMEOUT_MS = 60000;
    private static final int PAIRING_APPROVAL_WAIT_MS = 45000;
    // Mimo uses an ephemeral local UDP port for each preview session. Try the
    // ports observed so far before falling back to an OS-assigned port.
    private static final int[] FALLBACK_LOCAL_PORTS = { 58350, 58382, 55483, 33405, 43130, 54775, 0 };
    private static final int MIMO_UID = 10431;
    private static final int UDP_RECEIVE_BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int DJI_MEDIA_STRIP = 36;
    private static final int DJI_92EC_MEDIA_STRIP = 20;
    private static final int DJI_MEDIA_MIN_LENGTH = 512;
    private static final boolean ENABLE_SETUP_REPLAY = false;
    private static final byte[] DEFAULT_SPS_NAL = hex("0000000167640028acb40100040d3501040106d0a135");
    private static final byte[] DEFAULT_PPS_NAL = hex("0000000168ee06f2c0");
    private static final byte[] MIMO_LIVEVIEW_TRANSMIT_CTRL_PAYLOAD = hex("00040200000000000000");
    private static final byte[] UDP_92EC_PRE_BOOTSTRAP_HANDSHAKE = hex(
            "308092ec000000ce906464006400c005140000640000019001c005140000640014006400c00514000064000101040102");
    private static final UdpDumlCommand[] UDP_LIVEVIEW_START_COMMANDS = new UdpDumlCommand[] {
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011400")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011400")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010900")),
            new UdpDumlCommand(0x01, 0x02, 0xff,
                    hex("40150000000000000000000000000000000000000000000000000000000000000000")),
            new UdpDumlCommand(0x01, 0x00, 0x4f, hex("040000000000000000"))
    };
    private static final int UDP_92EC_PROPERTY_REQUEST_ID_START = 0xb7cb;
    private static final int UDP_92EC_MAINTENANCE_PROPERTY_REQUEST_ID_START = 0xb810;
    private static final UdpDumlCommand[] UDP_92EC_MAINTENANCE_MODE_COMMANDS = new UdpDumlCommand[] {
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010a00")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00014100")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011800")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011400")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011400")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010900")),
            new UdpDumlCommand(0x01, 0x02, 0xff,
                    hex("40150000000000000000000000000000000000000000000000000000000000000000")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010800")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011500")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010800")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00012000")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00012900")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00012900")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010600")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010f00")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00014100")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00012800"))
    };
    private static final UdpDumlCommand[] UDP_92EC_PREVIEW_SESSION_COMMANDS = new UdpDumlCommand[] {
            new UdpDumlCommand(0x28, 0x00, 0x88, hex("1700002300415050000000000002")),
            new UdpDumlCommand(0x03, 0x03, 0xda, hex("05ffffffff")),
            new UdpDumlCommand(0x88, 0x00, 0x74,
                    hex("2100000100000000060000000000000005000000000000001857b86f0100000005000000000000001857b86f010000008d7f8640000c0000300100000000000040296e3601000000f900dca7519d17ecb856b86f01000000982a6e36010000000057b86f0100000000ee190201000000e0726a360100000038bd00090100000024edb70201000000ff000000000000000000000000000000f900dca7519d17ec705ab86f01000000985ab86f01000000705ab86f010000006857b86f010000003057b86f01000000"))
    };
    private static final UdpDumlCommand[] UDP_92EC_STARTUP_SETUP_COMMANDS = new UdpDumlCommand[] {
            new UdpDumlCommand(0x48, 0x00, 0x01,
                    hex("0000000000000000000000000000000000000000000000000000000000")),
            new UdpDumlCommand(0x28, 0x00, 0x6a,
                    hex("01003b280a6a000000004a010c417369612f4b6f6c6b617461")),
            new UdpDumlCommand(0x01, 0x00, 0x26,
                    hex("4a002a10020000000000010000402d000d0100ffffffffffffffff0001000000")),
            new UdpDumlCommand(0x01, 0x02, 0xff,
                    hex("40150000000000000000000000000000000000000000000000000000000000000000")),
            new UdpDumlCommand(0x01, 0x00, 0x26, hex("4a040e1002000000000001000000")),
            new UdpDumlCommand(0x41, 0x09, 0xa8, MIMO_LIVEVIEW_TRANSMIT_CTRL_PAYLOAD),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011400")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00011500")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010800")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00012000")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00012900")),
            new UdpDumlCommand(0x01, 0x02, 0x8e, hex("00010600"))
    };
    private static final UdpDumlCommand[] UDP_92EC_POST_VIDEO_HEARTBEAT_COMMANDS = new UdpDumlCommand[] {
            new UdpDumlCommand(0x28, 0x00, 0x88, hex("1700002300415050000000000002")),
            new UdpDumlCommand(0x48, 0x08, 0x10,
                    hex("00415050000000000000000000000000000000000000000000000000000000000000020000000000000208000000000000000000000000000000000000000000"))
    };
    private static final String[] UDP_92EC_BOOTSTRAP_PROPERTY_NAMES = {
            "camcap_mode_profile",
            "camcap_video_format",
            "camcap_fov",
            "camcap_iso",
            "camcap_photo_storage_format",
            "camcap_color_mode",
            "camcap_wb",
            "camcap_photo_size",
            "camcap_video_codec",
            "camcap_shutter",
            "camcap_photo_timer_interval",
            "camcap_exposure_mode",
            "camcap_zoom",
            "camcap_antiflicker",
            "camcap_sharpness",
            "camcap_denoise",
            "camcap_aperture",
            "camcap_shutter_max",
            "camcap_eis",
            "camcap_iso_auto_max",
            "camcap_loop_video_duration",
            "camcap_hyperlapse_ratio",
            "camcap_slowmotion_ratio",
            "camcap_timelapse_duration",
            "camcap_countdown",
            "camcap_photo_time_limited_burst_param",
            "camcap_pano_mode_type",
            "camcap_custom_mode",
            "camcap_events",
            "camcap_style_filter_density",
            "camcap_style_filter_mode",
            "cam_storage",
            "cam_status",
            "cam_record_time",
            "cam_expo_param",
            "shutter_param",
            "cam_photo_param_new",
            "cam_lapse_param",
            "cam_video_param_v2",
            "cam_image_effect",
            "v_quality_enhance_status",
            "cam_fov",
            "cam_lens_state",
            "cam_audio_status_v2",
            "audio_timecode_status",
            "temp_curve",
            "camcap_common",
            "cam_imu_calib_info",
            "timecode_info",
            "cam_custom_mode_params",
            "cam_super_slowmotion_status",
            "cam_pano_mode_type",
            "cam_style_filter_status"
    };
    private static final UUID BLE_SERVICE_FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_FFF3 = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_FFF4 = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_FFF5 = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_FFF7 = UUID.fromString("0000fff7-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int BLE_APP_ADDRESS = 0x02;
    private static final int H264_DIAGNOSTIC_DUMP_LIMIT = 0;
    private static final int UDP_92EC_FULL_MAINTENANCE_INTERVAL_ROUNDS = 10;
    private static final int UDP_92EC_LIGHT_SUSTAIN_INTERVAL_ROUNDS = 10;
    private static final long MEDIA_SOFT_RESUME_GRACE_MS = 7000;
    private static final long MEDIA_SOFT_RESUME_COOLDOWN_MS = 30000;
    private static final long MEDIA_REARM_GRACE_MS = 45000;
    private static final long MEDIA_REARM_COOLDOWN_MS = 120000;
    private static final long MEDIA_SESSION_RESTART_GRACE_MS = 120000;
    private static final long MEDIA_SESSION_RESTART_COOLDOWN_MS = 180000;
    private static final long BLE_SCAN_TIMEOUT_MS = 30000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object decoderLock = new Object();
    private final ByteArrayOutputStream bleReceiveBuffer = new ByteArrayOutputStream(4096);

    private SurfaceView surfaceView;
    private TextView statusView;
    private Button startButton;
    private Button settingsButton;
    private Button saveConfigButton;
    private Button resetConfigButton;
    private Button closeConfigButton;
    private LinearLayout rootLayout;
    private LinearLayout mainLayout;
    private ScrollView settingsScroll;
    private EditText ssidInput;
    private EditText bleNameInput;
    private EditText bssidInput;
    private EditText passphraseInput;
    private EditText hostInput;
    private CheckBox confirmInput;
    private SharedPreferences configPrefs;
    private Surface surface;
    private MediaCodec decoder;
    private Network boundNetwork;
    private ConnectivityManager.NetworkCallback cameraNetworkCallback;
    private int passphraseCandidateIndex;
    private boolean requestCameraWifiWithBssid = true;
    private boolean requestCameraWifiWithWpa3;
    private int wifiAttemptToken;
    private String cameraAdvertisedPassphrase;
    private byte[] spsNal;
    private byte[] ppsNal;
    private long queuedNalUnits;
    private long renderedFrames;
    private int nalLogSamples;
    private int decoderLogSamples;
    private long decoderPresentationTimeUs;
    private int h264DiagnosticDumpBytes;
    private final ByteArrayOutputStream pendingAccessUnit = new ByteArrayOutputStream(256 * 1024);
    private boolean pendingAccessUnitHasVcl;
    private boolean pendingAccessUnitHasIdr;
    private int forcedLocalPort = -1;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic bleFff3;
    private BluetoothGattCharacteristic bleFff4;
    private BluetoothGattCharacteristic bleFff5;
    private BluetoothGattCharacteristic bleFff7;
    private BluetoothDevice pendingBleDevice;
    private int bleConnectAttempts;
    private boolean blePreflightInProgress;
    private boolean wifiRequestedAfterBle;
    private int bleSequence = 0x9500;
    private byte[][] pendingBleFrames;
    private int[][] pendingBleCommands;
    private int pendingBleFrameIndex;
    private int waitingBleCommandSet = -1;
    private int waitingBleCommandId = -1;
    private int waitingBleCommandToken;
    private int pairingApprovalToken;
    private int blePreflightToken;
    private boolean waitingForPairingApproval;
    private boolean blePairingApproved;
    private boolean blePreflightCompleted;
    private boolean bleWifiCredentialReceived;
    private int udpTransportCounter = 0xa698;
    private int udpPreviousTransportCounter = 0xa690;
    private int udpControlSequence = 0x0174;
    private int udp92EcCounter = 0x6498;
    private int udp92EcPreviousCounter = 0x6490;
    private int udp92EcSequence = 0x0101;
    private int udpDumlSequence = 0xa487;
    private int udp92EcDumlSequence = 0x2b9f;
    private int udp92EcAckSequence = 0x1169;
    private int udp92EcSessionSequenceIndex;
    private int udp92EcLiveAckIndex;
    private boolean triedBleWakeForCurrentStart;
    private int lastUdp92EcCounter = 0x6490;
    private int previousUdp92EcCommandCounter = 0x6490;
    private volatile int latestCamera92EcStatusCounter = 0x6490;
    private volatile int latestCamera92EcMediaCounter = 0x6490;
    private volatile long lastVideoProgressAtMs;
    private volatile long lastSoftMediaResumeAtMs;
    private volatile long lastMediaRearmAtMs;
    private volatile long lastMediaSessionRestartAtMs;
    private volatile boolean mediaSessionRestartRequested;
    private volatile long latestVideo92EcPackets;
    private volatile long latestH264Bytes;
    private volatile long latestRenderedFrames;
    private int udp92EcCameraRequestLogSamples;
    private List<BluetoothGattCharacteristic> pendingNotifyCharacteristics = new ArrayList<>();
    private final Set<String> answeredBleRequests = new LinkedHashSet<>();
    private final Map<String, byte[]> liveBlePropertyRequests = new LinkedHashMap<>();
    private final ArrayDeque<BleWrite> pendingBleWrites = new ArrayDeque<>();
    private boolean bleWriteInFlight;
    private BleWrite activeBleWrite;
    private boolean pendingStartAfterBluetoothEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        configPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        updateForcedLocalPort();

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        statusView = new TextView(this);
        statusView.setText("Tap gear to configure your Osmo 360, then Start");
        statusView.setPadding(24, 16, 24, 16);
        startButton = new Button(this);
        startButton.setText("Start Osmo Preview");
        startButton.setOnClickListener(v -> {
            if (running.get()) {
                stopPreview();
            } else {
                startPreview();
            }
        });
        settingsButton = new Button(this);
        settingsButton.setText("⚙ Settings");
        settingsButton.setOnClickListener(v -> showSettings());
        saveConfigButton = new Button(this);
        saveConfigButton.setText("Save Camera Settings");
        saveConfigButton.setOnClickListener(v -> {
            if (saveAndValidateCameraConfig()) {
                postStatus("Camera settings saved");
                showMain();
            }
        });
        resetConfigButton = new Button(this);
        resetConfigButton.setText("Reset Settings");
        resetConfigButton.setOnClickListener(v -> resetCameraConfig());
        closeConfigButton = new Button(this);
        closeConfigButton.setText("Close");
        closeConfigButton.setOnClickListener(v -> showMain());
        ssidInput = newConfigInput("Wi-Fi SSID, e.g. Osmo360-XXXX", PREF_CAMERA_SSID, "");
        bleNameInput = newConfigInput("Optional BLE name, usually same as SSID", PREF_CAMERA_BLE_NAME, "");
        bssidInput = newConfigInput("Optional Wi-Fi BSSID/MAC", PREF_CAMERA_BSSID, "");
        passphraseInput = newConfigInput("Wi-Fi password from your camera", PREF_CAMERA_PASSPHRASE, "");
        passphraseInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        hostInput = newConfigInput("Camera IP address", PREF_CAMERA_HOST, DEFAULT_CAMERA_HOST);
        confirmInput = new CheckBox(this);
        confirmInput.setText("I own/operate this Osmo 360 and have permission to connect");
        confirmInput.setChecked(configPrefs.getBoolean(PREF_USER_CONFIRMED, false));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(statusView, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        controls.addView(settingsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        controls.addView(startButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(surfaceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        mainLayout.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("Osmo 360 Connection Settings");
        settingsTitle.setPadding(24, 24, 24, 16);
        LinearLayout settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        settingsContent.setPadding(24, 0, 24, 24);
        settingsContent.addView(settingsTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(ssidInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(bleNameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(bssidInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(passphraseInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(hostInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(confirmInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(saveConfigButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(resetConfigButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContent.addView(closeConfigButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsScroll = new ScrollView(this);
        settingsScroll.addView(settingsContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(mainLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(settingsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        settingsScroll.setVisibility(android.view.View.GONE);
        setContentView(rootLayout);

        ensureLocationPermission();
        ensureBluetoothPermissions();
        bindProcessToCameraWifi();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateForcedLocalPort();
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        closeBle();
        unregisterCameraNetworkCallback();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindProcessToCameraWifi();
        if (pendingStartAfterBluetoothEnable && isBluetoothEnabled()) {
            pendingStartAfterBluetoothEnable = false;
            postStatus("Bluetooth enabled; tap Start again");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ENABLE_BLUETOOTH) {
            return;
        }
        if (resultCode == Activity.RESULT_OK || isBluetoothEnabled()) {
            pendingStartAfterBluetoothEnable = false;
            postStatus("Bluetooth enabled; tap Start again");
        } else {
            pendingStartAfterBluetoothEnable = false;
            postStatus("Bluetooth is required to find/wake the Osmo camera");
        }
    }

    private void showSettings() {
        mainLayout.setVisibility(android.view.View.GONE);
        settingsScroll.setVisibility(android.view.View.VISIBLE);
        postStatus("Edit and save your Osmo 360 settings");
    }

    private void showMain() {
        settingsScroll.setVisibility(android.view.View.GONE);
        mainLayout.setVisibility(android.view.View.VISIBLE);
    }

    private EditText newConfigInput(String hint, String prefKey, String defaultValue) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(configPrefs.getString(prefKey, defaultValue));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        return input;
    }

    private boolean saveAndValidateCameraConfig() {
        String ssid = textValue(ssidInput);
        String bleName = textValue(bleNameInput);
        String bssid = textValue(bssidInput).toLowerCase(Locale.US);
        String passphrase = textValue(passphraseInput);
        String host = textValue(hostInput);
        if (host.isEmpty()) {
            host = DEFAULT_CAMERA_HOST;
            hostInput.setText(host);
        }
        if (ssid.isEmpty()) {
            postStatus("Enter your Osmo 360 Wi-Fi SSID");
            return false;
        }
        if (passphrase.isEmpty()) {
            postStatus("Enter your Osmo 360 Wi-Fi password");
            return false;
        }
        if (passphrase.length() < 8 || passphrase.length() > 63) {
            postStatus("Wi-Fi password must be 8-63 characters");
            return false;
        }
        if (!bssid.isEmpty() && !bssid.matches("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$")) {
            postStatus("Optional BSSID must look like aa:bb:cc:dd:ee:ff");
            return false;
        }
        if (!confirmInput.isChecked()) {
            postStatus("Confirm you have permission to connect to this camera");
            return false;
        }
        configPrefs.edit()
                .putString(PREF_CAMERA_SSID, ssid)
                .putString(PREF_CAMERA_BLE_NAME, bleName)
                .putString(PREF_CAMERA_BSSID, bssid)
                .putString(PREF_CAMERA_PASSPHRASE, passphrase)
                .putString(PREF_CAMERA_HOST, host)
                .putBoolean(PREF_USER_CONFIRMED, true)
                .apply();
        return true;
    }

    private void resetCameraConfig() {
        configPrefs.edit().clear().apply();
        ssidInput.setText("");
        bleNameInput.setText("");
        bssidInput.setText("");
        passphraseInput.setText("");
        hostInput.setText(DEFAULT_CAMERA_HOST);
        confirmInput.setChecked(false);
        cameraAdvertisedPassphrase = null;
        blePreflightCompleted = false;
        postStatus("Camera settings reset");
    }

    private String textValue(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String cameraSsid() {
        return textValue(ssidInput);
    }

    private String cameraBleName() {
        String name = textValue(bleNameInput);
        return name;
    }

    private String cameraBssid() {
        return textValue(bssidInput).toLowerCase(Locale.US);
    }

    private String configuredCameraHost() {
        String host = textValue(hostInput);
        return host.isEmpty() ? DEFAULT_CAMERA_HOST : host;
    }

    private void rememberCameraSsid(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return;
        }
        String normalized = ssid.trim();
        if (normalized.equals(cameraSsid())) {
            return;
        }
        ssidInput.setText(normalized);
        configPrefs.edit().putString(PREF_CAMERA_SSID, normalized).apply();
        Log.i(TAG, "Updated camera Wi-Fi SSID from discovery: " + normalized);
    }

    private boolean isLikelyOsmoBleName(String observedName, String expectedName) {
        if (observedName == null || observedName.trim().isEmpty()) {
            return false;
        }
        String observed = observedName.toLowerCase(Locale.US);
        String expected = expectedName == null ? "" : expectedName.trim().toLowerCase(Locale.US);
        return (!expected.isEmpty() && observed.contains(expected))
                || observed.contains("osmo360")
                || observed.contains("osmo")
                || observed.contains("dji");
    }

    private boolean advertisesOsmoBleService(ScanResult result) {
        if (result == null || result.getScanRecord() == null || result.getScanRecord().getServiceUuids() == null) {
            return false;
        }
        for (ParcelUuid uuid : result.getScanRecord().getServiceUuids()) {
            if (uuid != null && BLE_SERVICE_FFF0.equals(uuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private String visibleOsmoSsidCandidate() {
        try {
            WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
            if (wifi == null) {
                return null;
            }
            String expectedSsid = cameraSsid();
            String expectedSuffix = cameraNameSuffix(expectedSsid);
            for (android.net.wifi.ScanResult result : wifi.getScanResults()) {
                String scannedSsid = result == null ? null : result.SSID;
                if (!isLikelyOsmoWifiSsid(scannedSsid)) {
                    continue;
                }
                if (expectedSsid.isEmpty()
                        || scannedSsid.equals(expectedSsid)
                        || (!expectedSuffix.isEmpty() && cameraNameSuffix(scannedSsid).equals(expectedSuffix))) {
                    return scannedSsid;
                }
            }
        } catch (SecurityException exc) {
            Log.w(TAG, "Cannot read Wi-Fi scan results for Osmo SSID candidate", exc);
        }
        return null;
    }

    private boolean isLikelyOsmoWifiSsid(String ssid) {
        if (ssid == null || ssid.trim().isEmpty()) {
            return false;
        }
        String normalized = ssid.toLowerCase(Locale.US);
        return normalized.contains("osmo360") || normalized.contains("osmo");
    }

    private String cameraNameSuffix(String name) {
        if (name == null) {
            return "";
        }
        int dash = name.lastIndexOf('-');
        if (dash >= 0 && dash + 1 < name.length()) {
            return name.substring(dash + 1).trim().toLowerCase(Locale.US);
        }
        return "";
    }

    private boolean isCameraSsidVisible() {
        try {
            WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
            if (wifi == null) {
                return true;
            }
            try {
                wifi.startScan();
            } catch (SecurityException exc) {
                Log.w(TAG, "Cannot refresh Wi-Fi scan results; using cached results", exc);
            }
            String expectedSsid = cameraSsid();
            if (expectedSsid.isEmpty()) {
                return true;
            }
            for (android.net.wifi.ScanResult result : wifi.getScanResults()) {
                String scannedSsid = result == null ? null : result.SSID;
                if (expectedSsid.equals(scannedSsid)) {
                    Log.i(TAG, "Camera SSID visible in scan results: " + expectedSsid);
                    return true;
                }
            }
            String discoveredSsid = visibleOsmoSsidCandidate();
            if (discoveredSsid != null) {
                rememberCameraSsid(discoveredSsid);
                return true;
            }
            Log.w(TAG, "Camera SSID not visible in scan results: " + expectedSsid);
            return false;
        } catch (SecurityException exc) {
            Log.w(TAG, "Cannot read Wi-Fi scan results; trying BLE first", exc);
            return false;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder.getSurface();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surface = null;
        stopPreview();
    }

    private void bindProcessToCameraWifi() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network cameraNetwork = findCameraWifiNetwork(cm);
        if (cameraNetwork != null) {
            boundNetwork = cameraNetwork;
            cm.bindProcessToNetwork(cameraNetwork);
            postStatus("Bound to Osmo Wi-Fi network");
            return;
        }
        String wifiIp = currentWifiIpAddress();
        if (wifiIp != null && wifiIp.startsWith("192.168.2.")) {
            boundNetwork = null;
            cm.bindProcessToNetwork(null);
            postStatus("Using Osmo Wi-Fi " + wifiIp);
            Log.i(TAG, "Using Osmo Wi-Fi fallback at " + wifiIp);
            return;
        }
        cm.bindProcessToNetwork(null);
        boundNetwork = null;
        postStatus("Not on Osmo Wi-Fi. Need 192.168.2.x" + (wifiIp == null ? "" : " (now " + wifiIp + ")"));
    }

    private void requestCameraWifi() {
        resetCameraWifiAttempts();
        requestCameraWifiWithCurrentCandidate();
    }

    private void beginCameraDiscovery() {
        triedBleWakeForCurrentStart = true;
        postStatus("Camera Wi-Fi not visible; scanning BLE for " + cameraBleName());
        startBlePreflight();
    }

    private void resetCameraWifiAttempts() {
        wifiAttemptToken++;
        unregisterCameraNetworkCallback();
        passphraseCandidateIndex = 0;
        requestCameraWifiWithBssid = true;
        requestCameraWifiWithWpa3 = false;
    }

    private void requestCameraWifiWithCurrentCandidate() {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            postStatus("Connect to Osmo Wi-Fi manually on this Android version");
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ensureLocationPermission();
            postStatus("Grant location, then tap Start again");
            return;
        }
        unregisterCameraNetworkCallback();
        int requestToken = wifiAttemptToken;
        String passphrase = currentPassphraseCandidate();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder()
                .setSsid(cameraSsid());
        if (requestCameraWifiWithWpa3 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            specifierBuilder.setWpa3Passphrase(passphrase);
        } else {
            specifierBuilder.setWpa2Passphrase(passphrase);
        }
        String bssid = cameraBssid();
        if (requestCameraWifiWithBssid && !bssid.isEmpty()) {
            specifierBuilder.setBssid(MacAddress.fromString(bssid));
        } else if (requestCameraWifiWithBssid) {
            requestCameraWifiWithBssid = false;
        }
        WifiNetworkSpecifier specifier = specifierBuilder.build();
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();
        cameraNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                if (requestToken != wifiAttemptToken) {
                    Log.i(TAG, "Ignoring stale Osmo Wi-Fi available callback token=" + requestToken);
                    return;
                }
                boundNetwork = network;
                cm.bindProcessToNetwork(network);
                postStatus("Osmo Wi-Fi connected; starting preview");
                Log.i(TAG, "Camera Wi-Fi available " + network);
                main.post(() -> startPreview());
            }

            @Override
            public void onLost(Network network) {
                if (requestToken != wifiAttemptToken) {
                    return;
                }
                if (network.equals(boundNetwork)) {
                    boundNetwork = null;
                    cm.bindProcessToNetwork(null);
                    postStatus("Osmo Wi-Fi lost");
                    Log.i(TAG, "Camera Wi-Fi lost " + network);
                }
            }

            @Override
            public void onUnavailable() {
                if (requestToken != wifiAttemptToken) {
                    Log.i(TAG, "Ignoring stale Osmo Wi-Fi unavailable callback token=" + requestToken);
                    return;
                }
                cameraNetworkCallback = null;
                retryNextCameraWifiRequest(requestToken);
            }
        };
        String matchMode = requestCameraWifiWithBssid ? "SSID/BSSID" : "SSID";
        String securityMode = requestCameraWifiWithWpa3 ? "WPA3-SAE" : "WPA2-PSK";
        postStatus("Choose " + cameraSsid() + " when Android asks for a device");
        Log.i(TAG, "Requesting Osmo Wi-Fi with " + matchMode + " " + securityMode
                + " specifier passphraseIndex=" + passphraseCandidateIndex);
        cm.requestNetwork(request, cameraNetworkCallback, WIFI_REQUEST_TIMEOUT_MS);
    }

    private void retryNextCameraWifiRequest(int requestToken) {
        if (requestToken != wifiAttemptToken) {
            Log.i(TAG, "Ignoring stale Osmo Wi-Fi retry token=" + requestToken);
            return;
        }
        Log.w(TAG, "Camera Wi-Fi unavailable for passphraseIndex="
                + passphraseCandidateIndex
                + " bssid=" + requestCameraWifiWithBssid
                + " wpa3=" + requestCameraWifiWithWpa3);
        if (requestCameraWifiWithBssid) {
            requestCameraWifiWithBssid = false;
            postStatus("Retrying Osmo Wi-Fi by SSID only");
            main.postDelayed(this::requestCameraWifiWithCurrentCandidate, 1000);
            return;
        }
        passphraseCandidateIndex++;
        if (hasCameraPassphraseCandidate(passphraseCandidateIndex)) {
            requestCameraWifiWithBssid = true;
            requestCameraWifiWithWpa3 = false;
            postStatus("Trying next Osmo Wi-Fi key");
            main.postDelayed(this::requestCameraWifiWithCurrentCandidate, 1000);
            return;
        }
        requestCameraWifiWithBssid = true;
        requestCameraWifiWithWpa3 = false;
        passphraseCandidateIndex = 0;
        cameraAdvertisedPassphrase = null;
        if (!triedBleWakeForCurrentStart) {
            postStatus("Camera Wi-Fi not visible; trying BLE wake-up");
            blePreflightInProgress = false;
            closeBle();
            main.postDelayed(this::beginCameraDiscovery, 1000);
            return;
        }
        postStatus("Camera not found. Power camera on, enable Wi-Fi/BLE, then tap Start");
    }

    private void unregisterCameraNetworkCallback() {
        if (cameraNetworkCallback == null) {
            return;
        }
        try {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(cameraNetworkCallback);
        } catch (Exception ignored) {
        }
        cameraNetworkCallback = null;
    }

    private void updateForcedLocalPort() {
        forcedLocalPort = getIntent().getIntExtra(EXTRA_LOCAL_PORT, -1);
        if (forcedLocalPort > 0) {
            Log.i(TAG, "Using forced local UDP port " + forcedLocalPort);
            postStatus("Using forced UDP port " + forcedLocalPort);
        }
    }

    private Network findCameraWifiNetwork(ConnectivityManager cm) {
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                continue;
            }
            LinkProperties linkProperties = cm.getLinkProperties(network);
            if (linkProperties == null) {
                continue;
            }
            for (LinkAddress address : linkProperties.getLinkAddresses()) {
                String hostAddress = address.getAddress().getHostAddress();
                if (hostAddress != null && hostAddress.startsWith("192.168.2.")) {
                    Log.i(TAG, "Found Osmo network " + network + " at " + hostAddress);
                    return network;
                }
            }
        }
        return null;
    }

    private String currentWifiIpAddress() {
        try {
            WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
            if (wifi == null) {
                return null;
            }
            WifiInfo info = wifi.getConnectionInfo();
            int ip = info.getIpAddress();
            if (ip == 0) {
                return null;
            }
            byte[] bytes = new byte[] {
                    (byte) (ip & 0xff),
                    (byte) ((ip >> 8) & 0xff),
                    (byte) ((ip >> 16) & 0xff),
                    (byte) ((ip >> 24) & 0xff)
            };
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException exc) {
            return null;
        }
    }

    private void startPreview() {
        startPreview(false);
    }

    private void startPreview(boolean resetBlePreflight) {
        if (surface == null) {
            postStatus("Surface is not ready yet");
            return;
        }
        if (!saveAndValidateCameraConfig()) {
            return;
        }
        if (resetBlePreflight) {
            blePreflightCompleted = false;
            blePreflightInProgress = false;
            triedBleWakeForCurrentStart = false;
            lastVideoProgressAtMs = 0;
            lastSoftMediaResumeAtMs = 0;
            lastMediaRearmAtMs = 0;
            latestVideo92EcPackets = 0;
            latestH264Bytes = 0;
            latestRenderedFrames = 0;
        }
        bindProcessToCameraWifi();
        if (boundNetwork == null && !hasCameraWifiIp()) {
            if (isCameraSsidVisible()) {
                triedBleWakeForCurrentStart = false;
                requestCameraWifi();
            } else {
                beginCameraDiscovery();
            }
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        startButton.setText("Stop");
        postStatus("Starting UDP replay/receive");
        Log.i(TAG, "Starting preview");
        executor.execute(this::runClient);
    }

    private boolean hasCameraWifiIp() {
        String wifiIp = cameraWifiIpAddress();
        return wifiIp != null && wifiIp.startsWith("192.168.2.");
    }

    private String cameraWifiIpAddress() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network cameraNetwork = findCameraWifiNetwork(cm);
        if (cameraNetwork != null) {
            LinkProperties linkProperties = cm.getLinkProperties(cameraNetwork);
            if (linkProperties != null) {
                for (LinkAddress address : linkProperties.getLinkAddresses()) {
                    String hostAddress = address.getAddress().getHostAddress();
                    if (hostAddress != null && hostAddress.startsWith("192.168.2.")) {
                        return hostAddress;
                    }
                }
            }
        }
        return currentWifiIpAddress();
    }

    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 7);
        }
    }

    private void ensureBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31
                && (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, 8);
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private boolean ensureBluetoothEnabledForDiscovery() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            postStatus("Bluetooth unavailable on this device");
            return false;
        }
        if (adapter.isEnabled()) {
            return true;
        }
        pendingStartAfterBluetoothEnable = true;
        postStatus("Turn on Bluetooth to find/wake the Osmo camera");
        try {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
        } catch (Exception exc) {
            Log.w(TAG, "Cannot open Bluetooth enable prompt", exc);
            postStatus("Turn on Bluetooth, then tap Start again");
        }
        return false;
    }

    private void startBlePreflight() {
        if (blePreflightInProgress) {
            bindProcessToCameraWifi();
            if (boundNetwork != null || hasCameraWifiIp()) {
                waitingForPairingApproval = false;
                blePreflightInProgress = false;
                wifiRequestedAfterBle = true;
                postStatus("Osmo Wi-Fi is up; starting preview");
                startPreview();
                return;
            }
            postStatus("BLE preflight already running");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 31
                && (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            ensureBluetoothPermissions();
            postStatus("Grant Bluetooth, then tap Start");
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (!ensureBluetoothEnabledForDiscovery()) {
            return;
        }
        BluetoothLeScanner scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
        if (scanner == null) {
            postStatus("Bluetooth scanner unavailable. Turn Bluetooth off/on, then Start");
            return;
        }
        blePreflightInProgress = true;
        wifiRequestedAfterBle = false;
        if (bluetoothGatt != null && bleFff4 != null && bleFff5 != null) {
            postStatus("Re-arming Osmo Wi-Fi with key " + (passphraseCandidateIndex + 1));
            writeBlePreflight(bluetoothGatt);
            return;
        }
        postStatus("Scanning BLE for Osmo");
        ScanCallback callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                if (name == null) {
                    try {
                        name = device.getName();
                    } catch (SecurityException ignored) {
                    }
                }
                String expectedName = cameraBleName();
                boolean serviceMatch = advertisesOsmoBleService(result);
                if (!serviceMatch && !isLikelyOsmoBleName(name, expectedName)) {
                    Log.i(TAG, "BLE ignored " + (name == null ? "<unnamed>" : name));
                    return;
                }
                if (name != null) {
                    rememberCameraSsid(name);
                }
                try {
                    scanner.stopScan(this);
                } catch (SecurityException ignored) {
                }
                postStatus("BLE found Osmo; connecting");
                Log.i(TAG, "BLE found " + name + " " + device.getAddress() + " serviceMatch=" + serviceMatch);
                pendingBleDevice = device;
                bleConnectAttempts = 0;
                connectBle(device);
            }

            @Override
            public void onScanFailed(int errorCode) {
                blePreflightInProgress = false;
                postStatus("BLE scan failed: " + errorCode);
                Log.w(TAG, "BLE scan failed " + errorCode);
            }
        };
        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(null, settings, callback);
            main.postDelayed(() -> {
                if (!blePreflightInProgress || bluetoothGatt != null) {
                    return;
                }
                try {
                    scanner.stopScan(callback);
                } catch (SecurityException ignored) {
                }
                blePreflightInProgress = false;
                postStatus("BLE not found; trying Android Wi-Fi chooser");
                Log.w(TAG, "BLE Osmo/DJI device not found before timeout");
                triedBleWakeForCurrentStart = true;
                requestCameraWifi();
            }, BLE_SCAN_TIMEOUT_MS);
        } catch (SecurityException exc) {
            blePreflightInProgress = false;
            postStatus("Bluetooth permission missing");
            Log.w(TAG, "BLE startScan denied", exc);
        }
    }

    private void connectBle(BluetoothDevice device) {
        try {
            closeBle();
            BluetoothGattCallback callback = new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    Log.i(TAG, "BLE state status=" + status + " newState=" + newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        bleConnectAttempts = 0;
                        postStatus("BLE connected; requesting MTU");
                        if (!gatt.requestMtu(247)) {
                            Log.w(TAG, "BLE requestMtu returned false; discovering services anyway");
                            gatt.discoverServices();
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (shouldRetryBleConnect(status)) {
                            int attempt = ++bleConnectAttempts;
                            postStatus("BLE retry " + attempt + " after GATT " + status);
                            Log.w(TAG, "BLE disconnected with status=" + status + "; retrying attempt=" + attempt);
                            main.postDelayed(() -> {
                                try {
                                    gatt.close();
                                } catch (SecurityException ignored) {
                                }
                                bluetoothGatt = null;
                                connectBle(pendingBleDevice);
                            }, 900L * attempt);
                        } else {
                            blePreflightInProgress = false;
                            closeBle();
                            postStatus("BLE disconnected");
                        }
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    Log.i(TAG, "BLE MTU changed mtu=" + mtu + " status=" + status);
                    postStatus("BLE MTU " + mtu + "; discovering services");
                    gatt.discoverServices();
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    BluetoothGattService service = gatt.getService(BLE_SERVICE_FFF0);
                    if (service == null) {
                        blePreflightInProgress = false;
                        postStatus("BLE service fff0 missing");
                        return;
                    }
                    bleFff3 = service.getCharacteristic(BLE_CHAR_FFF3);
                    bleFff4 = service.getCharacteristic(BLE_CHAR_FFF4);
                    bleFff5 = service.getCharacteristic(BLE_CHAR_FFF5);
                    bleFff7 = service.getCharacteristic(BLE_CHAR_FFF7);
                    if (bleFff4 == null || bleFff5 == null) {
                        blePreflightInProgress = false;
                        postStatus("BLE command chars missing");
                        return;
                    }
                    pendingNotifyCharacteristics = new ArrayList<>();
                    addNotifyCharacteristic(bleFff3);
                    addNotifyCharacteristic(bleFff4);
                    addNotifyCharacteristic(bleFff5);
                    addNotifyCharacteristic(bleFff7);
                    postStatus("BLE enabling notifications");
                    writeNextNotifyDescriptor(gatt);
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    byte[] value = characteristic.getValue();
                    Log.i(TAG, "BLE notify " + characteristic.getUuid() + " " + toHex(value));
                    processBleBytes(value);
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    Log.i(TAG, "BLE write " + characteristic.getUuid() + " status=" + status);
                    if (BLE_CHAR_FFF5.equals(characteristic.getUuid())) {
                        completeBleQueuedWrite();
                    } else {
                        main.postDelayed(() -> writeNextBlePreflightFrame(gatt), 1500);
                    }
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    Log.i(TAG, "BLE descriptor write " + descriptor.getCharacteristic().getUuid() + " status=" + status);
                    writeNextNotifyDescriptor(gatt);
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                bluetoothGatt = device.connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE);
            } else {
                bluetoothGatt = device.connectGatt(this, false, callback);
            }
        } catch (SecurityException exc) {
            blePreflightInProgress = false;
            postStatus("Bluetooth connect denied");
            Log.w(TAG, "BLE connect denied", exc);
        }
    }

    private boolean shouldRetryBleConnect(int status) {
        return blePreflightInProgress
                && pendingBleDevice != null
                && status != BluetoothGatt.GATT_SUCCESS
                && bleConnectAttempts < 4;
    }

    private void writeBlePreflight(BluetoothGatt gatt) {
        try {
            answeredBleRequests.clear();
            clearBleWriteQueue();
            int preflightToken = ++blePreflightToken;
            waitingForPairingApproval = false;
            blePairingApproved = false;
            bleWifiCredentialReceived = false;
            pairingApprovalToken++;
            waitingBleCommandSet = -1;
            waitingBleCommandId = -1;
            waitingBleCommandToken++;
            bleReceiveBuffer.reset();
            bleFff4.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            bleFff4.setValue(new byte[] { 0x01, 0x00 });
            pendingBleFrames = buildBlePreflightFrames();
            pendingBleFrameIndex = 0;
            postStatus("Sending LOVE pairing request; confirm on camera if prompted");
            Log.i(TAG, "BLE starting preflight token=" + preflightToken + " passphraseIndex=" + passphraseCandidateIndex);
            if (!gatt.writeCharacteristic(bleFff4)) {
                Log.w(TAG, "BLE wake write returned false; retrying preflight start");
                main.postDelayed(() -> {
                    if (blePreflightToken == preflightToken) {
                        writeBlePreflight(gatt);
                    }
                }, 300);
                return;
            }
            main.postDelayed(() -> {
                if (blePreflightToken == preflightToken && pendingBleFrameIndex == 0) {
                    Log.w(TAG, "BLE wake write callback not observed; starting preflight frames anyway");
                    writeNextBlePreflightFrame(gatt);
                }
            }, 1200);
        } catch (SecurityException exc) {
            blePreflightInProgress = false;
            postStatus("BLE write denied");
            Log.w(TAG, "BLE write denied", exc);
        }
    }

    private void writeNextBlePreflightFrame(BluetoothGatt gatt) {
        if (pendingBleFrames == null || pendingBleFrameIndex >= pendingBleFrames.length) {
            pendingBleFrames = null;
            pendingBleCommands = null;
            waitingBleCommandSet = -1;
            waitingBleCommandId = -1;
            waitingBleCommandToken++;
            if (!blePairingApproved) {
                Log.w(TAG, "BLE preflight reached Wi-Fi handoff without observed LOVE approval; continuing");
                postStatus("LOVE approval not observable; requesting Osmo Wi-Fi");
            }
            main.postDelayed(this::maybeRequestWifiAfterBle, 5000);
            return;
        }
        int preflightToken = blePreflightToken;
        byte[] frame = pendingBleFrames[pendingBleFrameIndex++];
        int commandIndex = pendingBleFrameIndex - 1;
        int timeoutMs = 5000;
        if (pendingBleCommands != null && commandIndex < pendingBleCommands.length) {
            waitingBleCommandSet = pendingBleCommands[commandIndex][0];
            waitingBleCommandId = pendingBleCommands[commandIndex][1];
            timeoutMs = bleResponseTimeoutMs(waitingBleCommandSet, waitingBleCommandId);
        } else {
            waitingBleCommandSet = -1;
            waitingBleCommandId = -1;
        }
        int commandToken = ++waitingBleCommandToken;
        int commandTimeoutMs = timeoutMs;
        int commandSet = waitingBleCommandSet;
        int commandId = waitingBleCommandId;
        if (frame == null) {
            Log.w(TAG, "BLE preflight frame missing for command index " + commandIndex + "; continuing");
            waitingBleCommandSet = -1;
            waitingBleCommandId = -1;
            waitingBleCommandToken++;
            writeNextBlePreflightFrame(gatt);
            return;
        }
        enqueueBleFrameNoResponse(frame, "BLE wrote preflight frame", true, () -> {
            main.postDelayed(() -> {
                if (blePreflightToken == preflightToken
                        && waitingBleCommandToken == commandToken
                        && (waitingBleCommandSet != -1 || waitingBleCommandId != -1)) {
                    Log.w(TAG, String.format("BLE command %02x/%02x response timeout; continuing",
                            waitingBleCommandSet, waitingBleCommandId));
                    waitingBleCommandSet = -1;
                    waitingBleCommandId = -1;
                    waitingBleCommandToken++;
                    writeNextBlePreflightFrame(gatt);
                }
            }, commandTimeoutMs);
        });
    }

    private static int bleResponseTimeoutMs(int cmdSet, int cmdId) {
        if (cmdSet == 0x02) {
            return 9000;
        }
        return 5000;
    }

    private void maybeRequestWifiAfterBle() {
        maybeRequestWifiAfterBle(1500);
    }

    private void maybeRequestWifiAfterBle(long delayMs) {
        if (wifiRequestedAfterBle) {
            return;
        }
        wifiRequestedAfterBle = true;
        blePreflightCompleted = true;
        main.postDelayed(() -> {
            blePreflightInProgress = false;
            bindProcessToCameraWifi();
            if (boundNetwork != null || hasCameraWifiIp()) {
                startPreview();
            } else {
                String discoveredSsid = visibleOsmoSsidCandidate();
                if (discoveredSsid != null) {
                    rememberCameraSsid(discoveredSsid);
                }
                if (isCameraSsidVisible()) {
                    requestCameraWifi();
                } else {
                    postStatus("Osmo Wi-Fi still not visible after BLE wake-up");
                }
            }
        }, delayMs);
    }

    private void closeBle() {
        if (bluetoothGatt == null) {
            return;
        }
        try {
            bluetoothGatt.close();
        } catch (SecurityException ignored) {
        }
        bluetoothGatt = null;
        bleFff3 = null;
        bleFff4 = null;
        bleFff5 = null;
        bleFff7 = null;
    }

    private void addNotifyCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return;
        }
        pendingNotifyCharacteristics.add(characteristic);
    }

    private void writeNextNotifyDescriptor(BluetoothGatt gatt) {
        if (pendingNotifyCharacteristics == null || pendingNotifyCharacteristics.isEmpty()) {
            postStatus("BLE waking Osmo Wi-Fi");
            writeBlePreflight(gatt);
            return;
        }
        BluetoothGattCharacteristic characteristic = pendingNotifyCharacteristics.remove(0);
        boolean enabled = gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BLE_CCCD);
        if (descriptor == null) {
            Log.i(TAG, "BLE notify subscription " + characteristic.getUuid() + " enabled=" + enabled + " descriptor=missing");
            writeNextNotifyDescriptor(gatt);
            return;
        }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        try {
            if (!gatt.writeDescriptor(descriptor)) {
                Log.w(TAG, "BLE notify descriptor write returned false for " + characteristic.getUuid());
                writeNextNotifyDescriptor(gatt);
            }
        } catch (SecurityException exc) {
            Log.w(TAG, "BLE notify descriptor write denied for " + characteristic.getUuid(), exc);
            writeNextNotifyDescriptor(gatt);
        }
    }

    private void processBleBytes(byte[] value) {
        try {
            bleReceiveBuffer.write(value);
        } catch (IOException ignored) {
            return;
        }
        byte[] buffer = bleReceiveBuffer.toByteArray();
        int offset = 0;
        while (true) {
            int start = indexOf(buffer, (byte) 0x55, offset);
            if (start < 0) {
                resetBleBuffer(new byte[0]);
                return;
            }
            if (buffer.length - start < 13) {
                resetBleBuffer(copyOfRange(buffer, start, buffer.length));
                return;
            }
            int length = (buffer[start + 1] & 0xff) | ((buffer[start + 2] & 0x03) << 8);
            if (length < 13 || length > 1023) {
                offset = start + 1;
                continue;
            }
            if (buffer.length - start < length) {
                resetBleBuffer(copyOfRange(buffer, start, buffer.length));
                return;
            }
            byte[] frame = copyOfRange(buffer, start, start + length);
            logBleFrame(frame);
            handleBleFrame(frame);
            offset = start + length;
            if (offset >= buffer.length) {
                resetBleBuffer(new byte[0]);
                return;
            }
        }
    }

    private void resetBleBuffer(byte[] remaining) {
        bleReceiveBuffer.reset();
        try {
            bleReceiveBuffer.write(remaining);
        } catch (IOException ignored) {
        }
    }

    private void logBleFrame(byte[] frame) {
        int expected = (frame[frame.length - 2] & 0xff) | ((frame[frame.length - 1] & 0xff) << 8);
        boolean crcOk = crc16Ccitt(frame, 0, frame.length - 2) == expected;
        int sender = frame[4] & 0xff;
        int receiver = frame[5] & 0xff;
        int sequence = ((frame[6] & 0xff) << 8) | (frame[7] & 0xff);
        int flags = frame[8] & 0xff;
        int cmdSet = frame[9] & 0xff;
        int cmdId = frame[10] & 0xff;
        int payloadLength = Math.max(0, frame.length - 13);
        Log.i(TAG, "BLE frame " + String.format(Locale.US,
                "%02x->%02x seq=%04x flags=%02x cmd=%02x/%02x crc=%s payloadLength=%d",
                sender, receiver, sequence, flags, cmdSet, cmdId, crcOk, payloadLength));
    }

    private void handleBleFrame(byte[] frame) {
        if (bluetoothGatt == null || bleFff5 == null) {
            return;
        }
        int sender = frame[4] & 0xff;
        int receiver = frame[5] & 0xff;
        int sequence = ((frame[6] & 0xff) << 8) | (frame[7] & 0xff);
        int flags = frame[8] & 0xff;
        int cmdSet = frame[9] & 0xff;
        int cmdId = frame[10] & 0xff;
        byte[] payload = copyOfRange(frame, 11, frame.length - 2);
        if (receiver == BLE_APP_ADDRESS) {
            rememberBlePropertyRequest(cmdSet, cmdId, payload);
        }
        if (receiver == BLE_APP_ADDRESS && isBleCommandResponseFlag(flags) && cmdSet == waitingBleCommandSet && cmdId == waitingBleCommandId) {
            Log.i(TAG, String.format(Locale.US, "BLE command response cmd=%02x/%02x payloadLength=%d",
                    cmdSet, cmdId, payload.length));
            waitingBleCommandSet = -1;
            waitingBleCommandId = -1;
            waitingBleCommandToken++;
            if (cmdSet == 0x07 && cmdId == 0x45 && pairingRequiresCameraApproval(payload)) {
                waitingForPairingApproval = true;
                blePairingApproved = true;
                int approvalToken = ++pairingApprovalToken;
                int preflightToken = blePreflightToken;
                postStatus("LOVE pairing status received; continuing soon");
                Log.i(TAG, "BLE pairing approval pending");
                main.postDelayed(() -> {
                    if (waitingForPairingApproval && pairingApprovalToken == approvalToken && blePreflightToken == preflightToken) {
                        waitingForPairingApproval = false;
                        Log.w(TAG, "BLE pairing approval event not observed after grace period; continuing preflight");
                        writeNextBlePreflightFrame(bluetoothGatt);
                    }
                }, 2500);
                return;
            }
            if (cmdSet == 0x07 && cmdId == 0x45) {
                blePairingApproved = true;
            }
            if (cmdSet == 0x07 && cmdId == 0x0e) {
                String blePassphrase = extractCameraWifiString(payload);
                if (blePassphrase != null) {
                    cameraAdvertisedPassphrase = blePassphrase;
                    bleWifiCredentialReceived = true;
                    Log.i(TAG, "BLE camera Wi-Fi password received");
                    maybeRequestWifiAfterBle(250);
                } else {
                    Log.w(TAG, "BLE Wi-Fi password response had no usable token");
                }
            }
            scheduleNextBlePreflightFrame(250);
            return;
        }
        if (receiver != BLE_APP_ADDRESS || flags != 0x40) {
            return;
        }
        Log.i(TAG, String.format(Locale.US,
                "BLE camera request pending handler sender=%02x seq=%04x cmd=%02x/%02x payloadLength=%d",
                sender, sequence, cmdSet, cmdId, payload.length));
        answerBleCameraRequest(sender, sequence, cmdSet, cmdId, payload);
        if (cmdSet == 0x07 && cmdId == 0x46 && waitingForPairingApproval) {
            waitingForPairingApproval = false;
            blePairingApproved = true;
            pairingApprovalToken++;
            postStatus("Camera approved LOVE pairing; continuing");
            Log.i(TAG, "BLE pairing event acknowledged; continuing preflight");
            scheduleNextBlePreflightFrame(250);
        }
    }

    private void scheduleNextBlePreflightFrame(int delayMs) {
        int preflightToken = blePreflightToken;
        int commandToken = waitingBleCommandToken;
        main.postDelayed(() -> {
            if (blePreflightToken != preflightToken || waitingBleCommandToken != commandToken) {
                Log.i(TAG, "Skipping stale BLE preflight continuation");
                return;
            }
            if (waitingBleCommandSet != -1 || waitingBleCommandId != -1) {
                Log.i(TAG, String.format("BLE command %02x/%02x still pending; not advancing",
                        waitingBleCommandSet, waitingBleCommandId));
                return;
            }
            writeNextBlePreflightFrame(bluetoothGatt);
        }, delayMs);
    }

    private static boolean pairingRequiresCameraApproval(byte[] payload) {
        if (payload.length < 2) {
            return false;
        }
        return payload[0] == 0x00 && payload[1] != 0x00;
    }

    private void answerBleCameraRequest(int sender, int sequence, int cmdSet, int cmdId, byte[] payload) {
        if (!shouldAnswerBleCameraRequest(cmdSet, cmdId)) {
            return;
        }
        String key = bleRequestDedupeKey(sender, sequence, cmdSet, cmdId);
        if (answeredBleRequests.contains(key)) {
            return;
        }
        answeredBleRequests.add(key);
        byte[] responsePayload = payload;
        if (cmdSet == 0x07 && cmdId == 0x46) {
            responsePayload = new byte[] { 0x00 };
        }
        byte[] response = buildBleDumlWithSequence(sender, sequence, 0xc0, cmdSet, cmdId, responsePayload);
        enqueueBleFrameNoResponse(response, "BLE answered camera request", cmdSet == 0x07 && cmdId == 0x46, null);
    }

    private static boolean isBleCommandResponseFlag(int flags) {
        return flags == 0xc0 || flags == 0x80;
    }

    private static boolean shouldAnswerBleCameraRequest(int cmdSet, int cmdId) {
        return (cmdSet == 0x07 && cmdId == 0x46)
                || (cmdSet == 0x00 && cmdId == 0x99)
                || (cmdSet == 0x00 && cmdId == 0x81)
                || (cmdSet == 0x00 && cmdId == 0x82)
                || (cmdSet == 0x00 && cmdId == 0x88)
                || (cmdSet == 0x00 && cmdId == 0x74);
    }

    private void rememberBlePropertyRequest(int cmdSet, int cmdId, byte[] payload) {
        if (cmdSet != 0x00 || cmdId != 0x99 || payload.length < 16
                || payload[0] != 0x02 || payload[1] != 0x06) {
            return;
        }
        for (int offset = 8; offset < Math.min(payload.length, 24); offset++) {
            int end = indexOf(payload, (byte) 0x00, offset);
            if (end <= offset) {
                continue;
            }
            String name = new String(payload, offset, end - offset, java.nio.charset.StandardCharsets.US_ASCII);
            if (!isUdp92EcBootstrapProperty(name)) {
                continue;
            }
            liveBlePropertyRequests.put(name, buildUdp92EcPropertyPayloadFromBle(payload, offset, end));
            Log.i(TAG, "Cached BLE property for 92ec bootstrap " + name
                    + " id=" + toHex(copyOfRange(payload, 4, 8)));
            return;
        }
    }

    private static boolean isUdp92EcBootstrapProperty(String name) {
        for (String bootstrapName : UDP_92EC_BOOTSTRAP_PROPERTY_NAMES) {
            if (bootstrapName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] buildUdp92EcPropertyPayloadFromBle(byte[] blePayload, int nameOffset, int nameEnd) {
        int nameLength = nameEnd - nameOffset;
        byte[] payload = new byte[15 + nameLength + 4];
        payload[0] = 0x02;
        payload[1] = 0x02;
        System.arraycopy(blePayload, 4, payload, 4, 4);
        payload[8] = 0x00;
        payload[9] = 0x00;
        payload[10] = 0x00;
        payload[11] = (byte) (nameLength + 6);
        payload[12] = 0x00;
        payload[13] = (byte) nameLength;
        payload[14] = 0x00;
        System.arraycopy(blePayload, nameOffset, payload, 15, nameLength);
        return payload;
    }

    private static byte[] buildUdp92EcPropertyRequestPayload(int requestId, String name) {
        byte[] nameBytes = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] payload = new byte[15 + nameBytes.length + 4];
        payload[0] = 0x02;
        payload[1] = 0x02;
        writeLittleEndianShort(payload, 4, requestId);
        payload[11] = (byte) (nameBytes.length + 6);
        payload[13] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, payload, 15, nameBytes.length);
        return payload;
    }

    private static String bleRequestDedupeKey(int sender, int sequence, int cmdSet, int cmdId) {
        return String.format("%02x:%04x:%02x:%02x", sender, sequence, cmdSet, cmdId);
    }

    private void enqueueBleFrameNoResponse(byte[] frame, String label, boolean priority, Runnable onAccepted) {
        BleWrite write = new BleWrite(frame, label, onAccepted);
        if (priority) {
            pendingBleWrites.addFirst(write);
        } else {
            pendingBleWrites.addLast(write);
        }
        drainBleWriteQueue();
    }

    private void drainBleWriteQueue() {
        if (bleWriteInFlight || pendingBleWrites.isEmpty() || bluetoothGatt == null || bleFff5 == null) {
            return;
        }
        BleWrite write = pendingBleWrites.peekFirst();
        try {
            bleFff5.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            bleFff5.setValue(write.frame);
            if (!bluetoothGatt.writeCharacteristic(bleFff5)) {
                Log.w(TAG, write.label + " writeCharacteristic busy; retrying " + toHex(write.frame));
                main.postDelayed(this::drainBleWriteQueue, 80);
                return;
            }
            bleWriteInFlight = true;
            activeBleWrite = write;
            Log.i(TAG, write.label + " " + toHex(write.frame));
            if (write.onAccepted != null) {
                write.onAccepted.run();
            }
        } catch (SecurityException exc) {
            Log.w(TAG, write.label + " denied", exc);
            pendingBleWrites.removeFirst();
            main.post(this::drainBleWriteQueue);
        }
    }

    private void completeBleQueuedWrite() {
        if (activeBleWrite != null && !pendingBleWrites.isEmpty() && pendingBleWrites.peekFirst() == activeBleWrite) {
            pendingBleWrites.removeFirst();
        }
        activeBleWrite = null;
        bleWriteInFlight = false;
        drainBleWriteQueue();
    }

    private void clearBleWriteQueue() {
        pendingBleWrites.clear();
        activeBleWrite = null;
        bleWriteInFlight = false;
    }

    private static int indexOf(byte[] buffer, byte value, int start) {
        for (int i = start; i < buffer.length; i++) {
            if (buffer[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] copyOfRange(byte[] buffer, int start, int end) {
        int length = Math.max(0, end - start);
        byte[] out = new byte[length];
        if (length > 0) {
            System.arraycopy(buffer, start, out, 0, length);
        }
        return out;
    }

    private byte[][] buildBlePreflightFrames() {
        pendingBleCommands = new int[][] {
                { 0x07, 0x45 },
                { 0x07, 0x07 },
                { 0x07, 0x0e },
                { 0x02, 0xe1 },
                { 0x02, 0x8e },
                { 0x07, 0xab },
                { 0x02, 0x09 },
                { 0x02, 0x8e }
        };
        return new byte[][] {
                buildBleDuml(0x07, 0x07, 0x45, packStrings("001749319286102", "love")),
                buildBleDuml(0x1b, 0x07, 0x07, new byte[] { 0x00 }),
                buildBleDuml(0x1b, 0x07, 0x0e, new byte[] { 0x00 }),
                buildBleDuml(0x08, 0x02, 0xe1, new byte[] { 0x1a }),
                buildBleDuml(0x08, 0x02, 0x8e, hex("00011c00")),
                buildBleDuml(0x1b, 0x07, 0xab, new byte[0]),
                buildBleDuml(0x08, 0x02, 0x09, liveViewSubscribePayload(true)),
                buildBleDuml(0x08, 0x02, 0x8e, hex("01011a000101"))
        };
    }

    private static byte[] liveViewSubscribePayload(boolean enabled) {
        byte[] payload = new byte[11];
        payload[10] = (byte) (enabled ? 0x03 : 0x04);
        return payload;
    }

    private String currentPassphraseCandidate() {
        if (passphraseCandidateIndex == 0
                && cameraAdvertisedPassphrase != null
                && !cameraAdvertisedPassphrase.isEmpty()) {
            return cameraAdvertisedPassphrase;
        }
        return textValue(passphraseInput);
    }

    private boolean hasCameraPassphraseCandidate(int candidateIndex) {
        if (candidateIndex == 0) {
            return (cameraAdvertisedPassphrase != null && !cameraAdvertisedPassphrase.isEmpty())
                    || !textValue(passphraseInput).isEmpty();
        }
        return candidateIndex == 1
                && cameraAdvertisedPassphrase != null
                && !cameraAdvertisedPassphrase.isEmpty()
                && !cameraAdvertisedPassphrase.equals(textValue(passphraseInput));
    }

    private static String extractCameraWifiString(byte[] payload) {
        String best = null;
        int start = -1;
        for (int i = 0; i <= payload.length; i++) {
            boolean asciiToken = i < payload.length
                    && ((payload[i] >= '0' && payload[i] <= '9')
                    || (payload[i] >= 'A' && payload[i] <= 'Z')
                    || (payload[i] >= 'a' && payload[i] <= 'z'));
            if (asciiToken) {
                if (start < 0) {
                    start = i;
                }
                continue;
            }
            if (start >= 0) {
                int length = i - start;
                if (length >= 8 && length <= 63) {
                    String token = new String(payload, start, length, java.nio.charset.StandardCharsets.US_ASCII);
                    if (best == null || token.length() > best.length()) {
                        best = token;
                    }
                }
                start = -1;
            }
        }
        return best;
    }

    private byte[] buildBleDuml(int receiver, int cmdSet, int cmdId, byte[] payload) {
        int sequence = bleSequence++ & 0xffff;
        return buildBleDumlWithSequence(receiver, sequence, 0x40, cmdSet, cmdId, payload);
    }

    private byte[] buildBleDumlWithSequence(int receiver, int sequence, int flags, int cmdSet, int cmdId, byte[] payload) {
        int length = 13 + payload.length;
        byte[] frame = new byte[length];
        frame[0] = 0x55;
        frame[1] = (byte) (length & 0xff);
        frame[2] = (byte) (((length >> 8) & 0x03) | 0x04);
        frame[3] = (byte) crc8Classic(frame, 0, 3);
        frame[4] = (byte) BLE_APP_ADDRESS;
        frame[5] = (byte) receiver;
        frame[6] = (byte) ((sequence >> 8) & 0xff);
        frame[7] = (byte) (sequence & 0xff);
        frame[8] = (byte) flags;
        frame[9] = (byte) cmdSet;
        frame[10] = (byte) cmdId;
        System.arraycopy(payload, 0, frame, 11, payload.length);
        int checksum = crc16Mimo(frame, 0, length - 2);
        frame[length - 2] = (byte) (checksum & 0xff);
        frame[length - 1] = (byte) ((checksum >> 8) & 0xff);
        return frame;
    }

    private static byte[] packStrings(String first, String second) {
        byte[] firstBytes = first.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] secondBytes = second.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + firstBytes.length + secondBytes.length];
        payload[0] = (byte) firstBytes.length;
        System.arraycopy(firstBytes, 0, payload, 1, firstBytes.length);
        int secondOffset = 1 + firstBytes.length;
        payload[secondOffset] = (byte) secondBytes.length;
        System.arraycopy(secondBytes, 0, payload, secondOffset + 1, secondBytes.length);
        return payload;
    }

    private static int crc8(byte[] data, int offset, int length) {
        int crc = 0xee;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x01) != 0) {
                    crc = ((crc >> 1) ^ 0x8c) & 0xff;
                } else {
                    crc = (crc >> 1) & 0xff;
                }
            }
        }
        return crc & 0xff;
    }

    private static int crc8Classic(byte[] data, int offset, int length) {
        int crc = 0x77;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x01) != 0) {
                    crc = ((crc >> 1) ^ 0x8c) & 0xff;
                } else {
                    crc = (crc >> 1) & 0xff;
                }
            }
        }
        return crc & 0xff;
    }

    private static int crc16Ccitt(byte[] data, int offset, int length) {
        int crc = 0x496c;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = ((crc >> 1) ^ 0x8408) & 0xffff;
                } else {
                    crc = (crc >> 1) & 0xffff;
                }
            }
        }
        return crc & 0xffff;
    }

    private static int crc16Classic(byte[] data, int offset, int length) {
        int crc = 0x3692;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = ((crc >> 1) ^ 0xa001) & 0xffff;
                } else {
                    crc = (crc >> 1) & 0xffff;
                }
            }
        }
        return crc & 0xffff;
    }

    private static int crc16Mimo(byte[] data, int offset, int length) {
        int crc = 0x3692;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = ((crc >> 1) ^ 0x8408) & 0xffff;
                } else {
                    crc = (crc >> 1) & 0xffff;
                }
            }
        }
        return crc & 0xffff;
    }

    private void stopPreview() {
        if (running.compareAndSet(true, false)) {
            postStatus("Stopping");
        }
        releaseDecoder();
        main.post(() -> startButton.setText("Start Osmo Preview"));
    }

    private void finishBlePreflightForPreview() {
        blePreflightInProgress = false;
        blePreflightCompleted = true;
        pendingBleFrames = null;
        pendingBleCommands = null;
        pendingBleFrameIndex = 0;
        waitingBleCommandSet = -1;
        waitingBleCommandId = -1;
        waitingBleCommandToken++;
        clearBleWriteQueue();
        Log.i(TAG, "BLE preflight finalized before UDP preview");
    }

    private void runClient() {
        DatagramSocket socket = null;
        List<DatagramSocket> replaySockets = new ArrayList<>();
        WifiManager.MulticastLock multicastLock = null;
        try {
            bindProcessToCameraWifi();
            String wifiIp = cameraWifiIpAddress();
            if (wifiIp == null || !wifiIp.startsWith("192.168.2.")) {
                postStatus("No Osmo route. Current Wi-Fi IP: " + (wifiIp == null ? "none" : wifiIp));
                Log.w(TAG, "Cannot start preview without Osmo Wi-Fi route; wifiIp=" + wifiIp);
                running.set(false);
                return;
            }

            WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
            multicastLock = wifi.createMulticastLock("osmo-preview");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();

            finishBlePreflightForPreview();
            socket = openPreviewSocket(PREFERRED_REPLAY_PORT);
            Log.i(TAG, "Primary UDP port " + socket.getLocalPort());
            sendTcpControlActivation();

            List<SetupPacket> setupPackets = ENABLE_SETUP_REPLAY ? readSetupPackets() : new ArrayList<>();
            replaySockets = openReplaySockets(setupPackets, socket);
            InetAddress camera = InetAddress.getByName(configuredCameraHost());
            AtomicReference<Exception> receiveError = new AtomicReference<>();
            List<Thread> receivers = new ArrayList<>();
            for (DatagramSocket receiveSocket : replaySockets) {
                Thread receiver = new Thread(() -> receiveCameraUdp(receiveSocket, receiveError),
                        "osmo-udp-receiver-" + receiveSocket.getLocalPort());
                receiver.start();
                receivers.add(receiver);
            }

            sendUdpLiveviewTransmitProbe(replaySockets, camera);
            sendUdp92EcBootstrap(replaySockets, camera);
            final List<DatagramSocket> liveviewSockets = replaySockets;
            Thread keepalive = new Thread(() -> keepUdpLiveviewAlive(liveviewSockets, camera, receiveError),
                    "osmo-liveview-keepalive");
            keepalive.start();
            Thread tcpKeepalive = new Thread(() -> keepTcpControlAlive(receiveError),
                    "osmo-tcp-control-keepalive");
            tcpKeepalive.start();
            if (ENABLE_SETUP_REPLAY) {
                postStatus("Replaying " + setupPackets.size() + " packets from UDP " + socket.getLocalPort());
                for (int i = 0; i < setupPackets.size(); i++) {
                    if (!running.get()) {
                        return;
                    }
                    Exception error = receiveError.get();
                    if (error != null) {
                        throw error;
                    }
                    SetupPacket setup = setupPackets.get(i);
                    DatagramSocket replaySocket = forcedLocalPort > 0
                            ? replaySockets.get(0)
                            : socketForPort(replaySockets, setup.localPort);
                    replaySocket.send(new DatagramPacket(setup.payload, setup.payload.length, camera, CAMERA_PORT));
                    if ((i + 1) % 250 == 0 || i + 1 == setupPackets.size()) {
                        postStatus("Replayed " + (i + 1) + "/" + setupPackets.size());
                        Log.i(TAG, "Replayed " + (i + 1) + "/" + setupPackets.size());
                    }
                    sleepQuietly(2);
                }
                sendUdpLiveviewTransmitProbe(replaySockets, camera);
                postStatus("Replay done; waiting for camera UDP on " + socket.getLocalPort());
            } else {
                postStatus("Live-view commands sent; waiting for camera UDP on " + socket.getLocalPort());
            }
            while (running.get() && receiveError.get() == null) {
                sleepQuietly(200);
            }
            if (receiveError.get() != null) {
                throw receiveError.get();
            }
        } catch (Exception exc) {
            Log.e(TAG, "Preview failed", exc);
            postStatus("Error: " + exc.getMessage());
        } finally {
            boolean restartPreview = mediaSessionRestartRequested && surface != null && !isFinishing();
            for (DatagramSocket replaySocket : replaySockets) {
                replaySocket.close();
            }
            if (socket != null && !replaySockets.contains(socket)) {
                socket.close();
            }
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
            releaseDecoder();
            running.set(false);
            main.post(() -> {
                startButton.setText("Start Osmo Preview");
                if (restartPreview) {
                    mediaSessionRestartRequested = false;
                    postStatus("Restarting preview after media stall");
                    main.postDelayed(() -> startPreview(true), 1200);
                }
            });
        }
    }

    private void receiveCameraUdp(DatagramSocket socket, AtomicReference<Exception> receiveError) {
        byte[] buffer = new byte[65535];
        long packets = 0;
        long h264Bytes = 0;
        long mediaPackets = 0;
        long dji92EcVideoPackets = 0;
        long dji92EcNonVideoLargePackets = 0;
        long maxUdpLength = 0;
        long receiveTimeouts = 0;
        long lastStatus = System.currentTimeMillis();
        long lastH264At = 0;
        int packetSamples = 0;
        int postBurst92EcSamples = 0;
        ByteArrayOutputStream annexBuffer = new ByteArrayOutputStream(256 * 1024);

        while (running.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException timeoutOrClosed) {
                if (!running.get() || socket.isClosed()) {
                    return;
                }
                receiveTimeouts++;
                long now = System.currentTimeMillis();
                drainDecoderOutput(0);
                if (now - lastStatus > 1000) {
                    flushStalledH264(annexBuffer, lastH264At, now);
                    drainDecoderOutput(50000);
                    if (socket.getLocalPort() == PREFERRED_92EC_PORT) {
                        postStatus("Waiting for UDP; timeouts " + receiveTimeouts);
                    }
                    lastStatus = now;
                }
                continue;
            } catch (Exception exc) {
                receiveError.compareAndSet(null, exc);
                return;
            }
            packets++;
            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
            maxUdpLength = Math.max(maxUdpLength, data.length);
            if (hasDji92EcMagic(data)) {
                rememberCamera92EcCounters(data);
                answerUdp92EcCameraRequest(socket, data);
                sendUdp92EcCameraCounterAckQuietly(socket, packet.getAddress());
            }
            if (isDjiMediaPacket(data)) {
                mediaPackets++;
                if (isDji92EcVideoPacket(data)) {
                    dji92EcVideoPackets++;
                } else if (hasDji92EcMagic(data)) {
                    dji92EcNonVideoLargePackets++;
                    if (h264Bytes > 0 && postBurst92EcSamples < 30) {
                        postBurst92EcSamples++;
                        Log.i(TAG, "Post-burst non-video 92ec sample " + postBurst92EcSamples
                                + " port=" + socket.getLocalPort()
                                + " len=" + data.length
                                + " byte6=" + (data.length > 6 ? data[6] & 0xff : -1)
                                + " field16=" + String.format(Locale.US, "%08x", readLittleEndianInt(data, 16))
                                + " annexStart=" + findBestAnnexStart(data)
                                + " first80=" + toHex(copyOfRange(data, 0, Math.min(data.length, 80))));
                    }
                }
                if (mediaPackets <= 5) {
                    Log.i(TAG, "Large DJI media packet port=" + socket.getLocalPort()
                            + " len=" + data.length
                            + " djiTotal=" + djiTotalLength(data)
                            + " magic=" + djiMagic(data)
                            + " first64=" + toHex(copyOfRange(data, 0, Math.min(data.length, 64))));
                }
            }
            if (packetSamples < 20) {
                packetSamples++;
                int totalLength = djiTotalLength(data);
                int annexStart = findBestAnnexStart(data);
                Log.i(TAG, "UDP sample " + packetSamples
                        + " port=" + socket.getLocalPort()
                        + " len=" + data.length
                        + " djiTotal=" + totalLength
                        + " magic=" + djiMagic(data)
                        + " annexStart=" + annexStart
                        + " nalType=" + (annexStart >= 0 ? nalUnitType(data, annexStart, data.length - annexStart) : -1)
                        + " first64=" + toHex(copyOfRange(data, 0, Math.min(data.length, 64))));
            }
            byte[] h264 = extractH264Candidate(data);
            if (h264.length > 0) {
                annexBuffer.write(h264, 0, h264.length);
                h264Bytes += h264.length;
                lastH264At = System.currentTimeMillis();
                rememberMediaProgress(dji92EcVideoPackets, h264Bytes);
                writeH264DiagnosticDump(h264);
                feedCompleteNalUnits(annexBuffer, false);
            }
            long now = System.currentTimeMillis();
            drainDecoderOutput(0);
            if (now - lastStatus > 1000) {
                flushStalledH264(annexBuffer, lastH264At, now);
                drainDecoderOutput(50000);
                if (socket.getLocalPort() == PREFERRED_92EC_PORT) {
                    postStatus("Packets " + packets + ", media " + mediaPackets + ", H264 " + h264Bytes
                            + " bytes, queued " + queuedNalUnits + ", rendered " + renderedFrames);
                }
                Log.i(TAG, "Received port=" + socket.getLocalPort()
                        + " packets=" + packets
                        + " mediaPackets=" + mediaPackets
                        + " video92ec=" + dji92EcVideoPackets
                        + " nonVideo92ec=" + dji92EcNonVideoLargePackets
                        + " maxUdpLength=" + maxUdpLength
                        + " h264Bytes=" + h264Bytes
                        + " queuedNalUnits=" + queuedNalUnits + " renderedFrames=" + renderedFrames);
                lastStatus = now;
            }
        }
    }

    private List<SetupPacket> readSetupPackets() throws IOException {
        List<SetupPacket> packets = new ArrayList<>();
        try (InputStream in = getAssets().open("osmo_9004_setup_58382.bin");
             DataInputStream data = new DataInputStream(in)) {
            while (data.available() > 0) {
                int localPort = readLittleEndianInt(data);
                int length = readLittleEndianInt(data);
                if (length <= 0 || length > 65535) {
                    throw new IOException("Bad setup packet length: " + length);
                }
                byte[] payload = new byte[length];
                data.readFully(payload);
                packets.add(new SetupPacket(localPort, payload));
            }
        }
        return packets;
    }

    private List<DatagramSocket> openReplaySockets(List<SetupPacket> setupPackets, DatagramSocket primarySocket)
            throws IOException {
        List<DatagramSocket> sockets = new ArrayList<>();
        sockets.add(primarySocket);
        if (forcedLocalPort > 0) {
            Log.i(TAG, "Forced UDP port active; replaying all setup packets through " + primarySocket.getLocalPort());
            return sockets;
        }
        Set<Integer> ports = new LinkedHashSet<>();
        for (SetupPacket packet : setupPackets) {
            ports.add(packet.localPort);
        }
        if (!ENABLE_SETUP_REPLAY && forcedLocalPort <= 0) {
            ports.add(PREFERRED_92EC_PORT);
        }
        for (int port : ports) {
            if (port == primarySocket.getLocalPort()) {
                continue;
            }
            DatagramSocket extra = openPreviewSocket(port);
            sockets.add(extra);
        }
        List<Integer> activePorts = new ArrayList<>();
        for (DatagramSocket replaySocket : sockets) {
            activePorts.add(replaySocket.getLocalPort());
        }
        Log.i(TAG, "Active UDP replay sockets " + activePorts);
        postStatus("Active UDP ports " + activePorts);
        return sockets;
    }

    private DatagramSocket socketForPort(List<DatagramSocket> sockets, int localPort) {
        for (DatagramSocket socket : sockets) {
            if (socket.getLocalPort() == localPort) {
                return socket;
            }
        }
        return sockets.get(0);
    }

    private void sendUdpLiveviewTransmitProbe(List<DatagramSocket> sockets, InetAddress camera) throws IOException {
        for (DatagramSocket socket : sockets) {
            if (socket.getLocalPort() == PREFERRED_92EC_PORT && sockets.size() > 1) {
                continue;
            }
            for (UdpDumlCommand command : UDP_LIVEVIEW_START_COMMANDS) {
                sendUdpDumlPacket(socket, camera, command.receiver, command.cmdSet, command.cmdId, command.payload);
            }
            byte[] packet = buildUdpDumlPacket(
                    0x41,
                    0x09,
                    0xa8,
                    MIMO_LIVEVIEW_TRANSMIT_CTRL_PAYLOAD);
            socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
            Log.i(TAG, "Sent UDP 09/a8 liveview transmit probe port=" + socket.getLocalPort()
                    + " packet=" + toHex(packet));
            sleepQuietly(75);
        }
    }

    private void sendUdp92EcBootstrap(List<DatagramSocket> sockets, InetAddress camera) throws IOException {
        DatagramSocket socket = socketForPort(sockets, PREFERRED_92EC_PORT);
        if (socket.getLocalPort() != PREFERRED_92EC_PORT && forcedLocalPort <= 0) {
            Log.w(TAG, "92ec bootstrap using fallback UDP port " + socket.getLocalPort());
        }
        sendUdp92EcRawPacket(socket, camera, UDP_92EC_PRE_BOOTSTRAP_HANDSHAKE, "pre-bootstrap handshake");
        sendUdp92EcMarkerPacket(socket, camera, 0x6490, 0x6490, "initial");
        sendUdp92EcPreviewSessionCommands(socket, camera);
        List<UdpDumlCommand> commands = udp92EcBootstrapCommands();
        for (UdpDumlCommand command : commands) {
            byte[] packet = buildUdp92EcDumlPacket(command.receiver, command.cmdSet, command.cmdId, command.payload);
            socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
            Log.i(TAG, String.format(Locale.US, "Sent UDP 92ec %02x/%02x bootstrap port=%d packet=%s",
                    command.cmdSet, command.cmdId, socket.getLocalPort(), toHex(packet)));
            if (isPropertyCommand(command, "cam_fov")) {
                sendUdp92EcMarkerPacket(socket, camera, 0x64f0, 0x6558, "after cam_fov");
                udp92EcPreviousCounter = lastUdp92EcCounter;
            }
            sleepQuietly(12);
        }
        for (int i = 0; i < 9; i++) {
            byte[] packet = buildUdp92EcDumlAckPacket(0x28, 0x00, 0x99);
            socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
            Log.i(TAG, "Sent UDP 92ec 00/99 ack trigger port=" + socket.getLocalPort()
                    + " packet=" + toHex(packet));
            sleepQuietly(12);
        }
        sendUdp92EcPostVideoHeartbeatCommands(socket, camera);
        sendUdp92EcStartupSetupCommands(socket, camera);
    }

    private void sendUdp92EcPreviewSessionCommands(DatagramSocket socket, InetAddress camera) throws IOException {
        for (UdpDumlCommand command : UDP_92EC_PREVIEW_SESSION_COMMANDS) {
            byte[] packet = buildUdp92EcSessionDumlPacket(command.receiver, command.cmdSet, command.cmdId,
                    command.payload);
            socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
            Log.i(TAG, String.format(Locale.US, "Sent UDP 92ec %02x/%02x session port=%d packet=%s",
                    command.cmdSet, command.cmdId, socket.getLocalPort(), toHex(packet)));
            sleepQuietly(12);
        }
    }

    private void sendUdp92EcPostVideoHeartbeatCommands(DatagramSocket socket, InetAddress camera) throws IOException {
        for (UdpDumlCommand command : UDP_92EC_POST_VIDEO_HEARTBEAT_COMMANDS) {
            byte[] packet = buildUdp92EcDumlPacket(command.receiver, command.cmdSet, command.cmdId, command.payload);
            socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
            Log.i(TAG, String.format(Locale.US, "Sent UDP 92ec %02x/%02x post-video heartbeat port=%d packet=%s",
                    command.cmdSet, command.cmdId, socket.getLocalPort(), toHex(packet)));
            sleepQuietly(12);
        }
    }

    private void sendUdp92EcStartupSetupCommands(DatagramSocket socket, InetAddress camera) throws IOException {
        for (UdpDumlCommand command : UDP_92EC_STARTUP_SETUP_COMMANDS) {
            byte[] packet = buildUdp92EcDumlPacket(command.receiver, command.cmdSet, command.cmdId, command.payload);
            socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
            Log.i(TAG, String.format(Locale.US, "Sent UDP 92ec %02x/%02x startup setup port=%d packet=%s",
                    command.cmdSet, command.cmdId, socket.getLocalPort(), toHex(packet)));
            sendUdp92EcCameraCounterAck(socket, camera);
            sleepQuietly(12);
        }
        byte[] previewPulse = buildUdp92EcDumlPacketWithFlags(0x28, 0x00, 0x88, hex("1a00000000"), 0x80);
        socket.send(new DatagramPacket(previewPulse, previewPulse.length, camera, CAMERA_PORT));
        Log.i(TAG, "Sent UDP 92ec startup preview pulse port=" + socket.getLocalPort()
                + " packet=" + toHex(previewPulse));
    }

    private void sendUdp92EcMarkerPacket(DatagramSocket socket, InetAddress camera, int fieldAt16, int fieldAt24,
            String label) throws IOException {
        byte[] packet = buildUdp92EcMarkerPacket(fieldAt16, fieldAt24);
        sendUdp92EcRawPacket(socket, camera, packet, "marker " + label);
    }

    private void sendUdp92EcRawPacket(DatagramSocket socket, InetAddress camera, byte[] packet, String label)
            throws IOException {
        socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
        Log.i(TAG, "Sent UDP 92ec " + label + " port=" + socket.getLocalPort()
                + " packet=" + toHex(packet));
        sleepQuietly(12);
    }

    private List<UdpDumlCommand> udp92EcBootstrapCommands() {
        List<UdpDumlCommand> commands = new ArrayList<>();
        for (int i = 0; i < UDP_92EC_BOOTSTRAP_PROPERTY_NAMES.length; i++) {
            int requestId = UDP_92EC_PROPERTY_REQUEST_ID_START + i;
            byte[] payload = buildUdp92EcPropertyRequestPayload(requestId, UDP_92EC_BOOTSTRAP_PROPERTY_NAMES[i]);
            commands.add(new UdpDumlCommand(0x28, 0x00, 0x99, payload));
        }
        Log.i(TAG, "Using synthesized Mimo 92ec property sweep count=" + commands.size());
        return commands;
    }

    private boolean isPropertyCommand(UdpDumlCommand command, String propertyName) {
        if (command.cmdSet != 0x00 || command.cmdId != 0x99 || command.payload.length < 19) {
            return false;
        }
        byte[] nameBytes = propertyName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int offset = 15;
        if (command.payload.length < offset + nameBytes.length) {
            return false;
        }
        for (int i = 0; i < nameBytes.length; i++) {
            if (command.payload[offset + i] != nameBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private void sendUdpDumlPacket(DatagramSocket socket, InetAddress camera, int receiver, int cmdSet, int cmdId,
            byte[] payload) throws IOException {
        byte[] packet = buildUdpDumlPacket(receiver, cmdSet, cmdId, payload);
        socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
        Log.i(TAG, String.format(Locale.US, "Sent UDP %02x/%02x liveview start port=%d packet=%s",
                cmdSet, cmdId, socket.getLocalPort(), toHex(packet)));
        sleepQuietly(75);
    }

    private void keepUdpLiveviewAlive(List<DatagramSocket> sockets, InetAddress camera,
            AtomicReference<Exception> receiveError) {
        int rounds = 0;
        while (running.get() && receiveError.get() == null) {
            sleepQuietly(rounds == 0 ? 120 : 1000);
            rounds++;
            try {
                for (DatagramSocket socket : sockets) {
                    if (socket.getLocalPort() == PREFERRED_92EC_PORT) {
                        sendUdp92EcLiveViewAcks(socket, camera, rounds);
                    } else if (rounds == 1 || rounds % 10 == 1) {
                        sendUdpLiveviewRefresh(socket, camera);
                    }
                }
                maybeSendSoftMediaResume(sockets, camera, rounds);
                if (rounds <= 5 || rounds % 10 == 0) {
                    Log.i(TAG, "Sent liveview keepalive round=" + rounds);
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException exc) {
                if (running.get()) {
                    receiveError.compareAndSet(null, exc);
                }
                return;
            }
        }
    }

    private void rememberMediaProgress(long video92EcPackets, long h264Bytes) {
        lastVideoProgressAtMs = System.currentTimeMillis();
        latestVideo92EcPackets = video92EcPackets;
        latestH264Bytes = h264Bytes;
        latestRenderedFrames = renderedFrames;
    }

    private void maybeSendSoftMediaResume(List<DatagramSocket> sockets, InetAddress camera, int rounds)
            throws IOException {
        long lastProgress = lastVideoProgressAtMs;
        if (lastProgress <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long stalledMs = now - lastProgress;
        if (stalledMs >= MEDIA_SESSION_RESTART_GRACE_MS
                && now - lastMediaSessionRestartAtMs >= MEDIA_SESSION_RESTART_COOLDOWN_MS) {
            lastMediaSessionRestartAtMs = now;
            mediaSessionRestartRequested = true;
            running.set(false);
            Log.w(TAG, "Requesting preview session restart round=" + rounds
                    + " stalledMs=" + stalledMs
                    + " video92ec=" + latestVideo92EcPackets
                    + " h264Bytes=" + latestH264Bytes
                    + " queued=" + queuedNalUnits
                    + " rendered=" + latestRenderedFrames);
            return;
        }
        if (stalledMs >= MEDIA_REARM_GRACE_MS && now - lastMediaRearmAtMs >= MEDIA_REARM_COOLDOWN_MS) {
            DatagramSocket primarySocket = socketForPort(sockets, PREFERRED_92EC_PORT);
            lastMediaRearmAtMs = now;
            Log.w(TAG, "Media session rearm round=" + rounds
                    + " stalledMs=" + stalledMs
                    + " video92ec=" + latestVideo92EcPackets
                    + " h264Bytes=" + latestH264Bytes
                    + " queued=" + queuedNalUnits
                    + " rendered=" + latestRenderedFrames);
            sendUdp92EcMediaRearmBurst(primarySocket, camera);
            return;
        }
        if (stalledMs < MEDIA_SOFT_RESUME_GRACE_MS
                || now - lastSoftMediaResumeAtMs < MEDIA_SOFT_RESUME_COOLDOWN_MS) {
            return;
        }
        DatagramSocket primarySocket = null;
        DatagramSocket refreshSocket = null;
        for (DatagramSocket socket : sockets) {
            if (socket.getLocalPort() == PREFERRED_92EC_PORT) {
                primarySocket = socket;
            } else {
                refreshSocket = socket;
            }
        }
        if (primarySocket == null) {
            return;
        }

        lastSoftMediaResumeAtMs = now;
        Log.w(TAG, "Soft media resume round=" + rounds
                + " stalledMs=" + stalledMs
                + " video92ec=" + latestVideo92EcPackets
                + " h264Bytes=" + latestH264Bytes
                + " queued=" + queuedNalUnits
                + " rendered=" + latestRenderedFrames);
        sendUdp92EcSoftResumeBurst(primarySocket, camera);
        if (refreshSocket != null) {
            sendUdpLiveviewRefresh(refreshSocket, camera);
        }
    }

    private void sendUdp92EcLiveViewAcks(DatagramSocket socket, InetAddress camera, int rounds) throws IOException {
        if (rounds == 1) {
            byte[] previewReady = buildUdp92EcDumlPacket(0x28, 0x00, 0x88, hex("1700002300415050000000000002"));
            socket.send(new DatagramPacket(previewReady, previewReady.length, camera, CAMERA_PORT));
            byte[] appState = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x81, appStatePayload(), 0x80);
            socket.send(new DatagramPacket(appState, appState.length, camera, CAMERA_PORT));
            byte[] appStateTrigger = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x82, new byte[] { 0x00 }, 0x80);
            socket.send(new DatagramPacket(appStateTrigger, appStateTrigger.length, camera, CAMERA_PORT));
            byte[] trigger = buildUdp92EcDumlPacket(0x48, 0x00, 0x82, hex("067b"));
            socket.send(new DatagramPacket(trigger, trigger.length, camera, CAMERA_PORT));
        }
        sendUdp92EcCameraCounterAck(socket, camera);
        if (rounds == 1) {
            sendUdp92EcMaintenanceBurst(socket, camera, rounds);
        } else if (rounds % UDP_92EC_LIGHT_SUSTAIN_INTERVAL_ROUNDS == 0) {
            sendUdp92EcLightSustainBurst(socket, camera, rounds);
        } else {
            sendUdp92EcReceiverKeepalive(socket, camera);
        }
    }

    private void sendUdp92EcMaintenanceBurst(DatagramSocket socket, InetAddress camera, int rounds)
            throws IOException {
        sendUdp92EcMaintenancePropertyRequest(socket, camera, rounds, 0, "camcap_style_filter_density");
        sendUdp92EcMaintenancePropertyRequest(socket, camera, rounds, 1, "camcap_events");
        for (int i = 0; i < 6; i++) {
            sendUdp92EcDumlCommand(socket, camera, UDP_92EC_MAINTENANCE_MODE_COMMANDS[i]);
        }
        sendUdp92EcReceiverKeepalive(socket, camera);
        byte[] previewPulse = buildUdp92EcDumlPacketWithFlags(0x28, 0x00, 0x88, hex("1a00000000"), 0x80);
        socket.send(new DatagramPacket(previewPulse, previewPulse.length, camera, CAMERA_PORT));
        sendUdp92EcReceiverKeepalive(socket, camera);
        for (int i = 6; i < 14; i++) {
            sendUdp92EcDumlCommand(socket, camera, UDP_92EC_MAINTENANCE_MODE_COMMANDS[i]);
        }
        byte[] appState = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x81, appStatePayload(), 0x80);
        socket.send(new DatagramPacket(appState, appState.length, camera, CAMERA_PORT));
        byte[] appStateTrigger = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x82, new byte[] { 0x00 }, 0x80);
        socket.send(new DatagramPacket(appStateTrigger, appStateTrigger.length, camera, CAMERA_PORT));
        for (int i = 14; i < UDP_92EC_MAINTENANCE_MODE_COMMANDS.length; i++) {
            sendUdp92EcDumlCommand(socket, camera, UDP_92EC_MAINTENANCE_MODE_COMMANDS[i]);
        }
        Log.i(TAG, "Sent UDP 92ec maintenance burst round=" + rounds);
    }

    private void sendUdp92EcLightSustainBurst(DatagramSocket socket, InetAddress camera, int rounds)
            throws IOException {
        sendUdp92EcReceiverKeepalive(socket, camera);
        sendUdp92EcCameraCounterAck(socket, camera);
        byte[] appState = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x81, appStatePayload(), 0x80);
        socket.send(new DatagramPacket(appState, appState.length, camera, CAMERA_PORT));
        byte[] appStateTrigger = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x82, new byte[] { 0x00 }, 0x80);
        socket.send(new DatagramPacket(appStateTrigger, appStateTrigger.length, camera, CAMERA_PORT));
        byte[] previewPulse = buildUdp92EcDumlPacketWithFlags(0x28, 0x00, 0x88, hex("1a00000000"), 0x80);
        socket.send(new DatagramPacket(previewPulse, previewPulse.length, camera, CAMERA_PORT));
        Log.i(TAG, "Sent UDP 92ec light sustain burst round=" + rounds);
    }

    private void sendUdp92EcSoftResumeBurst(DatagramSocket socket, InetAddress camera) throws IOException {
        sendUdp92EcCameraCounterAck(socket, camera);
        byte[] previewReady = buildUdp92EcDumlPacket(0x28, 0x00, 0x88, hex("1700002300415050000000000002"));
        socket.send(new DatagramPacket(previewReady, previewReady.length, camera, CAMERA_PORT));
        byte[] previewPulse = buildUdp92EcDumlPacketWithFlags(0x28, 0x00, 0x88, hex("1a00000000"), 0x80);
        socket.send(new DatagramPacket(previewPulse, previewPulse.length, camera, CAMERA_PORT));
        byte[] appState = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x81, appStatePayload(), 0x80);
        socket.send(new DatagramPacket(appState, appState.length, camera, CAMERA_PORT));
        byte[] appStateTrigger = buildUdp92EcDumlPacketWithFlags(0x48, 0x00, 0x82, new byte[] { 0x00 }, 0x80);
        socket.send(new DatagramPacket(appStateTrigger, appStateTrigger.length, camera, CAMERA_PORT));
        sendUdp92EcReceiverKeepalive(socket, camera);
        Log.i(TAG, "Sent UDP 92ec soft media resume burst port=" + socket.getLocalPort());
    }

    private void sendUdp92EcMediaRearmBurst(DatagramSocket socket, InetAddress camera) throws IOException {
        sendUdp92EcCameraCounterAck(socket, camera);
        sendUdp92EcPreviewSessionCommands(socket, camera);
        sendUdp92EcPostVideoHeartbeatCommands(socket, camera);
        sendUdp92EcStartupSetupCommands(socket, camera);
        sendUdp92EcMaintenanceBurst(socket, camera, -1);
        Log.i(TAG, "Sent UDP 92ec media session rearm burst port=" + socket.getLocalPort());
    }

    private void sendUdp92EcDumlCommand(DatagramSocket socket, InetAddress camera, UdpDumlCommand command)
            throws IOException {
        byte[] packet = buildUdp92EcDumlPacket(command.receiver, command.cmdSet, command.cmdId, command.payload);
        socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
    }

    private void sendUdp92EcMaintenancePropertyRequest(DatagramSocket socket, InetAddress camera, int rounds,
            int offset, String propertyName) throws IOException {
        int requestId = UDP_92EC_MAINTENANCE_PROPERTY_REQUEST_ID_START + (rounds * 2) + offset;
        byte[] payload = buildUdp92EcPropertyRequestPayload(requestId & 0xffff, propertyName);
        byte[] packet = buildUdp92EcDumlPacket(0x28, 0x00, 0x99, payload);
        socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
    }

    private void sendUdp92EcReceiverKeepalive(DatagramSocket socket, InetAddress camera) throws IOException {
        byte[] packet = buildUdp92EcDumlPacket(0x01, 0x00, 0x4f, hex("040000000000000000"));
        socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
    }

    private byte[] appStatePayload() {
        return hex("00415050000000000000000000000000000000000000000000000000000000000000020000000000000208000000000000000000000000000000000000000000");
    }

    private void sendUdp92EcCameraCounterAck(DatagramSocket socket, InetAddress camera) throws IOException {
        byte[] packet = buildUdp92EcCameraCounterAckPacket();
        socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
    }

    private void sendUdp92EcCameraCounterAckQuietly(DatagramSocket socket, InetAddress camera) {
        if (camera == null) {
            return;
        }
        try {
            sendUdp92EcCameraCounterAck(socket, camera);
        } catch (IOException exc) {
            Log.w(TAG, "Failed sending UDP 92ec camera counter ack", exc);
        }
    }

    private void answerUdp92EcCameraRequest(DatagramSocket socket, byte[] packet) {
        if (packet.length < 33 || (packet[6] & 0xff) != 0x01) {
            return;
        }
        for (int offset = 20; offset + 13 <= packet.length; offset++) {
            if (packet[offset] != 0x55) {
                continue;
            }
            int frameLength = (packet[offset + 1] & 0xff) | ((packet[offset + 2] & 0x03) << 8);
            if (frameLength < 13 || offset + frameLength > packet.length) {
                continue;
            }
            int sender = packet[offset + 4] & 0xff;
            int receiver = packet[offset + 5] & 0xff;
            int sequence = ((packet[offset + 6] & 0xff) << 8) | (packet[offset + 7] & 0xff);
            int flags = packet[offset + 8] & 0xff;
            int cmdSet = packet[offset + 9] & 0xff;
            int cmdId = packet[offset + 10] & 0xff;
            if (receiver != BLE_APP_ADDRESS || flags != 0x40 || !shouldAnswerBleCameraRequest(cmdSet, cmdId)) {
                continue;
            }
            byte[] payload = copyOfRange(packet, offset + 11, offset + frameLength - 2);
            try {
                byte[] response = buildUdp92EcCameraRequestResponsePacket(sender, sequence, cmdSet, cmdId, payload);
                InetAddress address = packetSourceAddress(socket);
                socket.send(new DatagramPacket(response, response.length, address, CAMERA_PORT));
                if (udp92EcCameraRequestLogSamples < 30) {
                    udp92EcCameraRequestLogSamples++;
                    Log.i(TAG, String.format(Locale.US,
                            "Answered UDP 92ec camera request %02x/%02x sender=%02x seq=%04x offset=%d payloadLength=%d",
                            cmdSet, cmdId, sender, sequence, offset, payload.length));
                }
            } catch (IOException exc) {
                Log.w(TAG, "Failed answering UDP 92ec camera request", exc);
            }
            offset += frameLength - 1;
        }
    }

    private InetAddress packetSourceAddress(DatagramSocket socket) throws IOException {
        InetAddress address = socket.getInetAddress();
        return address != null ? address : InetAddress.getByName(configuredCameraHost());
    }

    private void sendUdpLiveviewRefresh(DatagramSocket socket, InetAddress camera) throws IOException {
        for (UdpDumlCommand command : UDP_LIVEVIEW_START_COMMANDS) {
            byte[] packet = buildUdpDumlPacket(command.receiver, command.cmdSet, command.cmdId, command.payload);
            socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
            sleepQuietly(12);
        }
        byte[] packet = buildUdpDumlPacket(0x41, 0x09, 0xa8, MIMO_LIVEVIEW_TRANSMIT_CTRL_PAYLOAD);
        socket.send(new DatagramPacket(packet, packet.length, camera, CAMERA_PORT));
        Log.i(TAG, "Sent UDP liveview refresh port=" + socket.getLocalPort());
    }

    private byte[] buildUdpDumlPacket(int receiver, int cmdSet, int cmdId, byte[] payload) {
        byte[] frame = buildUdpDumlFrame(receiver, udpDumlSequence++ & 0xffff, 0x40, cmdSet, cmdId, payload);
        int counter = udpTransportCounter & 0xffff;
        int previousCounter = udpPreviousTransportCounter & 0xffff;
        byte[] packet = new byte[20 + frame.length];
        int totalLength = packet.length;
        packet[0] = (byte) (totalLength & 0xff);
        packet[1] = (byte) (0x80 | ((totalLength >> 8) & 0x3f));
        packet[2] = 0x5f;
        packet[3] = (byte) 0xc1;
        writeLittleEndianShort(packet, 4, counter);
        packet[6] = 0x05;
        packet[7] = (byte) xor8(packet, 0, 7);
        writeLittleEndianShort(packet, 8, previousCounter);
        writeLittleEndianShort(packet, 10, counter);
        writeLittleEndianInt(packet, 12, 0);
        writeLittleEndianInt(packet, 16, udpControlSequence++ & 0xffff);
        System.arraycopy(frame, 0, packet, 20, frame.length);
        udpPreviousTransportCounter = counter;
        udpTransportCounter = (udpTransportCounter + 8) & 0xffff;
        return packet;
    }

    private byte[] buildUdp92EcDumlPacket(int receiver, int cmdSet, int cmdId, byte[] payload) {
        byte[] frame = buildUdpDumlFrame(receiver, nextUdp92EcDumlSequence(), 0x40, cmdSet, cmdId, payload);
        byte[] packet = buildUdp92EcPacket(frame, udp92EcSequence++);
        return packet;
    }

    private byte[] buildUdp92EcDumlPacketWithFlags(int receiver, int cmdSet, int cmdId, byte[] payload, int flags) {
        byte[] frame = buildUdpDumlFrame(receiver, nextUdp92EcDumlSequence(), flags, cmdSet, cmdId, payload);
        return buildUdp92EcPacket(frame, udp92EcSequence++);
    }

    private byte[] buildUdp92EcSessionDumlPacket(int receiver, int cmdSet, int cmdId, byte[] payload) {
        byte[] frame = buildUdpDumlFrame(receiver, nextUdp92EcSessionSequence(), 0x40, cmdSet, cmdId, payload);
        return buildUdp92EcPacket(frame, udp92EcSequence++);
    }

    private byte[] buildUdp92EcDumlAckPacket(int receiver, int cmdSet, int cmdId) {
        byte[] frame = buildUdpDumlFrame(receiver, nextUdp92EcAckSequence(), 0xc0, cmdSet, cmdId,
                new byte[] { 0x00 });
        return buildUdp92EcPacket(frame, udp92EcSequence++);
    }

    private byte[] buildUdp92EcDumlResponsePacket(int receiver, int sequence, int cmdSet, int cmdId, byte[] payload) {
        byte[] frame = buildUdpDumlFrame(receiver, sequence, 0xc0, cmdSet, cmdId, payload);
        return buildUdp92EcPacket(frame, udp92EcSequence++);
    }

    private byte[] buildUdp92EcCameraRequestResponsePacket(int receiver, int sequence, int cmdSet, int cmdId,
            byte[] requestPayload) {
        if (cmdSet == 0x00 && (cmdId == 0x99 || cmdId == 0x74)) {
            return buildUdp92EcDumlResponsePacket(receiver, sequence, cmdSet, cmdId, new byte[] { 0x00 });
        }
        if (cmdSet == 0x00 && cmdId == 0x81) {
            return buildUdp92EcDumlFramePacketWithFlags(receiver, sequence, cmdSet, cmdId, appStatePayload(), 0x80);
        }
        if (cmdSet == 0x00 && cmdId == 0x82) {
            return buildUdp92EcDumlFramePacketWithFlags(receiver, sequence, cmdSet, cmdId, new byte[] { 0x00 }, 0x80);
        }
        if (cmdSet == 0x00 && cmdId == 0x88) {
            return buildUdp92EcDumlFramePacketWithFlags(receiver, sequence, cmdSet, cmdId, hex("1a00000000"), 0x80);
        }
        return buildUdp92EcDumlResponsePacket(receiver, sequence, cmdSet, cmdId, requestPayload);
    }

    private byte[] buildUdp92EcDumlFramePacketWithFlags(int receiver, int sequence, int cmdSet, int cmdId,
            byte[] payload, int flags) {
        byte[] frame = buildUdpDumlFrame(receiver, sequence, flags, cmdSet, cmdId, payload);
        return buildUdp92EcPacket(frame, udp92EcSequence++);
    }

    private int nextUdp92EcDumlSequence() {
        int sequence = udp92EcDumlSequence & 0xffff;
        udp92EcDumlSequence = (udp92EcDumlSequence + 0x0100) & 0xffff;
        return sequence;
    }

    private int nextUdp92EcAckSequence() {
        int sequence = udp92EcAckSequence & 0xffff;
        udp92EcAckSequence = (udp92EcAckSequence + 0x0100) & 0xffff;
        return sequence;
    }

    private int nextUdp92EcSessionSequence() {
        int[] sequences = { 0x279f, 0x289f, 0x2a9f };
        if (udp92EcSessionSequenceIndex < sequences.length) {
            return sequences[udp92EcSessionSequenceIndex++];
        }
        return (0x2b9f + ((udp92EcSessionSequenceIndex++ - sequences.length) * 0x0100)) & 0xffff;
    }

    private byte[] buildUdp92EcPacket(byte[] body, int controlSequence) {
        int counter = udp92EcCounter & 0xffff;
        int previousCounter = udp92EcPreviousCounter & 0xffff;
        byte[] packet = new byte[20 + body.length];
        int totalLength = packet.length;
        packet[0] = (byte) (totalLength & 0xff);
        packet[1] = (byte) (0x80 | ((totalLength >> 8) & 0x3f));
        packet[2] = (byte) 0x92;
        packet[3] = (byte) 0xec;
        writeLittleEndianShort(packet, 4, counter);
        packet[6] = 0x05;
        packet[7] = (byte) xor8(packet, 0, 7);
        writeLittleEndianShort(packet, 8, previousCounter);
        writeLittleEndianShort(packet, 10, counter);
        writeLittleEndianInt(packet, 12, 0);
        writeLittleEndianInt(packet, 16, controlSequence & 0xffff);
        System.arraycopy(body, 0, packet, 20, body.length);
        previousUdp92EcCommandCounter = previousCounter;
        lastUdp92EcCounter = counter;
        udp92EcPreviousCounter = counter;
        udp92EcCounter = (udp92EcCounter + 8) & 0xffff;
        return packet;
    }

    private byte[] buildUdp92EcCameraCounterAckPacket() {
        int mediaCounter = latestCamera92EcMediaCounter & 0xffff;
        int statusCounter = latestCamera92EcStatusCounter & 0xffff;
        int previousCommandCounter = previousUdp92EcCommandCounter & 0xffff;
        int commandCounter = lastUdp92EcCounter & 0xffff;
        byte[] packet = new byte[34];
        packet[0] = 0x22;
        packet[1] = (byte) 0x80;
        packet[2] = (byte) 0x92;
        packet[3] = (byte) 0xec;
        packet[6] = 0x04;
        packet[7] = (byte) xor8(packet, 0, 7);
        writeLittleEndianShort(packet, 8, statusCounter);
        writeLittleEndianShort(packet, 10, statusCounter);
        writeLittleEndianShort(packet, 16, mediaCounter);
        writeLittleEndianShort(packet, 18, mediaCounter);
        writeLittleEndianShort(packet, 24, previousCommandCounter);
        writeLittleEndianShort(packet, 26, commandCounter);
        return packet;
    }

    private byte[] buildUdp92EcMarkerPacket(int fieldAt16, int fieldAt24) {
        byte[] packet = new byte[34];
        packet[0] = 0x22;
        packet[1] = (byte) 0x80;
        packet[2] = (byte) 0x92;
        packet[3] = (byte) 0xec;
        packet[6] = 0x04;
        packet[7] = (byte) xor8(packet, 0, 7);
        writeLittleEndianShort(packet, 8, 0x6490);
        writeLittleEndianShort(packet, 10, 0x6490);
        writeLittleEndianShort(packet, 16, fieldAt16);
        writeLittleEndianShort(packet, 18, fieldAt16);
        writeLittleEndianShort(packet, 24, fieldAt24);
        writeLittleEndianShort(packet, 26, lastUdp92EcCounter);
        return packet;
    }

    private static void writeLittleEndianShort(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xff);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xff);
    }

    private byte[] buildUdpDumlFrame(int receiver, int sequence, int flags, int cmdSet, int cmdId, byte[] payload) {
        int length = 13 + payload.length;
        byte[] frame = new byte[length];
        frame[0] = 0x55;
        frame[1] = (byte) (length & 0xff);
        frame[2] = (byte) (((length >> 8) & 0x03) | 0x04);
        frame[3] = (byte) crc8Classic(frame, 0, 3);
        frame[4] = (byte) BLE_APP_ADDRESS;
        frame[5] = (byte) receiver;
        frame[6] = (byte) ((sequence >> 8) & 0xff);
        frame[7] = (byte) (sequence & 0xff);
        frame[8] = (byte) flags;
        frame[9] = (byte) cmdSet;
        frame[10] = (byte) cmdId;
        System.arraycopy(payload, 0, frame, 11, payload.length);
        int checksum = crc16Mimo(frame, 0, length - 2);
        frame[length - 2] = (byte) (checksum & 0xff);
        frame[length - 1] = (byte) ((checksum >> 8) & 0xff);
        return frame;
    }

    private static void writeLittleEndianInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xff);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xff);
        buffer[offset + 2] = (byte) ((value >> 16) & 0xff);
        buffer[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private DatagramSocket openPreviewSocket() throws IOException {
        IOException lastError = null;
        for (int port : localPortCandidates()) {
            try {
                return openPreviewSocket(port);
            } catch (IOException exc) {
                lastError = exc;
                Log.w(TAG, "Failed to bind UDP port " + port, exc);
                if (forcedLocalPort > 0 && port == forcedLocalPort) {
                    throw new IOException("Mimo still owns UDP port " + forcedLocalPort, exc);
                }
            }
        }
        throw lastError == null ? new IOException("No UDP port candidates") : lastError;
    }

    private DatagramSocket openPreviewSocket(int port) throws IOException {
        DatagramSocket candidate = new DatagramSocket(null);
        candidate.setReuseAddress(true);
        candidate.bind(new InetSocketAddress(port));
        candidate.setSoTimeout(200);
        candidate.setReceiveBufferSize(UDP_RECEIVE_BUFFER_SIZE);
        if (boundNetwork != null) {
            boundNetwork.bindSocket(candidate);
            Log.i(TAG, "Bound UDP socket to " + boundNetwork);
        }
        Log.i(TAG, "Opened UDP port " + candidate.getLocalPort()
                + " receiveBuffer=" + candidate.getReceiveBufferSize());
        return candidate;
    }

    private List<Integer> localPortCandidates() {
        Set<Integer> ports = new LinkedHashSet<>();
        if (forcedLocalPort > 0) {
            ports.add(forcedLocalPort);
        }
        for (int port : FALLBACK_LOCAL_PORTS) {
            ports.add(port);
        }
        Log.i(TAG, "UDP port candidates: " + ports);
        return new ArrayList<>(ports);
    }

    private List<Integer> discoverMimoUdpPorts() {
        List<Integer> ports = new ArrayList<>();
        readUdpProcFile("/proc/net/udp", ports);
        readUdpProcFile("/proc/net/udp6", ports);
        return ports;
    }

    private void readUdpProcFile(String path, List<Integer> ports) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openFile(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.trim().split("\\s+");
                if (columns.length < 10 || !String.valueOf(MIMO_UID).equals(columns[9])) {
                    continue;
                }
                String[] addressParts = columns[1].split(":");
                if (addressParts.length != 2) {
                    continue;
                }
                int port = Integer.parseInt(addressParts[1], 16);
                if (port > 1024 && port != CAMERA_PORT) {
                    ports.add(port);
                }
            }
        } catch (Exception exc) {
            Log.w(TAG, "Could not read " + path, exc);
        }
    }

    private InputStream openFile(String path) throws IOException {
        return new java.io.FileInputStream(path);
    }

    private int readLittleEndianInt(DataInputStream data) throws IOException {
        int b0 = data.readUnsignedByte();
        int b1 = data.readUnsignedByte();
        int b2 = data.readUnsignedByte();
        int b3 = data.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int xor8(byte[] data, int offset, int length) {
        int value = 0;
        for (int i = offset; i < offset + length; i++) {
            value ^= data[i] & 0xff;
        }
        return value & 0xff;
    }

    private static final class UdpDumlCommand {
        final int receiver;
        final int cmdSet;
        final int cmdId;
        final byte[] payload;

        UdpDumlCommand(int receiver, int cmdSet, int cmdId, byte[] payload) {
            this.receiver = receiver;
            this.cmdSet = cmdSet;
            this.cmdId = cmdId;
            this.payload = payload;
        }
    }

    private static final class SetupPacket {
        final int localPort;
        final byte[] payload;

        SetupPacket(int localPort, byte[] payload) {
            this.localPort = localPort;
            this.payload = payload;
        }
    }

    private static final class BleWrite {
        final byte[] frame;
        final String label;
        final Runnable onAccepted;

        BleWrite(byte[] frame, String label, Runnable onAccepted) {
            this.frame = frame;
            this.label = label;
            this.onAccepted = onAccepted;
        }
    }

    private static final class BitReader {
        private final byte[] data;
        private int bitOffset;

        BitReader(byte[] data) {
            this.data = data;
        }

        boolean readBit() {
            return readBits(1) != 0;
        }

        int readBits(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                if (bitOffset >= data.length * 8) {
                    return value;
                }
                value <<= 1;
                value |= (data[bitOffset / 8] >> (7 - (bitOffset % 8))) & 0x01;
                bitOffset++;
            }
            return value;
        }

        int readUnsignedExpGolomb() {
            int leadingZeroBits = 0;
            while (bitOffset < data.length * 8 && !readBit()) {
                leadingZeroBits++;
            }
            return (1 << leadingZeroBits) - 1 + readBits(leadingZeroBits);
        }

        int readSignedExpGolomb() {
            int value = readUnsignedExpGolomb();
            int sign = (value & 1) == 0 ? -1 : 1;
            return sign * ((value + 1) / 2);
        }
    }

    private void sendTcpControlActivation() {
        byte[] activation = hex("55110492021b299f400745000000009892");
        byte[] liveViewSubscribe = buildBleDumlWithSequence(
                0x08,
                0x9988,
                0x40,
                0x02,
                0x09,
                liveViewSubscribePayload(true));
        try (Socket control = new Socket()) {
            if (boundNetwork != null) {
                boundNetwork.bindSocket(control);
            }
            control.connect(new InetSocketAddress(configuredCameraHost(), CAMERA_TCP_CONTROL_PORT), 1200);
            control.setSoTimeout(300);
            control.getOutputStream().write(activation);
            control.getOutputStream().flush();
            Log.i(TAG, "Sent TCP 7001 activation " + toHex(activation));
            sleepQuietly(120);
            control.getOutputStream().write(liveViewSubscribe);
            control.getOutputStream().flush();
            Log.i(TAG, "Sent TCP 7001 live-view subscribe " + toHex(liveViewSubscribe));
        } catch (IOException exc) {
            Log.w(TAG, "TCP 7001 activation failed", exc);
        }
    }

    private void keepTcpControlAlive(AtomicReference<Exception> receiveError) {
        byte[] activation = hex("55110492021b299f400745000000009892");
        try (Socket control = new Socket()) {
            if (boundNetwork != null) {
                boundNetwork.bindSocket(control);
            }
            control.connect(new InetSocketAddress(configuredCameraHost(), CAMERA_TCP_CONTROL_PORT), 1200);
            control.setSoTimeout(300);
            control.getOutputStream().write(activation);
            control.getOutputStream().flush();
            Log.i(TAG, "Opened TCP 7001 keepalive channel " + toHex(activation));
            while (running.get() && receiveError.get() == null) {
                sleepQuietly(900);
                control.getOutputStream().write(0x00);
                control.getOutputStream().flush();
                Log.i(TAG, "Sent TCP 7001 heartbeat");
            }
        } catch (IOException exc) {
            if (running.get()) {
                Log.w(TAG, "TCP 7001 keepalive failed", exc);
            }
        }
    }

    private byte[] extractH264Candidate(byte[] packet) {
        int totalLength = djiTotalLength(packet);
        if (totalLength < 4 || totalLength > packet.length) {
            return new byte[0];
        }
        if (hasDjiMediaMagic(packet) && isDjiMediaPacket(packet)) {
            byte[] out = new byte[packet.length - DJI_MEDIA_STRIP];
            System.arraycopy(packet, DJI_MEDIA_STRIP, out, 0, out.length);
            return out;
        }
        byte[] dji92EcPayload = extractDji92EcVideoPayload(packet);
        if (dji92EcPayload.length > 0) {
            return dji92EcPayload;
        }
        if (hasDji92EcMagic(packet)) {
            return new byte[0];
        }
        int start = findBestAnnexStart(packet);
        if (start < 0 || totalLength < DJI_MEDIA_MIN_LENGTH) {
            return new byte[0];
        }
        byte[] out = new byte[packet.length - start];
        System.arraycopy(packet, start, out, 0, out.length);
        return out;
    }

    private boolean isDjiMediaPacket(byte[] packet) {
        return hasDjiTransportMagic(packet)
                && djiTotalLength(packet) >= DJI_MEDIA_MIN_LENGTH
                && packet.length > DJI_MEDIA_STRIP;
    }

    private boolean isDji92EcVideoPacket(byte[] packet) {
        return hasDji92EcMagic(packet)
                && djiTotalLength(packet) >= DJI_MEDIA_MIN_LENGTH
                && packet.length > DJI_92EC_MEDIA_STRIP
                && (packet[6] & 0xff) == 0x02
                && readLittleEndianInt(packet, 16) != 0;
    }

    private byte[] extractDji92EcVideoPayload(byte[] packet) {
        if (!isDji92EcVideoPacket(packet)) {
            return new byte[0];
        }
        int annexStart = findBestAnnexStart(packet);
        if (annexStart >= DJI_92EC_MEDIA_STRIP) {
            return copyOfRange(packet, annexStart, packet.length);
        }
        return copyOfRange(packet, DJI_92EC_MEDIA_STRIP, packet.length);
    }

    private boolean hasDjiMediaMagic(byte[] packet) {
        return packet.length >= 4 && packet[2] == 0x5f && (packet[3] & 0xff) == 0xc1;
    }

    private boolean hasDji92EcMagic(byte[] packet) {
        return packet.length >= 4 && (packet[2] & 0xff) == 0x92 && (packet[3] & 0xff) == 0xec;
    }

    private boolean hasDjiTransportMagic(byte[] packet) {
        return packet.length >= 4
                && (hasDjiMediaMagic(packet) || hasDji92EcMagic(packet));
    }

    private void rememberCamera92EcCounters(byte[] packet) {
        if (packet.length < 20 || !hasDji92EcMagic(packet)) {
            return;
        }
        int byte6 = packet[6] & 0xff;
        if (byte6 == 0x02 || byte6 == 0x03) {
            latestCamera92EcMediaCounter = readLittleEndianShort(packet, 10);
        } else if (byte6 == 0x01) {
            latestCamera92EcStatusCounter = readLittleEndianShort(packet, 10);
        }
    }

    private int djiTotalLength(byte[] packet) {
        if (packet.length < 2) {
            return -1;
        }
        return (packet[0] & 0xff) | ((packet[1] & 0x7f) << 8);
    }

    private int readLittleEndianInt(byte[] buffer, int offset) {
        if (offset < 0 || offset + 4 > buffer.length) {
            return 0;
        }
        return (buffer[offset] & 0xff)
                | ((buffer[offset + 1] & 0xff) << 8)
                | ((buffer[offset + 2] & 0xff) << 16)
                | ((buffer[offset + 3] & 0xff) << 24);
    }

    private int readLittleEndianShort(byte[] buffer, int offset) {
        if (offset < 0 || offset + 2 > buffer.length) {
            return 0;
        }
        return (buffer[offset] & 0xff) | ((buffer[offset + 1] & 0xff) << 8);
    }

    private String djiMagic(byte[] packet) {
        if (packet.length < 4) {
            return "none";
        }
        return String.format(Locale.US, "%02x%02x", packet[2] & 0xff, packet[3] & 0xff);
    }

    private int findBestAnnexStart(byte[] packet) {
        int[] likelyOffsets = { 20, 24, 28, 32, 36, 40, 44, 48 };
        for (int offset : likelyOffsets) {
            if (isValidAvcStartCodeAt(packet, offset)) {
                return offset;
            }
        }
        for (int i = 0; i + 4 < packet.length; i++) {
            if (isValidAvcStartCodeAt(packet, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isValidAvcStartCodeAt(byte[] data, int offset) {
        if (!hasStartCodeAt(data, offset)) {
            return false;
        }
        int header = nalHeaderOffset(data, offset, data.length - offset);
        if (header < 0 || (data[header] & 0x80) != 0) {
            return false;
        }
        int type = data[header] & 0x1f;
        return type == 1 || type == 5 || type == 6 || type == 7 || type == 8 || type == 9;
    }

    private boolean hasStartCodeAt(byte[] data, int offset) {
        if (offset < 0 || offset + 3 >= data.length) {
            return false;
        }
        if (data[offset] == 0 && data[offset + 1] == 0 && data[offset + 2] == 1) {
            return true;
        }
        return offset + 4 <= data.length
                && data[offset] == 0
                && data[offset + 1] == 0
                && data[offset + 2] == 0
                && data[offset + 3] == 1;
    }

    private void ensureDecoder() throws IOException {
        synchronized (decoderLock) {
            if (decoder != null) {
                return;
            }
            byte[] activeSps = spsNal;
            byte[] activePps = ppsNal;
            if (activeSps == null || activePps == null) {
                return;
            }
            int[] dimensions = avcDimensions(activeSps);
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", dimensions[0], dimensions[1]);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Math.max(8 * 1024 * 1024, dimensions[0] * dimensions[1]));
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(activeSps));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(activePps));
            MediaCodec codec = createConfiguredAvcDecoder(format);
            codec.start();
            decoder = codec;
            Log.i(TAG, "Configured AVC decoder " + codec.getName()
                    + " from " + (spsNal != null && ppsNal != null ? "stream" : "default")
                    + " SPS/PPS " + dimensions[0] + "x" + dimensions[1]
                    + " format=" + format);
        }
    }

    private MediaCodec createConfiguredAvcDecoder(MediaFormat format) throws IOException {
        String[] preferredDecoders = { "c2.android.avc.decoder", "OMX.google.h264.decoder" };
        for (String decoderName : preferredDecoders) {
            MediaCodec codec = null;
            try {
                codec = MediaCodec.createByCodecName(decoderName);
                codec.configure(format, surface, null, 0);
                Log.i(TAG, "Configured preferred AVC decoder " + decoderName);
                return codec;
            } catch (IllegalArgumentException | IOException exc) {
                if (codec != null) {
                    try {
                        codec.release();
                    } catch (Exception ignored) {
                    }
                }
                Log.i(TAG, "Preferred AVC decoder unavailable " + decoderName + ": " + exc.getMessage());
            }
        }
        MediaCodec codec = MediaCodec.createDecoderByType("video/avc");
        codec.configure(format, surface, null, 0);
        return codec;
    }

    private void feedCompleteNalUnits(ByteArrayOutputStream buffer, boolean flushTail) {
        byte[] bytes = buffer.toByteArray();
        List<Integer> starts = findStartCodes(bytes);
        if (starts.isEmpty() || (!flushTail && starts.size() < 2)) {
            return;
        }
        int completedCount = flushTail ? starts.size() : starts.size() - 1;
        int consumeUntil = flushTail ? bytes.length : starts.get(starts.size() - 1);
        for (int i = 0; i < completedCount; i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : bytes.length;
            processNalUnit(bytes, start, end - start);
        }
        buffer.reset();
        if (!flushTail) {
            buffer.write(bytes, consumeUntil, bytes.length - consumeUntil);
        }
    }

    private void flushStalledH264(ByteArrayOutputStream buffer, long lastH264At, long now) {
        if (buffer.size() == 0 || lastH264At == 0 || now - lastH264At < 250) {
            return;
        }
        feedCompleteNalUnits(buffer, true);
        flushPendingAccessUnit();
    }

    private void processNalUnit(byte[] data, int offset, int length) {
        int nalType = nalUnitType(data, offset, length);
        if (!isValidAvcStartCodeAt(data, offset)) {
            if (nalLogSamples < 40) {
                nalLogSamples++;
                Log.i(TAG, "Skipping invalid NAL type=" + nalType + " length=" + length
                        + " first32=" + toHex(copyOfRange(data, offset, Math.min(offset + length, offset + 32))));
            }
            return;
        }
        if (nalLogSamples < 40) {
            nalLogSamples++;
            Log.i(TAG, "NAL sample type=" + nalType + " length=" + length
                    + " first32=" + toHex(copyOfRange(data, offset, Math.min(offset + length, offset + 32))));
        }
        if (nalType == 7) {
            spsNal = copyNal(data, offset, length);
            Log.i(TAG, "Captured SPS length=" + length);
            return;
        } else if (nalType == 8) {
            ppsNal = copyNal(data, offset, length);
            Log.i(TAG, "Captured PPS length=" + length);
            return;
        } else if (nalType == 6) {
            return;
        }
        appendNalToAccessUnit(data, offset, length, nalType);
    }

    private void writeH264DiagnosticDump(byte[] h264) {
        if (h264DiagnosticDumpBytes >= H264_DIAGNOSTIC_DUMP_LIMIT || h264.length == 0) {
            return;
        }
        int writeLength = Math.min(h264.length, H264_DIAGNOSTIC_DUMP_LIMIT - h264DiagnosticDumpBytes);
        try (FileOutputStream out = new FileOutputStream(getCacheDir() + "/osmo-preview.h264", h264DiagnosticDumpBytes != 0)) {
            out.write(h264, 0, writeLength);
            h264DiagnosticDumpBytes += writeLength;
            if (h264DiagnosticDumpBytes == writeLength || h264DiagnosticDumpBytes >= H264_DIAGNOSTIC_DUMP_LIMIT) {
                Log.i(TAG, "H264 diagnostic dump bytes=" + h264DiagnosticDumpBytes);
            }
        } catch (IOException exc) {
            Log.w(TAG, "H264 diagnostic dump failed", exc);
            h264DiagnosticDumpBytes = H264_DIAGNOSTIC_DUMP_LIMIT;
        }
    }

    private void appendNalToAccessUnit(byte[] data, int offset, int length, int nalType) {
        boolean isVcl = nalType == 1 || nalType == 5;
        if (nalType == 9) {
            flushPendingAccessUnit();
            return;
        }
        boolean startsNewAccessUnit = nalType == 9 || (isVcl && pendingAccessUnitHasVcl);
        if (startsNewAccessUnit) {
            flushPendingAccessUnit();
        }
        pendingAccessUnit.write(data, offset, length);
        if (isVcl) {
            pendingAccessUnitHasVcl = true;
        }
        if (nalType == 5) {
            pendingAccessUnitHasIdr = true;
        }
        if (nalType == 9) {
            flushPendingAccessUnit();
        }
    }

    private void flushPendingAccessUnit() {
        if (pendingAccessUnit.size() == 0) {
            pendingAccessUnitHasVcl = false;
            pendingAccessUnitHasIdr = false;
            return;
        }
        try {
            ensureDecoder();
        } catch (IOException exc) {
            Log.e(TAG, "Decoder init failed", exc);
            postStatus("Decoder init failed: " + exc.getMessage());
            pendingAccessUnit.reset();
            pendingAccessUnitHasVcl = false;
            pendingAccessUnitHasIdr = false;
            return;
        }
        if (decoder != null && pendingAccessUnitHasVcl) {
            byte[] accessUnit = pendingAccessUnit.toByteArray();
            queueDecoderInput(accessUnit, 0, accessUnit.length, pendingAccessUnitHasIdr);
        }
        pendingAccessUnit.reset();
        pendingAccessUnitHasVcl = false;
        pendingAccessUnitHasIdr = false;
    }

    private int nalUnitType(byte[] data, int offset, int length) {
        int header = nalHeaderOffset(data, offset, length);
        if (header < 0) {
            return -1;
        }
        return data[header] & 0x1f;
    }

    private int nalHeaderOffset(byte[] data, int offset, int length) {
        int header = offset + startCodeLength(data, offset, length);
        if (header >= offset + length) {
            return -1;
        }
        return header;
    }

    private byte[] copyNal(byte[] data, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(data, offset, copy, 0, length);
        return copy;
    }

    private int startCodeLength(byte[] data, int offset, int length) {
        if (length >= 4
                && data[offset] == 0
                && data[offset + 1] == 0
                && data[offset + 2] == 0
                && data[offset + 3] == 1) {
            return 4;
        }
        if (length >= 3
                && data[offset] == 0
                && data[offset + 1] == 0
                && data[offset + 2] == 1) {
            return 3;
        }
        return 0;
    }

    private List<Integer> findStartCodes(byte[] bytes) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i + 4 < bytes.length; i++) {
            if (bytes[i] == 0 && bytes[i + 1] == 0 && bytes[i + 2] == 1) {
                starts.add(i);
                i += 2;
            } else if (bytes[i] == 0 && bytes[i + 1] == 0 && bytes[i + 2] == 0 && bytes[i + 3] == 1) {
                starts.add(i);
                i += 3;
            }
        }
        return starts;
    }

    private int[] avcDimensions(byte[] spsWithStartCode) {
        try {
            int header = nalHeaderOffset(spsWithStartCode, 0, spsWithStartCode.length);
            if (header < 0 || (spsWithStartCode[header] & 0x1f) != 7) {
                return new int[] { 2048, 1024 };
            }
            byte[] rbsp = avcRbsp(spsWithStartCode, header + 1, spsWithStartCode.length);
            BitReader bits = new BitReader(rbsp);
            bits.readBits(8); // profile_idc
            bits.readBits(8); // constraint flags + reserved
            bits.readBits(8); // level_idc
            bits.readUnsignedExpGolomb(); // seq_parameter_set_id
            int chromaFormatIdc = 1;
            if (isHighProfile(spsWithStartCode[header + 1] & 0xff)) {
                chromaFormatIdc = bits.readUnsignedExpGolomb();
                if (chromaFormatIdc == 3) {
                    bits.readBit();
                }
                bits.readUnsignedExpGolomb();
                bits.readUnsignedExpGolomb();
                bits.readBit();
                if (bits.readBit()) {
                    int matrixCount = chromaFormatIdc == 3 ? 12 : 8;
                    for (int i = 0; i < matrixCount; i++) {
                        if (bits.readBit()) {
                            skipScalingList(bits, i < 6 ? 16 : 64);
                        }
                    }
                }
            }
            bits.readUnsignedExpGolomb(); // log2_max_frame_num_minus4
            int picOrderCntType = bits.readUnsignedExpGolomb();
            if (picOrderCntType == 0) {
                bits.readUnsignedExpGolomb();
            } else if (picOrderCntType == 1) {
                bits.readBit();
                bits.readSignedExpGolomb();
                bits.readSignedExpGolomb();
                int cycle = bits.readUnsignedExpGolomb();
                for (int i = 0; i < cycle; i++) {
                    bits.readSignedExpGolomb();
                }
            }
            bits.readUnsignedExpGolomb(); // max_num_ref_frames
            bits.readBit(); // gaps_in_frame_num_value_allowed_flag
            int widthInMbsMinus1 = bits.readUnsignedExpGolomb();
            int heightInMapUnitsMinus1 = bits.readUnsignedExpGolomb();
            boolean frameMbsOnly = bits.readBit();
            if (!frameMbsOnly) {
                bits.readBit();
            }
            bits.readBit(); // direct_8x8_inference_flag
            int cropLeft = 0;
            int cropRight = 0;
            int cropTop = 0;
            int cropBottom = 0;
            if (bits.readBit()) {
                cropLeft = bits.readUnsignedExpGolomb();
                cropRight = bits.readUnsignedExpGolomb();
                cropTop = bits.readUnsignedExpGolomb();
                cropBottom = bits.readUnsignedExpGolomb();
            }
            int width = (widthInMbsMinus1 + 1) * 16;
            int height = (heightInMapUnitsMinus1 + 1) * 16 * (frameMbsOnly ? 1 : 2);
            int cropUnitX = chromaFormatIdc == 0 ? 1 : 2;
            int cropUnitY = chromaFormatIdc == 0 ? (frameMbsOnly ? 1 : 2) : 2 * (frameMbsOnly ? 1 : 2);
            width -= (cropLeft + cropRight) * cropUnitX;
            height -= (cropTop + cropBottom) * cropUnitY;
            if (width <= 0 || height <= 0) {
                return new int[] { 2048, 1024 };
            }
            return new int[] { width, height };
        } catch (Exception exc) {
            Log.w(TAG, "Failed to parse SPS dimensions", exc);
            return new int[] { 2048, 1024 };
        }
    }

    private static boolean isHighProfile(int profileIdc) {
        return profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
                || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
                || profileIdc == 128 || profileIdc == 138 || profileIdc == 144;
    }

    private static byte[] avcRbsp(byte[] data, int start, int end) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(end - start);
        int zeroCount = 0;
        for (int i = start; i < end; i++) {
            int value = data[i] & 0xff;
            if (zeroCount == 2 && value == 0x03) {
                zeroCount = 0;
                continue;
            }
            out.write(value);
            zeroCount = value == 0 ? zeroCount + 1 : 0;
        }
        return out.toByteArray();
    }

    private static void skipScalingList(BitReader bits, int size) {
        int lastScale = 8;
        int nextScale = 8;
        for (int i = 0; i < size; i++) {
            if (nextScale != 0) {
                nextScale = (lastScale + bits.readSignedExpGolomb() + 256) % 256;
            }
            lastScale = nextScale == 0 ? lastScale : nextScale;
        }
    }

    private void queueDecoderInput(byte[] data, int offset, int length, boolean keyFrame) {
        synchronized (decoderLock) {
            if (decoder == null || length <= 0) {
                return;
            }
            try {
                drainDecoderOutputLocked(0);
                int inputIndex = decoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(inputIndex);
                    if (input != null) {
                        input.clear();
                        int copyLength = Math.min(length, input.remaining());
                        input.put(data, offset, copyLength);
                        long presentationTimeUs = decoderPresentationTimeUs;
                        decoderPresentationTimeUs += 33333;
                        int flags = keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        decoder.queueInputBuffer(inputIndex, 0, copyLength, presentationTimeUs, flags);
                        queuedNalUnits++;
                        if (decoderLogSamples < 40) {
                            decoderLogSamples++;
                            Log.i(TAG, "Queued decoder NAL type=" + nalUnitType(data, offset, length)
                                    + " length=" + length
                                    + " copied=" + copyLength
                                    + " capacity=" + input.capacity()
                                    + " keyFrame=" + keyFrame);
                        }
                        if (copyLength < length) {
                            Log.w(TAG, "Decoder input truncated NAL length=" + length
                                    + " capacity=" + input.capacity());
                        }
                    }
                } else if (decoderLogSamples < 40) {
                    decoderLogSamples++;
                    Log.i(TAG, "No decoder input buffer for NAL type=" + nalUnitType(data, offset, length)
                            + " length=" + length);
                }
                drainDecoderOutputLocked(50000);
            } catch (IllegalStateException exc) {
                Log.w(TAG, "Decoder state error", exc);
                postStatus("Decoder state error; restart preview");
            }
        }
    }

    private void drainDecoderOutput(long timeoutUs) {
        synchronized (decoderLock) {
            if (decoder == null) {
                return;
            }
            try {
                drainDecoderOutputLocked(timeoutUs);
            } catch (IllegalStateException exc) {
                Log.w(TAG, "Decoder state error", exc);
                postStatus("Decoder state error; restart preview");
            }
        }
    }

    private void drainDecoderOutputLocked(long timeoutUs) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputIndex;
        while ((outputIndex = decoder.dequeueOutputBuffer(info, timeoutUs)) >= 0) {
            decoder.releaseOutputBuffer(outputIndex, true);
            renderedFrames++;
            if (decoderLogSamples < 40) {
                decoderLogSamples++;
                Log.i(TAG, "Rendered decoder frame size=" + info.size
                        + " pts=" + info.presentationTimeUs
                        + " total=" + renderedFrames);
            }
            timeoutUs = 0;
        }
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.i(TAG, "Decoder output format " + decoder.getOutputFormat());
        } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && decoderLogSamples < 80) {
            decoderLogSamples++;
            Log.i(TAG, "Decoder output not ready timeoutUs=" + timeoutUs);
        }
    }

    private void releaseDecoder() {
        synchronized (decoderLock) {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {
                }
                try {
                    decoder.release();
                } catch (Exception ignored) {
                }
                decoder = null;
            }
            spsNal = null;
            ppsNal = null;
            queuedNalUnits = 0;
            renderedFrames = 0;
            nalLogSamples = 0;
            decoderLogSamples = 0;
            decoderPresentationTimeUs = 0;
            h264DiagnosticDumpBytes = 0;
            pendingAccessUnit.reset();
            pendingAccessUnitHasVcl = false;
            pendingAccessUnitHasIdr = false;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void postStatus(String text) {
        main.post(() -> statusView.setText(text));
    }

    private static byte[] hex(String value) {
        int length = value.length();
        byte[] out = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return out;
    }

    private static String toHex(byte[] value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }
}
