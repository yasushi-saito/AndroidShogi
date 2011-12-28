package com.ysaito.shogi.test;

import java.io.IOException;
import java.io.Serializable;

import com.ysaito.shogi.ExternalCacheManager;

import android.test.InstrumentationTestCase;
import android.util.Log;

public class ExternalCacheManagerTest extends InstrumentationTestCase {
  private static class TestObject implements Serializable {
    public final int value;
    TestObject(int v) { value = v; }
  }
  
  public void testBasic() throws IOException {
    ExternalCacheManager cache = newCache();
    cache.write("foobar", new TestObject(1234));
    assertTrue(cache.read("blah") == null);
    assertEquals(1234, ((TestObject)cache.read("foobar")).value);
  }
  
  private ExternalCacheManager mCache = null;
  private ExternalCacheManager newCache() {
    if (mCache == null) {
      mCache = new ExternalCacheManager(getInstrumentation().getTargetContext(), "test");
    }
    mCache.clearAll();
    return mCache;
  }
}
