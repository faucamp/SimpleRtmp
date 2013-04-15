package com.github.faucamp.simplertmp.output;

import java.io.IOException;
import com.github.faucamp.simplertmp.io.packets.ContentData;
import com.github.faucamp.simplertmp.io.packets.Data;

/**
 * Interface for writing RTMP content streams (audio/video)
 * 
 * @author francois
 */
public abstract class RtmpStreamWriter {

    public abstract void write(Data dataPacket) throws IOException;

    public abstract void write(ContentData packet) throws IOException;

    public void close() {
        synchronized (this) {
            this.notifyAll();
        }
    }
}
