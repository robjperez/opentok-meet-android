package com.opentok.android;


import java.net.MalformedURLException;
import java.net.URL;

public class OpenTokConfig {
    static {
        System.loadLibrary("opentok");
    }

    private final static String LOG_TAG = "opentok-config";

    public static void setAPIRootURL(String apiRootURL) throws MalformedURLException {
        setAPIRootURL(apiRootURL, true);
    }

    public static void setAPIRootURL(String apiRootURL, boolean rumorSSL) throws MalformedURLException {
        URL url = new URL(apiRootURL);
        boolean ssl = false;
        int port = url.getPort();
        if("https".equals(url.getProtocol())) {
            ssl = true;
            if(port == -1) {
                port = 443;
            }
        } else if("http".equals(url.getProtocol())) {
            ssl = false;
            if(port == -1) {
                port = 80;
            }
        }

        setAPIRootURLNative(url.getHost(), ssl, port, rumorSSL);
    }

    public static void setOTKitLogs(boolean otkitLogs) {
        setOTKitLogsNative(otkitLogs);
    }

    public static void setJNILogs(boolean jniLogs) {
        setJNILogsNative(jniLogs);
    }

    public static void setWebRTCLogs(boolean webrtcLogs) {
        setWebRTCLogsNative(webrtcLogs);
    }

    public static String getPublisherInfoStats(PublisherKit publisher) {
        String publisherStats = getPublisherInfoStatsNative(publisher);

        return publisherStats;
    }

    public static String getSubscriberInfoStats(SubscriberKit subscriber) {
        String subscriberStats = getSubscriberInfoStatsNative(subscriber);

        return subscriberStats;
    }

    protected static native void setAPIRootURLNative(String host, boolean ssl, int port, boolean rumorSSL);
    protected static native void setOTKitLogsNative(boolean otkitLogs);
    protected static native void setJNILogsNative(boolean jniLogs);
    protected static native void setWebRTCLogsNative(boolean webrtcLogs);
    protected static native String getPublisherInfoStatsNative(PublisherKit publisher);
    protected static native String getSubscriberInfoStatsNative(SubscriberKit subscriber);
}