package com.ysaito.shogi;

import java.io.File;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;

import com.ysaito.shogi.BonanzaController;

public class Shogi extends Activity {
  static final String TAG = "Shogi"; 
  File mExtDir;
  String mMenu;

  static final int DOWNLOAD_DIALOG = 1234;
  ProgressDialog mDownloadDialog;
  BonanzaController mController;
  BoardView mBoardView;
  
  static final int S_INITIAL = 0;
  static final int S_SENTE = 1;
  static final int S_GOTE = 2;
  static final int S_FINISHED = 3;
  int mState;
  
  // Becomes true if downloading is cancelled by the user.
  // TODO(saito): is there a way to synchronize accesses to this field?
  boolean mDownloadCancelled;

  static {  
    System.loadLibrary("bonanza-jni");  
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    mState = S_INITIAL;
    mExtDir = getExternalFilesDir(null);
    Log.d(TAG, "Found dir2: " + mExtDir.getAbsolutePath());
    if (!installedShogiData()) {
      // Create a dialog and ask the download manager to fetch the file.
      // Block until download completes.
      // showDialog(DOWNLOAD_DIALOG);
    }

    mState = S_SENTE;
    mBoardView = (BoardView)findViewById(R.id.boardview);
    mBoardView.setTurn(Board.P_SENTE);
    mBoardView.setEventListener(mViewListener);
    mController = new BonanzaController(mControllerHandler);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return true;
  }

  // Download Bonanza data files
  boolean installedShogiData() {
    return false;
  }

  final Handler mControllerHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      Log.d(TAG, "Got controller callback");
      BonanzaController.Result r = (BonanzaController.Result)(
          msg.getData().get("result"));
      mBoardView.setBoard(r.board);
    }
  };
  
  final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanMove(Board.Move move) {
      Log.d(TAG, "Move: " + move.toString());
      mController.humanMove(move);
      mController.computerMove();
    }
  };
  
  final Handler mDownloadHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      String message = msg.getData().getString("message");
      if (message != null) {
        mDownloadDialog.setMessage(message);
      }
      int progress = msg.getData().getInt("progress", -1);
      if (progress >= 0) {
        mDownloadDialog.setProgress(progress);
      }
      if (msg.getData().getBoolean("done")) {
        mDownloadDialog.dismiss();
      }
    }
  };

  protected Dialog onCreateDialog(int id) {
    mDownloadDialog = new ProgressDialog(this);
    mDownloadDialog.setCancelable(true);
    mDownloadDialog.setMessage("Downloading shogi-data.zip");
    mDownloadDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      public void onCancel(DialogInterface unused) {
        mDownloadCancelled = true;
      }
    });
    DownloadThread t = new DownloadThread(this);
    t.start();
    return mDownloadDialog;
  }

  class DownloadThread extends Thread {
    Shogi mContext;

    public DownloadThread(Shogi s) {
      mContext = s;
    }

    public void run() {
      // Arrange to download from XXXX to /sdcard/<app_dir>/shogi-data.zip
      Uri.Builder uriBuilder = new Uri.Builder();
      uriBuilder.scheme("http");
      uriBuilder.authority("www.corp.google.com");
      uriBuilder.path("/~saito/shogi-data.zip");
      DownloadManager.Request req = new DownloadManager.Request(uriBuilder.build());

      File dest = new File(mExtDir, "shogi-data.zip");
      req.setDestinationUri(Uri.fromFile(dest));
      Log.d(TAG, "Start downloading to " + dest.getAbsolutePath());
      DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
      long downloadId = manager.enqueue(req);

      DownloadManager.Query query = new DownloadManager.Query();
      query.setFilterById(downloadId);

      int n = 0;
      while (!mContext.mDownloadCancelled) {
        ++n;
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          Log.e(TAG, "Thread Interrupted");
        }

        int status = -1;
        long bytes = -1;
        if (true) {
          Cursor cursor = manager.query(query);
          int idStatus = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
          cursor.moveToFirst();
          status = cursor.getInt(idStatus);

          int idBytes = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
          cursor.moveToFirst();
          bytes = cursor.getLong(idBytes);
          cursor.close();
        }

        Message msg = mContext.mDownloadHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("message", "XXX " + n + ": " + status + ": bytes: " + bytes);
        msg.setData(b);
        mContext.mDownloadHandler.sendMessage(msg);
        if (status == DownloadManager.STATUS_FAILED ||
            status == DownloadManager.STATUS_SUCCESSFUL) break;
      }
      if (mContext.mDownloadCancelled) {
        manager.remove(downloadId);
      }
      Log.d(TAG, "Download thread exiting");
    }
  };
}