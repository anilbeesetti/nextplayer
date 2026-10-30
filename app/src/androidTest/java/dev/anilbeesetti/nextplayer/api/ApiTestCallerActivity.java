package dev.anilbeesetti.nextplayer.api;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
import android.widget.TextView;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Android-only helpers also run standalone under the test APK's own UID. */
public final class ApiTestCallerActivity extends Activity {
    private static WeakReference<ApiTestCallerActivity> current = new WeakReference<>(null);
    private String token;
    private String targetPackage;
    private String authority;
    private File resultFile;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        current = new WeakReference<>(this);
        token = getIntent().getStringExtra("token");
        targetPackage = getIntent().getStringExtra("target_package");
        resultFile = new File(getFilesDir(), "api-result-" + token + ".json");
        resultFile.delete();
        TextView view = new TextView(this);
        view.setText("Player API test caller");
        setContentView(view);
        try {
            authority = getPackageManager().getProviderInfo(
                new ComponentName(this, MediaProvider.class), 0).authority;
            launch(getIntent(), true);
        } catch (Exception error) {
            writeResult(new JSONObject(), error);
        }
    }

    private Uri uri(String name, String gate) {
        Uri.Builder builder = new Uri.Builder().scheme("content").authority(authority)
            .appendPath(token).appendPath(name);
        if (gate != null) builder.appendQueryParameter("gate", gate);
        return builder.build();
    }

    private void launch(Intent options, boolean forResult) {
        String item = options.getStringExtra("item");
        if (item == null) item = "first.wav";
        String gate = options.getStringExtra("gate");
        Intent request = new Intent(Intent.ACTION_VIEW)
            .setClassName(targetPackage, "dev.anilbeesetti.nextplayer.feature.player.PlayerActivity")
            .setDataAndType(uri(item, gate), "audio/wav")
            .putExtra("return_result", true)
            .putExtra("background", options.getBooleanExtra("background", false))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (options.hasExtra("speed")) request.putExtra("playback_speed", options.getFloatExtra("speed", 1f));
        ArrayList<Uri> items = new ArrayList<>();
        items.add(uri("first.wav", gate));
        items.add(uri("second.wav", null));
        items.add(uri("third.wav", null));
        Uri subtitle = uri("captions.vtt", null);
        ClipData grants = ClipData.newRawUri("API test media", items.get(0));
        grants.addItem(new ClipData.Item(items.get(1)));
        grants.addItem(new ClipData.Item(items.get(2)));
        grants.addItem(new ClipData.Item(subtitle));
        request.setClipData(grants);
        if (options.getBooleanExtra("queue", false)) {
            request.putExtra("video_list", items.toArray(new Uri[0]));
            request.putExtra("video_list_is_explicit", true);
            request.putExtra("subs", new Uri[]{subtitle});
            request.putExtra("subs.enable", new Uri[]{subtitle});
        }
        if (forResult) startActivityForResult(request, 2018);
        else startActivity(request);
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        JSONObject result = new JSONObject();
        try {
            result.put("code", resultCode).put("callerUid", Process.myUid());
            if (data != null) result.put("uri", data.getDataString());
            writeResult(result, null);
        } catch (Exception error) {
            writeResult(result, error);
        }
    }

    private void writeResult(JSONObject result, Exception error) {
        try {
            if (error != null) result.put("caller_error", error.toString());
            try (FileOutputStream stream = new FileOutputStream(resultFile)) {
                stream.write(result.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception writeError) {
            throw new IllegalStateException(writeError);
        }
    }

    @Override public void onDestroy() {
        if (current.get() == this) current.clear();
        super.onDestroy();
    }

    public static final class Commands extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            String operation = intent.getStringExtra("operation");
            String token = intent.getStringExtra("token");
            if ("status".equals(operation)) {
                Gate gate = MediaProvider.gates.get(token);
                setResultCode(gate == null ? 0 : gate.finished ? 2 : 1);
            } else if ("release".equals(operation)) {
                Gate gate = MediaProvider.gates.get(token);
                if (gate != null) gate.released.countDown();
            } else if ("replace".equals(operation)) {
                ApiTestCallerActivity caller = current.get();
                if (caller == null) throw new IllegalStateException("No active test caller");
                caller.launch(intent, false);
            } else if ("finish".equals(operation)) {
                ApiTestCallerActivity caller = current.get();
                if (caller != null) caller.finishAndRemoveTask();
            }
        }
    }

    private static final class Gate {
        final CountDownLatch released = new CountDownLatch(1);
        volatile boolean finished;
    }

    public static final class MediaProvider extends ContentProvider {
        private static final ConcurrentHashMap<String, Gate> gates = new ConcurrentHashMap<>();
        private File audio;
        private File subtitle;

        @Override public boolean onCreate() {
            audio = new File(getContext().getFilesDir(), "api-audio.wav");
            subtitle = new File(getContext().getFilesDir(), "api-captions.vtt");
            try {
                if (!audio.exists()) {
                    int size = 16000 * 2 * 120;
                    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                        .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + size)
                        .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                        .putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000)
                        .putShort((short) 2).putShort((short) 16)
                        .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(size);
                    try (FileOutputStream stream = new FileOutputStream(audio)) {
                        stream.write(header.array());
                        byte[] silence = new byte[16000];
                        for (int remaining = size; remaining > 0; remaining -= silence.length) stream.write(silence);
                    }
                }
                try (FileOutputStream stream = new FileOutputStream(subtitle)) {
                    stream.write("WEBVTT\n\n00:00.000 --> 02:00.000\nAPI subtitle\n".getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }

        private File file(Uri uri) throws FileNotFoundException {
            String name = uri.getLastPathSegment();
            if ("captions.vtt".equals(name)) return subtitle;
            if ("first.wav".equals(name) || "second.wav".equals(name) || "third.wav".equals(name)) return audio;
            throw new FileNotFoundException(uri.toString());
        }

        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            if (!"r".equals(mode)) throw new FileNotFoundException("Read-only fixture");
            return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
            String token = uri.getQueryParameter("gate");
            if (token != null) {
                Gate gate = gates.computeIfAbsent(token, ignored -> new Gate());
                try {
                    if (!gate.released.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Metadata gate timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                } finally {
                    gate.finished = true;
                }
            }
            String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = uri.getLastPathSegment();
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = audio.length();
            }
            cursor.addRow(row);
            return cursor;
        }
        @Override public String getType(Uri uri) { return "captions.vtt".equals(uri.getLastPathSegment()) ? "text/vtt" : "audio/wav"; }
        @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
        @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    }
}
