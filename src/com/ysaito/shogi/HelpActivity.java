package com.ysaito.shogi;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class HelpActivity extends Activity {
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.help);
		WebView browser = (WebView)findViewById(R.id.helpview);
		browser.loadData(helpString(), "text/html", "utf-8");
	}
	
	private String helpString() {
		StringBuilder b = new StringBuilder();
		b.append("<html><body>");
		b.append("<h2>Shogi version 1.2</h2>");
		b.append("Released Jan 3, 2011.");
		b.append("<p>Copyright(c) 2011 Yasushi Saito<br>All rights reserved<br>");
		b.append("This program is distributed under Apache 2.0 license.");
		b.append("<h3>What are Shogi Database files?</h3>");
		b.append("Three files, <tt>hash.bin</tt>, <tt>fv.bin</tt>, and <tt>book.bin</tt> are loaded by the computer solver code (Bonanza).");
		b.append("These files are large (41MB compressed, 200MB uncompressed) and they don't fit in the ");
		b.append("application <tt>.apk</tt> file.");
		b.append("<p>");
		b.append("If you don't want to download them over air, you can copy them to sdcard manually:<ul>");
		b.append("<li> Download Bonanza from ");
		appendHref(b, "http://www.computer-shogi.org/library/bonanza_v4.1.3.zip");
		b.append(".");
		b.append("<li> Extract hash.bin, fv.bin, and book.bin from the zip file.");
		b.append("<li> Mount the phone's sdcard, and copy the files to directory /sdcard/Android/data/com.ysaito.shogi/files");
		b.append("</ul>");
		
		b.append("<h3>Credits</h3>");
		b.append("<ul><li>The computer shogi solver is a slightly modified Bonanza version 4.1.3, by Kunihito Hoki et al.");
		b.append("For more information about Bonanza, visit ");
		appendHref(b, "http://www.geocities.jp/bonanza_shogi/");
		b.append(".");
		b.append("<li>Bitmaps for shogi pieces are from ");
		appendHref(b, "http://mucho.girly.jp/bona/");
		b.append(". </ul>");
		
		b.append("<h3>Source Code</h3>");
		b.append("The source code for this program is available at ");
		appendHref(b, "https://github.com/yasushi-saito/AndroidShogi");
		b.append(".");
		b.append("</body></html>");		
		return b.toString();
	}
	
	private void appendHref(StringBuilder b, String uri) {
		b.append("<a href=\""); b.append(uri); b.append("\">");
		b.append(uri);
		b.append("</a>");
	}
}
