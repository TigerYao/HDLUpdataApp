package com.huatu.tiger.hdlupdateapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.blanke.xsocket.tcp.client.TcpConnConfig;
import com.blanke.xsocket.tcp.client.XTcpClient;
import com.blanke.xsocket.tcp.client.bean.TcpMsg;
import com.blanke.xsocket.tcp.server.TcpServerConfig;
import com.blanke.xsocket.tcp.server.XTcpServer;
import com.blanke.xsocket.tcp.server.listener.TcpServerListener;
import com.blanke.xsocket.utils.CharsetUtil;
import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.DownloadTask;
import com.liulishuo.filedownloader.FileDownloadListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.util.FileDownloadLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;

public class TcpServerService extends Service implements TcpServerListener {
    private XTcpServer mXTcpServer;
    private NotificationCompat.Builder mBuilder = null;
    private NotificationChannel mNotificationChannel = null;
    private NotificationManager mNotificationManager;
    private static final String TAG = "TcpServerService";
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("TcpServerService", "onCreate");
        createNotification();
        FileDownloader.setup(this.getBaseContext());
        FileDownloadLog.NEED_LOG = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        openTcpServer(20206);
        startForeground(100, mBuilder.build());
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mXTcpServer != null) {
            mXTcpServer.removeTcpServerListener(this);
            mXTcpServer.stopServer();
            mXTcpServer = null;
        }
        mNotificationManager.cancel(TAG, 100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationManager.deleteNotificationChannel(TAG);
        } else {
            stopForeground(true);
        }
    }

    private void createNotification() {
        if (null == mNotificationChannel) {
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mNotificationChannel = new NotificationChannel(TAG, TAG, NotificationManager.IMPORTANCE_DEFAULT);
                mNotificationChannel.setSound(null, null);
                mNotificationManager.createNotificationChannel(mNotificationChannel);
            }
        }
        if (null == mBuilder) {
            mBuilder = new NotificationCompat.Builder(this, TAG)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setSound(null)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true);
            mBuilder.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE);
        }
    }

    private void openTcpServer(int port) {
        if (mXTcpServer == null) {
            mXTcpServer = XTcpServer.getTcpServer(port);
            mXTcpServer.addTcpServerListener(this);
            mXTcpServer.config(new TcpServerConfig.Builder()
                    .setTcpConnConfig(new TcpConnConfig.Builder().create()).create());
        }
        mXTcpServer.startServer();
        Log.d("TcpServerService", "startServer");
    }

    public int getAppVerCode() {
        int versionCode = -1;
        List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
        for (int i = 0; i < packages.size(); i++) {
            PackageInfo packageInfo = packages.get(i);
            if (packageInfo != null && packageInfo.packageName.equalsIgnoreCase("com.tiger.hdl.hdlhome")) {
                versionCode = packageInfo.versionCode;
                Log.d("TcpServerService", "getAppVerCode .. " + versionCode);
                break;
            }
        }
        return versionCode;
    }

    /****************************TcpServerListener***************************************/
    @Override
    public void onCreated(XTcpServer server) {
        Log.d("TcpServerService", "onCreated");

        Toast.makeText(getApplicationContext(), "服务端开启", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onListened(XTcpServer server) {

        Log.d("TcpServerService", "onListened");

        Toast.makeText(getApplicationContext(), "服务端监听开启", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAccept(XTcpServer server, XTcpClient tcpClient) {
        Log.d("TcpServerService", "onAccept");
        Toast.makeText(getApplicationContext(), "客户端：" + tcpClient.getTargetInfo().getIp(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSended(XTcpServer server, XTcpClient tcpClient, TcpMsg tcpMsg) {

    }

    @Override
    public void onReceive(XTcpServer server, XTcpClient tcpClient, TcpMsg tcpMsg) {
        String receiveMsg = CharsetUtil.dataToString(tcpMsg.getSourceDataBytes(), CharsetUtil.UTF_8);
        if (TextUtils.isEmpty(receiveMsg))
            return;
        receiveMsg = receiveMsg.trim();
        Log.d("TcpServerService", "onReceive .. " + receiveMsg);
        if ("getversion".equalsIgnoreCase(receiveMsg)) {
            server.sendMsg(getAppVerCode() + "", tcpClient);
        } else if (receiveMsg.startsWith("updater")) {
            String downloadUrl = receiveMsg.substring(receiveMsg.indexOf("#") + 1);
            if (TextUtils.isEmpty(downloadUrl))
                return;
            downloadUrl(downloadUrl, server, tcpClient);
            server.sendMsg("downloading", tcpClient);
        } else if (receiveMsg.equalsIgnoreCase("getconfig")) {
            String path = Environment.getExternalStorageDirectory().getPath() + "/config.txt";
            String configMsg = readTextFile(path);
            server.sendMsg(configMsg, tcpClient);
        } else if (receiveMsg.equalsIgnoreCase("restart")){
            RootUtils.reboot();
        }
    }

    @Override
    public void onValidationFail(XTcpServer server, XTcpClient client, TcpMsg tcpMsg) {
        Log.d("TcpServerService", "onValidationFail");
        Toast.makeText(getApplicationContext(), "初始化失败", Toast.LENGTH_LONG).show();

    }

    @Override
    public void onClientClosed(XTcpServer server, XTcpClient tcpClient, String msg, Exception e) {

        Toast.makeText(getApplicationContext(), "客户端关闭", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onServerClosed(XTcpServer server, String msg, Exception e) {
        Log.d("TcpServerService", "onServerClosed");
        Toast.makeText(getApplicationContext(), "服务端关闭", Toast.LENGTH_LONG).show();

    }

    private void downloadUrl(String url, XTcpServer server, XTcpClient xTcpClient) {
        String apkName = url.substring(url.lastIndexOf("/") + 1);
        String apkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/" + apkName;
        FileDownloader.getImpl().create(url).setPath(apkPath).setListener(new FileDownloadListener() {
            @Override
            protected void pending(BaseDownloadTask baseDownloadTask, int i, int i1) {
                server.sendMsg("download start:", xTcpClient);
                InstallUninstallBroadcastReceiver.setIOInfo(server, xTcpClient, baseDownloadTask.getTargetFilePath());
            }

            @Override
            protected void progress(BaseDownloadTask baseDownloadTask, int i, int i1) {
                server.sendMsg("download progress " + (i /i1), xTcpClient);
            }

            @Override
            protected void completed(BaseDownloadTask baseDownloadTask) {
                server.sendMsg("download completed" + baseDownloadTask.getTargetFilePath(), xTcpClient);
                if(baseDownloadTask.getUrl().endsWith("apk")){
                    RootUtils.installPkg(baseDownloadTask.getTargetFilePath());
                }
            }

            @Override
            protected void paused(BaseDownloadTask baseDownloadTask, int i, int i1) {
                server.sendMsg("download paused", xTcpClient);
            }

            @Override
            protected void error(BaseDownloadTask baseDownloadTask, Throwable throwable) {
                server.sendMsg("download error :" + throwable.getMessage(), xTcpClient);
            }

            @Override
            protected void warn(BaseDownloadTask baseDownloadTask) {
                server.sendMsg("download warn", xTcpClient);
            }
        }).start();
    }

    public String readTextFile(String filePath) {
        String feedType = null;
        //StringBuffer初始化
        StringBuffer feedTypeStringBuffer = new StringBuffer();
//        feedTypeStringBuffer.append("");
        String lineTxt = null;
        File file = new File(filePath);
        //文件读写会产生异常所以要放在try catch中
        try {
            //判断文件存在
            if (file.isFile() && file.exists()) {
                Log.e("文件", "存在");
                //读取字节流 utf-8是字符编码方式 可以根据具体情况进行更改
                InputStreamReader read = new InputStreamReader(new FileInputStream(file), "utf-8");
                BufferedReader bufferedReader = new BufferedReader(read);

                while ((lineTxt = bufferedReader.readLine()) != null) {
                    feedTypeStringBuffer.append(lineTxt);
                    Log.e("读取的数据：", feedTypeStringBuffer.toString());
                }
                //通过split转换成list返回
                feedType = feedTypeStringBuffer.toString();
                read.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return feedType;
    }

    private void installApk(String apkPath) {

        if (apkPath == null) {
            Log.e(TAG, "Download apk failed,empty apk uri");
            return;
        } else {
            Log.d(TAG, "Download apk finish ,apkUri:%s" + apkPath);
        }

        //获取待安装的apk文件
        File apkFile = new File(apkPath);//(getRealFilePath(context, downLoadApkUri));
        if (!apkFile.exists()) {
            Log.d(TAG, "Apk file is not exist." + apkPath);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uri = FileProvider.getUriForFile(getBaseContext(), getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        }
        try {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Start system install activity exception: %s" + e.getLocalizedMessage());
        }

    }
}
