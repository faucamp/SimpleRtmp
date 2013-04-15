package com.github.faucamp.simplertmp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import com.github.faucamp.simplertmp.output.RtmpStreamWriter;

/**
 * Simple RTMP client, using vanilla Java networking (no NIO)
 * This was created primarily to address a NIO bug in Android 2.2 when
 * used with Apache Mina, but also to provide an easy-to-use way to access
 * RTMP streams
 * 
 * @author francois
 */
public interface RtmpClient {
    
    void connect() throws IOException;
    
    /**
     * Issues an RTMP "play" command and uses the specified RtmpStreamWriter to
     * write the media content stream packets (audio, video, and metadata). 
     * 
     * This method blocks until the end of the stream has been reached.
     * 
     * @param playPath The logical 'file'/media content name that you wish to play back
     * @param raw if <code>true</code>, write the raw RTMP packets (no FLV container)
     * @return An InputStream allowing you to read the incoming playback data
     * @throws IllegalStateException if the client is not connected to a RTMP server
     * @throws IOException if a network/IO error occurs
     */
    void play(String playPath, RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException;
        
    /**
     * Issues an RTMP "play" command and uses the specified RtmpStreamWriter to
     * write the media content stream packets (audio, video, and metadata). 
     * 
     * This method blocks until the end of the stream has been reached.
     * 
     * @param playPath The logical 'file'/media content name that you wish to play back
     * @param raw if <code>true</code>, write the raw RTMP packets (no FLV container)
     * @return An InputStream allowing you to read the incoming playback data
     * @throws IllegalStateException if the client is not connected to a RTMP server
     * @throws IOException if a network/IO error occurs
     */
    void playAsync(String playPath, RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException;
    
    /**
     * Stops and closes the current RTMP stream
     */
    void closeStream() throws IllegalStateException;
    
    /**
     * Shuts down the RTMP client and stops all threads associated with it
     */
    void shutdown();
    
    /**
     * Pauses the current RTMP stream if it is playing, or unpauses it if it has already been paused
     */
    void pause();
}
