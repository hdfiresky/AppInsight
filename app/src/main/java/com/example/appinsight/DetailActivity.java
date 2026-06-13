package com.example.appinsight;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetailActivity extends Activity {

    private PackageManager pm;
    private String packageName;
    private PackageInfo packageInfo;
    private LinearLayout infoContainer;
    private LinearLayout permContainer;
    private ProgressBar progressBar;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        pm = getPackageManager();
        packageName = getIntent().getStringExtra("package");
        if (packageName == null) {
            Toast.makeText(this, "No package specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView iconView = findViewById(R.id.detail_icon);
        TextView nameView = findViewById(R.id.detail_app_name);
        TextView versionView = findViewById(R.id.detail_version);
        infoContainer = findViewById(R.id.info_container);
        permContainer = findViewById(R.id.permissions_container);
        progressBar = findViewById(R.id.detail_progress);
        scrollView = findViewById(R.id.detail_scroll);

        Button openBtn = findViewById(R.id.btn_open);
        Button infoBtn = findViewById(R.id.btn_app_info);
        Button extractBtn = findViewById(R.id.btn_extract);

        openBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = pm.getLaunchIntentForPackage(packageName);
                if (intent != null) startActivity(intent);
                else Toast.makeText(DetailActivity.this, "Cannot open", Toast.LENGTH_SHORT).show();
            }
        });

        infoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        });

        extractBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extractApk();
            }
        });

        loadDetails(iconView, nameView, versionView);
    }

    private void loadDetails(final ImageView iconView, final TextView nameView, final TextView versionView) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Drawable icon = packageInfo.applicationInfo.loadIcon(pm);
                            iconView.setImageDrawable(icon);
                            nameView.setText(packageInfo.applicationInfo.loadLabel(pm));
                            versionView.setText("v" + (packageInfo.versionName != null ? packageInfo.versionName : "?"));

                            addInfoRow("Package", packageInfo.packageName);
                            addInfoRow("Version Code", String.valueOf(packageInfo.getLongVersionCode()));
                            addInfoRow("Target SDK", String.valueOf(packageInfo.applicationInfo.targetSdkVersion));

                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                            addInfoRow("Installed", sdf.format(new Date(packageInfo.firstInstallTime)));
                            addInfoRow("Updated", sdf.format(new Date(packageInfo.lastUpdateTime)));

                            File apkFile = new File(packageInfo.applicationInfo.sourceDir);
                            addInfoRow("APK Size", formatSize(apkFile.length()));
                            addInfoRow("APK Path", apkFile.getAbsolutePath());

                            loadPermissions();

                            progressBar.setVisibility(View.GONE);
                            scrollView.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DetailActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    private void addInfoRow(String label, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_info, infoContainer, false);
        ((TextView) row.findViewById(R.id.info_label)).setText(label);
        ((TextView) row.findViewById(R.id.info_value)).setText(value);
        infoContainer.addView(row);
    }

    private void loadPermissions() {
        if (packageInfo.requestedPermissions == null) {
            View row = getLayoutInflater().inflate(R.layout.item_permission, permContainer, false);
            ((TextView) row.findViewById(R.id.perm_label)).setText("No permissions declared");
            ((TextView) row.findViewById(R.id.perm_status)).setVisibility(View.GONE);
            permContainer.addView(row);
            return;
        }

        for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
            String perm = packageInfo.requestedPermissions[i];
            boolean granted = (packageInfo.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;

            View row = getLayoutInflater().inflate(R.layout.item_permission, permContainer, false);
            TextView labelView = row.findViewById(R.id.perm_label);
            TextView statusView = row.findViewById(R.id.perm_status);

            String label = getPermissionLabel(perm);
            labelView.setText(label);

            if (isDangerousPermission(perm)) {
                labelView.setTextColor(0xFFD32F2F);
                labelView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }

            if (granted) {
                statusView.setText("Granted");
                statusView.setTextColor(0xFF388E3C);
            } else {
                statusView.setText("Not granted");
                statusView.setTextColor(0xFF9E9E9E);
            }

            permContainer.addView(row);
        }
    }

    private String getPermissionLabel(String permission) {
        try {
            PermissionInfo pi = pm.getPermissionInfo(permission, 0);
            CharSequence label = pi.loadLabel(pm);
            return label != null ? label.toString() : permission;
        } catch (Exception e) {
            String shortName = permission;
            if (shortName.startsWith("android.permission."))
                shortName = shortName.substring(20);
            else if (shortName.contains("."))
                shortName = shortName.substring(shortName.lastIndexOf('.') + 1);
            return shortName.replace('_', ' ');
        }
    }

    private boolean isDangerousPermission(String permission) {
        try {
            PermissionInfo pi = pm.getPermissionInfo(permission, 0);
            return (pi.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) != 0;
        } catch (Exception e) {
            String[] dangerous = {
                "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION",
                "ACCESS_COARSE_LOCATION", "READ_CONTACTS", "WRITE_CONTACTS",
                "READ_CALL_LOG", "WRITE_CALL_LOG", "READ_EXTERNAL_STORAGE",
                "WRITE_EXTERNAL_STORAGE", "READ_SMS", "SEND_SMS",
                "RECEIVE_SMS", "BODY_SENSORS", "READ_PHONE_STATE",
                "CALL_PHONE", "ADD_VOICEMAIL", "USE_SIP",
                "PROCESS_OUTGOING_CALLS", "ACTIVITY_RECOGNITION",
                "ACCESS_BACKGROUND_LOCATION", "ACCESS_MEDIA_LOCATION",
                "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO",
                "POST_NOTIFICATIONS", "NEARBY_WIFI_DEVICES"
            };
            for (String d : dangerous) {
                if (permission.endsWith("." + d)) return true;
            }
            return false;
        }
    }

    private void extractApk() {
        final String apkPath = packageInfo.applicationInfo.sourceDir;
        if (apkPath == null) {
            Toast.makeText(this, "Cannot locate APK", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Extracting...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String label = packageInfo.applicationInfo.loadLabel(pm).toString();
                    final String fileName = label + ".apk";
                    final String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                        values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
                        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/AppInsight");
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new Exception("Failed to create MediaStore entry");

                        InputStream in = new FileInputStream(apkPath);
                        OutputStream out = getContentResolver().openOutputStream(uri);
                        if (out == null) throw new Exception("Failed to open output stream");

                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                        in.close();
                        out.close();
                    } else {
                        File dir = new File(Environment.getExternalStorageDirectory(), "Download/AppInsight");
                        dir.mkdirs();
                        File outFile = new File(dir, safeName);

                        InputStream in = new FileInputStream(apkPath);
                        OutputStream out = new FileOutputStream(outFile);
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                        in.close();
                        out.close();
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DetailActivity.this,
                                "Saved: Download/AppInsight/" + safeName, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(DetailActivity.this,
                                "Extract failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private String formatSize(long bytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        double size = bytes;
        while (size >= 1024 && i < units.length - 1) {
            size /= 1024;
            i++;
        }
        return String.format(Locale.US, "%.1f %s", size, units[i]);
    }
}
