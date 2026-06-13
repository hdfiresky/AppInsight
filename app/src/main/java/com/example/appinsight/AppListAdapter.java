package com.example.appinsight;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class AppListAdapter extends BaseAdapter {
    private final Context context;
    private List<AppInfo> apps;
    private final PackageManager pm;

    public AppListAdapter(Context context, List<AppInfo> apps) {
        this.context = context;
        this.apps = apps;
        this.pm = context.getPackageManager();
    }

    public void setData(List<AppInfo> apps) {
        this.apps = apps;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public AppInfo getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewHolder holder;

        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.icon = view.findViewById(R.id.item_icon);
            holder.appName = view.findViewById(R.id.item_app_name);
            holder.packageName = view.findViewById(R.id.item_package_name);
            holder.version = view.findViewById(R.id.item_version);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        AppInfo app = apps.get(position);
        Drawable ic = app.icon;
        if (ic == null) ic = pm.getDefaultActivityIcon();
        holder.icon.setImageDrawable(ic);
        holder.appName.setText(app.appName);
        holder.packageName.setText(app.packageName);
        String ver = "v" + app.versionName;
        if (app.isSystemApp) ver += " \u00B7 System";
        holder.version.setText(ver);
        holder.version.setTextColor(app.isSystemApp ? 0xFF9E9E9E : 0xFF757575);
        return view;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView appName;
        TextView packageName;
        TextView version;
    }
}
