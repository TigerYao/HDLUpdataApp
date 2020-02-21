package com.huatu.tiger.hdlupdateapp;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.blanke.xsocket.tcp.client.XTcpClient;
import com.blanke.xsocket.tcp.server.XTcpServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadUtils {

    private static final String TAG = "DownloadUtils";

    //下载器
    private DownloadManager downloadManager;
    //上下文
    private Context mContext;
    //下载的ID
    private long downloadId;
    XTcpServer xTcpServer;
    XTcpClient xTcpClient;
    public String apkPath;

    public DownloadUtils(Context context, XTcpServer xTcpServer, XTcpClient xTcpClient) {
        this.mContext = context;
        this.xTcpServer = xTcpServer;
        this.xTcpClient = xTcpClient;
    }


    public void downloadApk(String downLoadUrl, String description) {

        String apkName = downLoadUrl.substring(downLoadUrl.lastIndexOf("/") + 1);

        Log.d(TAG, "DownLoadUrl: %s \nDownLoadDescription: \n%s" + downLoadUrl + "..." + description);
        DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(Uri.parse(downLoadUrl));
        } catch (Exception e) {
            Log.d(TAG, "downLoad failed :%s" + e.getLocalizedMessage());
            return;
        }

        request.setTitle("HDL系统升级");
        request.setDescription(description);

        //在通知栏显示下载进度
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

        //设置保存下载apk保存路径
        apkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/" + apkName;
        request.setDestinationInExternalPublicDir(Environment.getRootDirectory().getAbsolutePath() + "/Download/", apkName);

        //获取DownloadManager
        downloadManager = (DownloadManager) mContext.getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);

        //将下载请求加入下载队列，加入下载队列后会给该任务返回一个long型的id，通过该id可以取消任务，重启任务、获取下载的文件等等
        downloadId = downloadManager.enqueue(request);

        //注册广播接收者，监听下载状态
        mContext.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    //广播监听下载的各个状态
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                Uri downloadFileUri = downloadManager.getUriForDownloadedFile(downloadId);
                xTcpServer.sendMsg("下载完成，正在安装", xTcpClient);
//                installApk(downloadFileUri, context);
            }
        }
    };


    private String getRealFilePath(Context context, Uri uri) {
        if (null == uri) return null;
        final String scheme = uri.getScheme();
        String data = null;
        if (scheme == null)
            data = uri.getPath();
        else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            data = uri.getPath();
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.ImageColumns.DATA}, null, null, null);
            if (null != cursor) {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                    if (index > -1) {
                        data = cursor.getString(index);
                    }
                }
                cursor.close();
            }
        }
        return data;
    }


    private void installApk() {

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

        //调用系统安装apk
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {   //7.0版本以上
            Uri uriForFile = FileProvider.getUriForFile(mContext, "com.example.app.fileProvider", apkFile);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(uriForFile, "application/vnd.android.package-archive");
            Log.d(TAG, "Install apk,\ndata: %s" + uriForFile);
        } else {
            Uri uri = Uri.fromFile(apkFile);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            Log.d(TAG, "Install apk,\ndata: %s" + uri);
        }

        try {
            mContext.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Start system install activity exception: %s" + e.getLocalizedMessage());
        }

    }


    /**
     * 从服务器下载文件
     *
     * @param path     下载文件的地址
     */
    public void downLoad(final String path) {
        String apkName = path.substring(path.lastIndexOf(".") + 1);
        apkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/"+apkName;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(path);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setReadTimeout(5000);
                    con.setConnectTimeout(5000);
                    con.setRequestProperty("Charset", "UTF-8");
                    con.setRequestMethod("GET");
//                    if (con.getResponseCode() == 200) {
                        InputStream is = con.getInputStream();//获取输入流
                        FileOutputStream fileOutputStream = null;//文件输出流
                        if (is != null) {
                            fileOutputStream = new FileOutputStream(createFile(apkPath));//指定文件保存路径，代码看下一步
                            byte[] buf = new byte[1024];
                            int ch;
                            while ((ch = is.read(buf)) != -1) {
                                fileOutputStream.write(buf, 0, ch);//将获取到的流写入文件中
                            }
                        }
                        if (fileOutputStream != null) {
                            fileOutputStream.flush();
                            fileOutputStream.close();
                        }
                        xTcpServer.sendMsg("下载完成，正在安装", xTcpClient);
                        installApk();
//                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    xTcpServer.sendMsg("下载出错", xTcpClient);
                }
            }
        }).start();
    }


    /**
     * 创建一个文件
     *
     * @return
     */
    public File createFile(String fileName) {
        File file = new File(fileName);

        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return new File(fileName);
    }

}