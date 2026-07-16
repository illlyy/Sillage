package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.Toast;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/** Receives Android shares and lets the user choose the Fcode destination. */
public final class CodexShareActivity extends Activity {
    static final String ACTION_SHARE_TO_CURRENT_UI = "com.ilyop.codex.SHARE_TO_CURRENT_UI";
    static final String ACTION_SHARE_TO_NEW_UI = "com.ilyop.codex.SHARE_TO_NEW_UI";
    static final String EXTRA_SHARE_PAYLOAD = "com.ilyop.codex.SHARE_PAYLOAD";

    @Override protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        prepareAndShow(getIntent());
    }

    private void prepareAndShow(Intent source) {
        try {
            JSONObject payload = stageShare(source);
            new AlertDialog.Builder(this)
                .setTitle("??? Fcode")
                .setItems(new String[]{"????? UI ??", "??? UI ???", "??? Termux"}, (dialog, which) -> {
                    if (which == 0) launchUi(ACTION_SHARE_TO_CURRENT_UI, payload);
                    else if (which == 1) launchUi(ACTION_SHARE_TO_NEW_UI, payload);
                    else launchTermux(source, payload);
                })
                .setOnCancelListener(dialog -> finish())
                .show();
        } catch (Exception error) {
            Toast.makeText(this, "?????????" + error.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private JSONObject stageShare(Intent source) throws Exception {
        File root = new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, "codex-shares/" + UUID.randomUUID());
        if (!root.mkdirs()) throw new IllegalStateException("??????????");
        JSONArray files = new JSONArray();
        for (Uri uri : sharedUris(source)) {
            if (uri == null) continue;
            String name = safeName(displayName(uri));
            File destination = new File(root, files.length() + "-" + name).getCanonicalFile();
            String prefix = root.getCanonicalPath() + File.separator;
            if (!destination.getPath().startsWith(prefix)) throw new IllegalArgumentException("?????");
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(destination)) {
                if (input == null) throw new IllegalStateException("????????");
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) if (count > 0) output.write(buffer, 0, count);
            }
            files.put(new JSONObject().put("path", destination.getAbsolutePath())
                .put("label", name).put("mimeType", mimeType(uri, name)));
        }
        String text = source == null ? "" : source.getStringExtra(Intent.EXTRA_TEXT);
        return new JSONObject().put("files", files).put("text", text == null ? "" : text)
            .put("subject", source == null ? "" : source.getStringExtra(Intent.EXTRA_SUBJECT));
    }

    private void launchUi(String action, JSONObject payload) {
        Intent intent = new Intent(this, CodexHomeActivity.class).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHARE_PAYLOAD, payload.toString());
        startActivity(intent);
        finish();
    }

    private void launchTermux(Intent source, JSONObject payload) {
        try {
            JSONArray files = payload.optJSONArray("files");
            if (files != null && files.length() > 1) {
                File downloads = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "downloads");
                if (!downloads.isDirectory() && !downloads.mkdirs()) throw new IllegalStateException("???? ~/downloads");
                for (int i = 0; i < files.length(); i++) {
                    JSONObject item = files.getJSONObject(i);
                    File inputFile = new File(item.getString("path"));
                    File outputFile = uniqueDestination(downloads, item.optString("label", inputFile.getName()));
                    try (InputStream input = new java.io.FileInputStream(inputFile);
                         FileOutputStream output = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[32 * 1024];
                        int count;
                        while ((count = input.read(buffer)) >= 0) if (count > 0) output.write(buffer, 0, count);
                    }
                }
                Toast.makeText(this, "??? " + files.length() + " ???? ~/downloads", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, CodexHomeActivity.class).setAction(CodexHomeActivity.ACTION_OPEN_TERMUX)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } else {
                Intent intent = source == null ? new Intent(Intent.ACTION_SEND) : new Intent(source);
                intent.setClass(this, com.termux.filepicker.TermuxFileReceiverActivity.class);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        } catch (Exception error) {
            Toast.makeText(this, "????? Termux?" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private static File uniqueDestination(File directory, String requestedName) {
        String safe = safeName(requestedName);
        File candidate = new File(directory, safe);
        if (!candidate.exists()) return candidate;
        int dot = safe.lastIndexOf('.');
        String stem = dot > 0 ? safe.substring(0, dot) : safe;
        String extension = dot > 0 ? safe.substring(dot) : "";
        for (int index = 1; ; index++) {
            candidate = new File(directory, stem + "-" + index + extension);
            if (!candidate.exists()) return candidate;
        }
    }

    private static java.util.ArrayList<Uri> sharedUris(Intent source) {
        java.util.ArrayList<Uri> result = new java.util.ArrayList<>();
        if (source == null) return result;
        if (source.getClipData() != null) {
            for (int i = 0; i < source.getClipData().getItemCount(); i++) result.add(source.getClipData().getItemAt(i).getUri());
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(source.getAction())) {
            java.util.ArrayList<Uri> streams = source.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null) result.addAll(streams);
        } else {
            Uri uri = source.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) result.add(uri);
        }
        return result;
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (!TextUtils.isEmpty(value)) return value;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String fallback = uri == null ? "shared-file" : uri.getLastPathSegment();
        return TextUtils.isEmpty(fallback) ? "shared-file" : fallback;
    }

    private String mimeType(Uri uri, String name) {
        String value = uri == null ? null : getContentResolver().getType(uri);
        if (!TextUtils.isEmpty(value)) return value;
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name);
        String guessed = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension == null ? "" : extension.toLowerCase(java.util.Locale.ROOT));
        return guessed == null ? "application/octet-stream" : guessed;
    }

    private static String safeName(String value) {
        String name = TextUtils.isEmpty(value) ? "shared-file" : value;
        name = name.replace('/', '_').replace('\\', '_').replace('\n', '_').replace('\r', '_');
        return name.length() > 160 ? name.substring(0, 160) : name;
    }
}
