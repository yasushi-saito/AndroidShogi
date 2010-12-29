sub MaybeMkdir ($) {
  local ($dir) = @_;
  if (!-d $dir) {
    mkdir($dir) || die "$dir: $!";
  }
}

$BONANZA_DIR="/tmp/bonanza_download";
$TMP_DIR="/tmp/tmpmount";

MaybeMkdir($BONANZA_DIR);
MaybeMkdir($TMP_DIR);

$BONANZA_ZIP = "$BONANZA_DIR/bonanza_v4.1.3.zip";
if (!-e $BONANZA_ZIP) {
  $url="http://www.computer-shogi.org/library/bonanza_v4.1.3.zip";
  print "Downloading $url to $BONANZA_SIP\n";
  system("wget http://www.computer-shogi.org/library/bonanza_v4.1.3.zip --output-document=$BONANZA_ZIP");
}
if (!-d "$BONANZA_DIR/bonanza_v4.1.3") {
  system("cd $BONANZA_DIR; unzip bonanza_v4.1.3.zip");
}

system("sudo umount $TMP_DIR");
system("sudo mount -o loop,umask=0 /home/saito/.android/avd/saito_avd.avd/sdcard.img $TMP_DIR");

print "mounted\n";

MaybeMkdir("$TMP_DIR/shogi");

print "copy book.bin\n";
system("cp $BONANZA_DIR/bonanza_v4.1.3/winbin/book.bin $TMP_DIR/shogi");

print "copy hash.bin\n";
system("cp $BONANZA_DIR/bonanza_v4.1.3/winbin/hash.bin $TMP_DIR/shogi");

print "copy fv.bin\n";
system("cp $BONANZA_DIR/bonanza_v4.1.3/winbin/fv.bin $TMP_DIR/shogi");

print "unmounting\n";
system("sudo umount $TMP_DIR");

