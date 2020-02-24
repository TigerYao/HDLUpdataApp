package com.huatu.tiger.hdlupdateapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.blanke.xsocket.tcp.client.XTcpClient;
import com.blanke.xsocket.tcp.server.XTcpServer;

import java.io.File;

/**
 * （安装/替换/卸载）接收者，可以接收三个广播
 * 当其他应用被（安装/替换/卸载）后，Android操作系统会自动检测到，系统会自动的发出以下三种广播
 * 1安装
 * 2替换
 * 3卸载
 */
public class InstallUninstallBroadcastReceiver extends BroadcastReceiver {

    private final String TAG = InstallUninstallBroadcastReceiver.class.getSimpleName();
    private static XTcpServer mServer;
    private static XTcpClient mTcpClient;
    private static String mApkPath;
    public static void setIOInfo(XTcpServer server, XTcpClient xTcpClient, String apkPath) {
        mServer = server;
        mTcpClient = xTcpClient;
        mApkPath = apkPath;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        /**
         * 获取（安装/替换/卸载）应用的 信息
         */
        String packages = intent.getDataString();
        packages = packages.split(":")[1];
        if (packages == null || !packages.equalsIgnoreCase("com.tiger.hdl.hdlhome"))
            return;
        if (mServer == null || mTcpClient == null || mServer.isClosed() || mTcpClient.isDisconnected())
            return;
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            try {
                Intent openHome = new Intent();
                openHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                openHome.setClassName("com.tiger.hdl.hdlhome", "com.tiger.hdl.hdlhome.LauncherActivity");
            } catch (Exception e) {
                e.printStackTrace();
            }
            deletedFile();
            Log.d(TAG, packages + "应用程序安装了，需要进行该应用安全扫描吗");
            mServer.sendMsg("installed", mTcpClient);
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            Log.d(TAG, packages + "应用程序卸载了，需要清理垃圾有缓存吗");
            mServer.sendMsg("unInstalled", mTcpClient);
        } else if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            Log.d(TAG, packages + "应用程序覆盖了");
            try {
                Intent openHome = new Intent();
                openHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                openHome.setClassName("com.tiger.hdl.hdlhome", "com.tiger.hdl.hdlhome.LauncherActivity");
            } catch (Exception e) {
                e.printStackTrace();
            }
            deletedFile();
            mServer.sendMsg("installed", mTcpClient);
        }
    }

    private void deletedFile(){
        if(mApkPath == null || TextUtils.isEmpty(mApkPath))
            return;
        try {
            File file = new File(mApkPath);
            if (file.exists()) {
                file.delete();
            }
        }catch (Exception e){

        }
    }
}