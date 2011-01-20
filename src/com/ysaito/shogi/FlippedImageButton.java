package com.ysaito.shogi;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;

public class FlippedImageButton extends ImageButton {
  private final static String TAG = "Flip"; 
  public FlippedImageButton(Context context, AttributeSet attrs) {
    super(context, attrs);
  }
  public FlippedImageButton(Context context) {
    super(context);
  }

  @Override protected void onDraw(Canvas canvas) {
    float px = getMeasuredWidth() / 2.15f;
    float py = getMeasuredHeight() / 2.15f;
    Log.d(TAG, String.format("DRAW: %f %f", px, py));
    canvas.rotate(180, px, py);
    
    super.onDraw(canvas);
  }
}
