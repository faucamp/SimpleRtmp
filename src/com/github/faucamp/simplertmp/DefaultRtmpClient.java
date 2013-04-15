package com.github.faucamp.simplertmp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.github.faucamp.simplertmp.io.RtmpConnection;
import com.github.faucamp.simplertmp.output.RtmpStreamWriter;

/**
 * Default implementation of an RTMP client
 * 
 * @author francois
 */
public class DefaultRtmpClient implements RtmpClient {

    private static final Pattern rtmpUrlPattern = Pattern.compile("^rtmp://([^/:]+)(:(\\d+))*/([^?]+)(\\?(.*))*$");
    
    private RtmpClient rtmpConnection;
    private String playPath;

    /** 
     * Constructor for specified host, port and application
     * 
     * @param host the hostname or IP address to connect to
     * @param port the port to connect to
     * @param application the application to connect to
     */
    public DefaultRtmpClient(String host, int port, String application) {
        rtmpConnection = new RtmpConnection(host, port, application);
    }

    /** 
     * Constructor for specified host and application, using the default RTMP port (1935)
     * 
     * @param host the hostname or IP address to connect to
     * @param application the application to connect to
     */
    public DefaultRtmpClient(String host, String application) {
        this(host, 1935, application);
    }

    /** 
     * Constructor for URLs in the format: rtmp://host[:port]/application[?streamName]
     * 
     * @param url a RTMP URL in the format: rtmp://host[:port]/application[?streamName]
     */
    public DefaultRtmpClient(String url) {
        Matcher matcher = rtmpUrlPattern.matcher(url);
        if (matcher.matches()) {
            String portStr = matcher.group(3);
            int port = portStr != null ? Integer.parseInt(portStr) : 1935;            
            playPath = matcher.group(6);            
            rtmpConnection = new RtmpConnection(matcher.group(1), port, matcher.group(4));
        } else {
            throw new RuntimeException("Invalid RTMP URL. Must be in format: rtmp://host[:port]/application[?streamName]");
        }
    }

   

    @Override
    public void connect() throws IOException {
        rtmpConnection.connect();
    }

    @Override
    public void shutdown() {
        rtmpConnection.shutdown();
    }

    public void play(RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException {
        if (playPath == null) {
            throw new IllegalStateException("No stream name specified");
        }
        rtmpConnection.play(playPath, rtmpStreamWriter);
    }
    
    public void playAsync(RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException {
        if (playPath == null) {
            throw new IllegalStateException("No stream name specified");
        }
        rtmpConnection.playAsync(playPath, rtmpStreamWriter);
    }

    @Override
    public void play(String playPath, RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException {
        rtmpConnection.play(playPath, rtmpStreamWriter);
    }

    @Override
    public void playAsync(String playPath, RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException {
        rtmpConnection.playAsync(playPath, rtmpStreamWriter);
    }

    @Override
    public void closeStream() throws IllegalStateException {
        rtmpConnection.closeStream();
    }
    
    @Override
    public void pause() throws IllegalStateException {
        rtmpConnection.pause();
    }
}
