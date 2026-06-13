package com.example.appinsight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity implements SearchView.OnQueryTextListener {

    private ListView listView;
    private AppListAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private List<AppInfo> allApps = new ArrayList<>();
    private LoadAppsTask loadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.app_list);
        progressBar = findViewById(R.id.progress_bar);
        emptyView = findViewById(android.R.id.empty);
        listView.setEmptyView(emptyView);

        adapter = new AppListAdapter(this, new ArrayList<AppInfo>());
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = adapter.getItem(position);
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra("package", app.packageName);
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final AppInfo app = adapter.getItem(position);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(app.appName)
                    .setItems(new CharSequence[]{"Open", "App Info", "Share APK"}, 
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                switch (which) {
                                    case 0: openApp(app.packageName); break;
                                    case 1: openAppInfo(app.packageName); break;
                                    case 2: shareApk(app); break;
                                }
                            }
                        })
                    .show();
                return true;
            }
        });

        loadApps();
    }

    private void loadApps() {
        if (loadTask != null) return;
        loadTask = new LoadAppsTask();
        loadTask.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView sv = (SearchView) searchItem.getActionView();
        sv.setOnQueryTextListener(this);
        sv.setQueryHint("Search apps...");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            loadApps();
            return true;
        }
        if (id == R.id.action_extracted) {
            Toast.makeText(this, "APK saved to Downloads/AppInsight/", Toast.LENGTH_LONG).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        filter(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filter(newText);
        return true;
    }

    private void filter(String query) {
        if (TextUtils.isEmpty(query)) {
            adapter.setData(allApps);
            return;
        }
        String lower = query.toLowerCase();
        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo app : allApps) {
            if (app.appName.toLowerCase().contains(lower) ||
                app.packageName.toLowerCase().contains(lower)) {
                filtered.add(app);
            }
        }
        adapter.setData(filtered);
    }

    private void openApp(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "Cannot open this app", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(String packageName) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + packageName));
        startActivity(intent);
    }

    private void shareApk(AppInfo app) {
        Toast.makeText(this, "Use Extract in details to save APK", Toast.LENGTH_SHORT).show();
    }

    private class LoadAppsTask extends AsyncTask<Void, Void, List<AppInfo>> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }

        @Override
        protected List<AppInfo> doInBackground(Void... params) {
            PackageManager pm = getPackageManager();
            int flags = 0;
            if (Build.VERSION.SDK_INT >= 33) {
                flags |= PackageManager.MATCH_ALL;
            }
            List<PackageInfo> packages = pm.getInstalledPackages(flags);
            List<AppInfo> result = new ArrayList<>();

            for (PackageInfo pi : packages) {
                try {
                    if (pi.applicationInfo == null) continue;

                    AppInfo info = new AppInfo();
                    info.packageName = pi.packageName;
                    CharSequence label = pi.applicationInfo.loadLabel(pm);
                    info.appName = label != null ? label.toString() : pi.packageName;
                    info.versionName = pi.versionName != null ? pi.versionName : "?";
                    info.versionCode = pi.getLongVersionCode();
                    info.firstInstallTime = pi.firstInstallTime;
                    info.lastUpdateTime = pi.lastUpdateTime;
                    info.targetSdkVersion = pi.applicationInfo.targetSdkVersion;
                    info.apkPath = pi.applicationInfo.sourceDir;
                    info.apkSize = new File(info.apkPath).length();
                    info.icon = pi.applicationInfo.loadIcon(pm);
                    info.isSystemApp = (pi.applicationInfo.flags &
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                    result.add(info);
                } catch (Exception ignored) {
                }
            }

            Collections.sort(result, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    return a.appName.compareToIgnoreCase(b.appName);
                }
            });

            return result;
        }

        @Override
        protected void onPostExecute(List<AppInfo> result) {
            allApps = result;
            adapter.setData(result);
            progressBar.setVisibility(View.GONE);
            emptyView.setText(allApps.isEmpty() ? "No apps found" : "No apps match");
            loadTask = null;
        }
    }
}
