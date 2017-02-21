package cn.woblog.android.downloader.core.thread;


import android.os.Process;
import android.util.Log;
import cn.woblog.android.downloader.DownloadException;
import cn.woblog.android.downloader.DownloadManagerImpl.Config;
import cn.woblog.android.downloader.core.DownloadResponse;
import cn.woblog.android.downloader.domain.DownloadInfo;
import cn.woblog.android.downloader.domain.DownloadThreadInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

/**
 * Created by renpingqing on 17/1/22.
 */

public class DownloadThread implements Runnable {

  public static final String TAG = "DownloadThread";

  private final DownloadThreadInfo downloadThreadInfo;
  private final DownloadResponse downloadResponse;
  private final Config config;
  private final DownloadInfo downloadInfo;
  private final DownloadProgressListener downloadProgressListener;
  private long lastProgress;
  private InputStream inputStream;

  public DownloadThread(DownloadThreadInfo downloadThreadInfo, DownloadResponse downloadResponse,
      Config config,
      DownloadInfo downloadInfo, DownloadProgressListener downloadProgressListener) {
    this.downloadThreadInfo = downloadThreadInfo;
    this.downloadResponse = downloadResponse;
    this.config = config;
    this.downloadInfo = downloadInfo;
    this.lastProgress = downloadThreadInfo.getProgress();
    this.downloadProgressListener = downloadProgressListener;
  }

  @Override
  public void run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
    checkIfPause();
    try {
      executeDownload();
    } catch (DownloadException e) {
      e.printStackTrace();
      if (e.getCode() == DownloadException.EXCEPTION_PAUSE) {

      } else {
        downloadInfo.setStatus(DownloadInfo.STATUS_ERROR);
        downloadResponse.onStatusChanged(downloadInfo);
      }
      downloadResponse.handleException(e);
    }
  }

  private void executeDownload() {
    HttpURLConnection httpConnection = null;
    try {
      final URL url = new URL(downloadThreadInfo.getUri());
      httpConnection = (HttpURLConnection) url.openConnection();
      httpConnection.setConnectTimeout(config.getConnectTimeout());
      httpConnection.setReadTimeout(config.getReadTimeout());
      httpConnection.setRequestMethod(config.getMethod());
      if (downloadInfo.isSupportRanges()) {
        httpConnection.setRequestProperty("Range",
            "bytes=" + downloadThreadInfo.getStart() + "-" + downloadThreadInfo.getEnd());
      }
      final int responseCode = httpConnection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_PARTIAL
          || responseCode == HttpURLConnection.HTTP_OK) {
        inputStream = httpConnection.getInputStream();
        RandomAccessFile raf = new RandomAccessFile(downloadInfo.getPath(), "rwd");
        raf.seek(lastProgress);
        final byte[] bf = new byte[1024 * 8];
        int len = -1;
        int offset = 0;
        while (true) {
          checkIfPause();
          len = inputStream.read(bf);
          if (len == -1) {
            break;
          }
          raf.write(bf, 0, len);
          offset += len;

//          synchronized (downloadProgressListener) {
          downloadThreadInfo.setProgress(lastProgress + offset);
            downloadProgressListener.onProgress();
//          }

          Log.d(TAG,
              "downloadInfo thread " + downloadThreadInfo.getThreadId() + " progress:"
                  + downloadThreadInfo.getProgress()
                  + ",start:" + downloadThreadInfo.getStart() + ",end:" + downloadThreadInfo
                  .getEnd());
        }

        //downloadInfo success
        downloadProgressListener.onDownloadSuccess();
      } else {
        throw new DownloadException(DownloadException.EXCEPTION_SERVER_SUPPORT_CODE,
            "UnSupported response code:" + responseCode);
      }
      checkIfPause();
    } catch (ProtocolException e) {
      throw new DownloadException(DownloadException.EXCEPTION_PROTOCOL, "Protocol error", e);
    } catch (IOException e) {
      throw new DownloadException(DownloadException.EXCEPTION_IO_EXCEPTION, "IO error", e);
    } catch (Exception e) {
      throw new DownloadException(DownloadException.EXCEPTION_OTHER, "other error", e);
    } finally {
      if (httpConnection != null) {
        httpConnection.disconnect();
      }
    }
  }

  private void checkIfPause() {
    if (downloadInfo.isPause()) {
      throw new DownloadException(DownloadException.EXCEPTION_PAUSE);
    }
  }

  public interface DownloadProgressListener {

    void onProgress();

    void onDownloadSuccess();
  }


}
