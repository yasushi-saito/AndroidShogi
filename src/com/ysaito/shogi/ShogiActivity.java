package com.ysaito.shogi;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;

import com.ysaito.shogi.BonanzaController;

public class ShogiActivity extends Activity {
  static final String TAG = "Shogi"; 
  File mExtDir;
  String mMenu;

  static final int DIALOG_DOWNLOAD = 1234;
  static final int DIALOG_PROMOTE = 1235;
  static final int DIALOG_CONFIRM_QUIT = 1236;
 
  ProgressDialog mDownloadDialog;
  AlertDialog mPromoteDialog;
  BonanzaController mController;
  BoardView mBoardView;
  TextView mStatusView;
  
  // List of players played by humans. The list size is usually one, when one side is 
  // played by Human and the other side by the computer.
  ArrayList<Board.Player> mHumanPlayers;
  
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
    if (!installedShogiData()) {
      // Create a dialog and ask the download manager to fetch the file.
      // Block until download completes.
      // showDialog(DOWNLOAD_DIALOG);
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
        getBaseContext());
    String player_black = prefs.getString("player_black", "Human");
    String player_white = prefs.getString("player_white", "Computer");
    int computer_level = Integer.parseInt(
        prefs.getString("computer_difficulty", "1"));
    Log.d(TAG, "onCreate, dir=" + mExtDir.getAbsolutePath() 
        + " black=" + player_black + " white=" + player_white);

    mHumanPlayers = new ArrayList<Board.Player>();
    // mHumanPlayers.add(Board.Player.BLACK);
    if (true) {
      if (player_black.equals("Human")) {
        Log.d(TAG, "Add BLACK human player");
        mHumanPlayers.add(Board.Player.BLACK);
      }
      if (player_white.equals("Human")) {
        Log.d(TAG, "Add WHITE human player");        
        mHumanPlayers.add(Board.Player.WHITE);      
      }
    }
    mStatusView = (TextView)findViewById(R.id.gamestatus);
    mStatusView.setText("FOOHAH!");
    mBoardView = (BoardView)findViewById(R.id.boardview);
    
    mBoardView.initialize(mViewListener, mStatusView, mHumanPlayers);
    
    mController = new BonanzaController(mControllerHandler, Board.Player.BLACK);
    // mController will call back via mControllerHandler when Bonanza is 
    // initialized. mControllerHandler will cause mBoardView to start accepting
    // user inputs.
  }
  
  @Override
  public void onDestroy() {
    Log.d(TAG, "ShogiActivity destroyed");
    super.onDestroy();
  }

  @Override
  public void onBackPressed() { 
    Log.d(TAG, "Back button");
    if (mBoardView.gameState() == Board.GameState.ACTIVE) {
      showDialog(DIALOG_CONFIRM_QUIT);
    } else {
      super.onBackPressed();
    }
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
      case DIALOG_CONFIRM_QUIT:
        return createConfirmQuitDialog();
      default:    
        return null;
    }
  }

  boolean isComputerPlayer(Board.Player p) { 
    return p != Board.Player.INVALID && !isHumanPlayer(p);
  }
  
  boolean isHumanPlayer(Board.Player p) {
    return mHumanPlayers.contains(p);
  }
  
  // Download Bonanza data files
  boolean installedShogiData() {
    return false;
  }

  //
  // Handling results from the Bonanza controller thread
  //
  final Handler mControllerHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      Log.d(TAG, "Got controller callback");
      BonanzaController.Result r = (BonanzaController.Result)(
          msg.getData().get("result"));
      Log.d(TAG, "Controller msg: " + r.toString());
      mBoardView.setState(r.gameState, r.board, r.nextPlayer, 
          r.errorMessage);
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
    if (move.from_x < 0) return false;  // dropping a captured piece
    if (move.player == Board.Player.WHITE && move.to_y < 6) return false;
    if (move.player == Board.Player.BLACK && move.to_y >= 3) return false;
    return true;
  }

  // 
  // Confirm quitting the game ("BACK" button interceptor)
  //
  AlertDialog createConfirmQuitDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage("Are you sure you want to quite the game?");
    builder.setCancelable(false);
    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface d, int id) {
        finish();
      }
    });
    builder.setNegativeButton("No",  new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface d, int id) {
        // nothing to do
      }
    });
    return builder.create();
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
    ShogiActivity mContext;

    public DownloadThread(ShogiActivity s) {
      mContext = s;
    }
    
    @Override public void run() {
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
  }
}