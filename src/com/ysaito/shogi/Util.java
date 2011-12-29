// Copyright 2011 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import org.mozilla.universalchardet.UniversalDetector;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * @author saito@google.com (Yaz Saito)
 *
 */
public class Util {
  private static ThreadLocal<byte[]> mBuf = new ThreadLocal<byte[]>() {
    @Override protected synchronized byte[] initialValue() { 
      return new byte[8192];
    }
  };

  public static Reader inputStreamToReader(InputStream in, String defaultEncoding) throws IOException {
    byte[] contents = Util.streamToBytes(in);
    String encoding = Util.detectEncoding(contents, defaultEncoding);
    return new InputStreamReader(new ByteArrayInputStream(contents), encoding);
  }
  
  /** Read the contents of @p into a byte array */
  public static byte[] streamToBytes(InputStream in) throws IOException {
    byte[] tmpBuf = mBuf.get();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int n;
    while ((n = in.read(tmpBuf)) > 0) {
      out.write(tmpBuf, 0, n);  
    }
    return out.toByteArray();
  }
  
  /** Detect character encoding of @p contents. Return null on error */
  public static String detectEncoding(
      byte[] contents,
      String defaultEncoding) {
    UniversalDetector encodingDetector = new UniversalDetector(null);
    
    encodingDetector.reset();
    encodingDetector.handleData(contents, 0, contents.length);
    encodingDetector.dataEnd();
    String encoding = encodingDetector.getDetectedCharset();
    if (encoding == null) {
      encoding = defaultEncoding;
      if (encoding == null) encoding = "SHIFT-JIS";
    }
    return encoding;
  }
  
  public static String bytesToHexText(byte[] b) {
    StringBuffer hex = new StringBuffer();
    for (int i = 0;i < b.length; i++) {
      hex.append(Integer.toHexString(b[i] & 0xff));
    }        
    return hex.toString();
  }
  
  public static String throwableToString(Throwable e) {
    StringBuilder b = new StringBuilder();
    b.append(e.toString()).append("\n");
    for (StackTraceElement elem : e.getStackTrace()) {
      b.append(elem.toString()).append("\n");
    }
    return b.toString();
  }
  
  public static void showErrorDialog(Context context, String error) {
    AlertDialog.Builder b = new AlertDialog.Builder(context);
    b.setMessage(error)
    .setCancelable(false)
    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {  };
    });
    b.create().show();
  }
}
