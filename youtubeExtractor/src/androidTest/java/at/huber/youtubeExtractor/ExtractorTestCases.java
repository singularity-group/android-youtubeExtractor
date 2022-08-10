package at.huber.youtubeExtractor;

import android.os.Handler;
import android.os.Looper;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Log;
import android.util.SparseArray;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNotSame;
import static junit.framework.TestCase.assertTrue;

@RunWith(AndroidJUnit4.class)
@FlakyTest
public class ExtractorTestCases {

    private static final String EXTRACTOR_TEST_TAG = "Extractor Test";

    private String testUrl;

    @Test
    public void testUsualVideo() throws Throwable {
        VideoMeta expMeta = new VideoMeta("YE7VzlLtp-4", "Big Buck Bunny", "Blender",
                "UCSMOQeBJ2RAnuFungnQOxLg", 597, 0, false, "");
        extractorTest("http://youtube.com/watch?v=YE7VzlLtp-4", expMeta);
    }

    @Test
    public void testUnembeddable() throws Throwable {
        VideoMeta expMeta = new VideoMeta("QH4VHl2uQ9o", "Match Chain Reaction Amazing Fire Art - real ghost rider", "BLACKHAND",
                "UCl9nsRuGenStMDZfD95w85A", 331, 0, false, "");
        extractorTest("https://www.youtube.com/watch?v=QH4VHl2uQ9o", expMeta);
        extractorTestDashManifest("https://www.youtube.com/watch?v=QH4VHl2uQ9o");
    }

    @Test
    public void testEncipheredVideo() throws Throwable {
        VideoMeta expMeta = new VideoMeta("e8X3ACToii0", "Rise Against - Savior (Official Music Video)", "RiseAgainstVEVO",
                "UChMKB2AHNpeuWhalpRYhUaw", 242, 0, false, "");
        extractorTest("https://www.youtube.com/watch?v=e8X3ACToii0", expMeta);
    }

//    @Test
//    public void testAgeRestrictVideo() throws Throwable {
//        VideoMeta expMeta = new VideoMeta("61Ev-YvBw2c", "Test video for age-restriction",
//                "jpdemoA", "UC95NqtFsDZKlmzOJmZi_g6Q", 14, 0, false, "");
//        extractorTest("http://www.youtube.com/watch?v=61Ev-YvBw2c", expMeta);
//        // extractorTestDashManifest("http://www.youtube.com/watch?v=61Ev-YvBw2c");
//    }

    @Test
    public void testLiveStream() throws Throwable {
        VideoMeta expMeta = new VideoMeta("86YLFOog4GM", "\uD83C\uDF0E Nasa Live Stream  - Earth From Space :  Live Views from the ISS",
                "Space Videos", "UCakgsb0w7QB0VHdnCc-OVEA", 0, 0, true, "");
        int[] expectedItags = new int[] { Format.HLS_MANIFEST_ITAG };
        extractorTest("https://www.youtube.com/watch?v=86YLFOog4GM", expMeta, expectedItags);
    }


    private void extractorTestDashManifest(final String youtubeLink)
            throws Throwable {
        final CountDownLatch signal = new CountDownLatch(1);
        YouTubeExtractor.LOGGING = true;
        YouTubeExtractor.CACHING = false;

        testUrl = null;

        new Handler(Looper.getMainLooper()).post(new Runnable() {

            @Override
            public void run() {
                final YouTubeExtractor ytEx = new YouTubeExtractor(InstrumentationRegistry.getInstrumentation().getContext()) {
                    @Override
                    public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta videoMeta) {
                        assertNotNull(ytFiles);
                        int numNotDash = 0;
                        int itag;
                        for (int i = 0; i < ytFiles.size(); i++) {
                            itag = ytFiles.keyAt(i);
                            if (ytFiles.get(itag).getFormat().isDashContainer()) {
                                numNotDash = i;
                                break;
                            }
                        }
                        itag = ytFiles.keyAt(new Random().nextInt(ytFiles.size() - numNotDash) + numNotDash);
                        testUrl = ytFiles.get(itag).getUrl();
                        Log.d(EXTRACTOR_TEST_TAG, "Testing itag: " + itag +", url:" + testUrl);
                        signal.countDown();
                    }
                };
                ytEx.extract(youtubeLink);
            }
        });

        signal.await(10, TimeUnit.SECONDS);

        assertNotNull(testUrl);

        final URL url = new URL(testUrl);

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        int code = con.getResponseCode();
        con.getInputStream().close();
        con.disconnect();
        assertEquals(200, code);
    }

    private void extractorTest(final String youtubeLink, final VideoMeta expMeta)
            throws Throwable {
        extractorTest(youtubeLink, expMeta, new int[0]);
    }

    private void extractorTest(final String youtubeLink, final VideoMeta expMeta, final int[] mustIncludeItags)
            throws Throwable {
        final CountDownLatch signal = new CountDownLatch(1);
        YouTubeExtractor.LOGGING = true;
        YouTubeExtractor.CACHING = mustIncludeItags.length > 0;

        testUrl = null;

        final ArrayList<Integer> actualItags = new ArrayList<>();

        getInstrumentation().getTargetContext().getMainExecutor().execute(() -> {
            final YouTubeExtractor ytEx = new YouTubeExtractor(getInstrumentation().getTargetContext()) {
                @Override
                public void  onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta videoMeta) {
                    try {
                        assertNotNull(videoMeta);
                        assertEquals(expMeta.getVideoId(), videoMeta.getVideoId());
                        assertEquals(expMeta.getTitle(), videoMeta.getTitle());
                        assertEquals(expMeta.getAuthor(), videoMeta.getAuthor());
                        assertEquals(expMeta.getChannelId(), videoMeta.getChannelId());
                        assertEquals(expMeta.getVideoLength(), videoMeta.getVideoLength());
                        assertEquals(expMeta.isLiveStream(), videoMeta.isLiveStream());
                        assertNotSame(0, videoMeta.getViewCount());
                        assertNotNull(ytFiles);
                        int itag = ytFiles.keyAt(new Random().nextInt(ytFiles.size()));
                        testUrl = ytFiles.get(itag).getUrl();
                        Log.d(EXTRACTOR_TEST_TAG, "Testing itag: " + itag + ", url:" + testUrl);
                        for (int i = 0; i < ytFiles.size(); i++) {
                            int foundItag = ytFiles.keyAt(i);
                            actualItags.add(foundItag);
                        }
                    } finally {
                        signal.countDown();
                    }
                }
            };
            ytEx.extract(youtubeLink);
        });

        signal.await(10, TimeUnit.SECONDS);

        assertNotNull(testUrl);

        for (int expectedItag : mustIncludeItags) {
            String msg = "actualItags contains expected itag value '" + expectedItag + "'";
            assertTrue(msg, actualItags.contains(expectedItag));
        }

        final URL url = new URL(testUrl);

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        int code = con.getResponseCode();
        con.getInputStream().close();
        con.disconnect();
        assertEquals(200, code);
    }

}
