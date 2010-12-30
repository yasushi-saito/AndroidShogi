package com.ysaito.shogi;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
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

  static final int DIALOG_DOWNLOAD = 1234;
  static final int DIALOG_PROMOTE = 1235;
  
  ProgressDialog mDownloadDialog;
  AlertDialog mPromoteDialog;
  BonanzaController mController;
  BoardView mBoardView;
  
  Board.Player mHumanPlayer;
  
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

    mExtDir = getExternalFilesDir(null);
    Log.d(TAG, "Found dir2: " + mExtDir.getAbsolutePath());
    if (!installedShogiData()) {
      // Create a dialog and ask the download manager to fetch the file.
      // Block until download completes.
      // showDialog(DOWNLOAD_DIALOG);
    }

    mHumanPlayer = Board.Player.BLACK;
    mBoardView = (BoardView)findViewById(R.id.boardview);
    mBoardView.setTurn(mHumanPlayer);
    mBoardView.setEventListener(mViewListener);
    mController = new BonanzaController(mControllerHandler, mHumanPlayer);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return true;
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case DIALOG_DOWNLOAD:
        mDownloadDialog = newDownloadDialog();
        DownloadThread t = new DownloadThread(this);
        t.start();
        return mDownloadDialog;
      case DIALOG_PROMOTE: 
        mPromoteDialog = createPromoteDialog();
        return mPromoteDialog;
      default:    
        return null;
    }
  }

  boolean isComputerPlayer(Board.Player p) { return p != mHumanPlayer; }
  boolean isHumanPlayer(Board.Player p) { return p == mHumanPlayer; }
  
  // Download Bonanza data files
  boolean installedShogiData() {
    return false;
  }

  final Handler mControllerHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      Log.d(TAG, "Got controller callback");
      BonanzaController.Result r = (BonanzaController.Result)(
          msg.getData().get("result"));
      Log.d(TAG, "Controller msg: " + r.nextPlayer.toString());
      mBoardView.setState(r.board, r.nextPlayer);
      if (isComputerPlayer(r.nextPlayer)) {
        mController.computerMove(r.nextPlayer);
      }
    }
  };
  
  //
  // Handling of move requests from BoardView
  //
  Board.Move mLastMove;  // state kept during the run of promotion dialog
  final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanMove(Board.Move move) {
      if (MoveAllowsForPromotion(move)) {
        mLastMove = move;
        showDialog(DIALOG_PROMOTE);
      } else {
        mController.humanMove(move.player, move);
      }
    }
  };
  
  AlertDialog createPromoteDialog() {
    AlertDialog.Builder b = new AlertDialog.Builder(this);
    b.setTitle("Promote piece?");
    b.setCancelable(true);
    b.setOnCancelListener(
        new DialogInterface.OnCancelListener() {
          public void onCancel(DialogInterface unused) {
          }
        });
    b.setItems(
        new CharSequence[] {"Promote", "Do not promote"},
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface d, int item) {
            mLastMove.promote = false;
            if (item == 0) mLastMove.promote = true;
            mController.humanMove(mLastMove.player, mLastMove);
            mLastMove = null;
          }
        });
    return b.create();
  }
  
  static final boolean MoveAllowsForPromotion(Board.Move move) {
    if (Board.isPromoted(move.piece)) return false;  // already promoted
    if (move.player == Board.Player.WHITE && move.to_y < 6) return false;
    if (move.player == Board.Player.BLACK && move.to_y >= 3) return false;
    return true;
  }

  //
  // Data download
  //
  ProgressDialog newDownloadDialog() {
    ProgressDialog d = new ProgressDialog(this);
    d.setCancelable(true);
    d.setMessage("Downloading shogi-data.zip");
    d.setOnCancelListener(new DialogInterface.OnCancelListener() {
      public void onCancel(DialogInterface unused) {
        mDownloadCancelled = true;
      }
    });
    return d;
  }
  
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