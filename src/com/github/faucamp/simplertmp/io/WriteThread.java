package com.github.faucamp.simplertmp.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.github.faucamp.simplertmp.io.packets.Command;
import com.github.faucamp.simplertmp.io.packets.RtmpPacket;
import com.github.faucamp.simplertmp.util.L;

/**
 * RTMPConnection's write thread
 * 
 * @author francois
 */
public class WriteThread extends Thread {

    private RtmpSessionInfo rtmpSessionInfo;
    private OutputStream out;
    private ConcurrentLinkedQueue<RtmpPacket> writeQueue = new ConcurrentLinkedQueue<RtmpPacket>();
    private final Object lock = new Object();
    private volatile boolean active = true;
    private ThreadController threadController;

    public WriteThread(RtmpSessionInfo rtmpSessionInfo, OutputStream out, ThreadController threadController) {
        super("RtmpWriteThread");
        this.rtmpSessionInfo = rtmpSessionInfo;
        this.out = out;
        this.threadController = threadController;
    }

    @Override
    public void run() {

        while (active) {
            RtmpPacket rtmpPacket = writeQueue.poll();
            // Write all queued RTMP packets
            while (rtmpPacket != null) {
                try {
                    final ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(rtmpPacket.getHeader().getChunkStreamId());
                    chunkStreamInfo.setPrevHeaderTx(rtmpPacket.getHeader());
                    L.d("WriteThread: writing packet: " + rtmpPacket);
                    rtmpPacket.writeTo(out, rtmpSessionInfo.getChunkSize(), chunkStreamInfo);
                    System.out.println("writethread wrote packet: "+rtmpPacket+", size: "+rtmpPacket.getHeader().getPacketLength());
                    if (rtmpPacket instanceof Command) {
                        rtmpSessionInfo.addInvokedCommand(((Command) rtmpPacket).getTransactionId(), ((Command) rtmpPacket).getCommandName());
                    }
                } catch (IOException ex) {
                    L.e("WriteThread: Caught IOException during write loop, shutting down", ex);
                    active = false;
                }
                rtmpPacket = writeQueue.poll();
            }
            try {
                out.flush();
            } catch (IOException ex) {
                L.e("WriteThread: Caught IOException while flushing stream, shutting down", ex);
                active = false;
                continue;
            }
            // Wait for next command
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    L.w("WriteThread: Interrupted", ex);
                }
            }
        }
        // Close outputstream
        try {
            out.close();
        } catch (Exception ex) {
            L.w("WriteThread: Failed to close outputstream", ex);
        }
        L.d("WriteThread: exiting");
        if (threadController != null) {
            threadController.threadHasExited(this);
        }
    }

    /** Transmit the specified RTMP packet (thread-safe) */
    public void send(RtmpPacket rtmpPacket) {
        writeQueue.add(rtmpPacket);
        synchronized (lock) {
            lock.notify();
        }
    }
    
    /** Transmit the specified RTMP packet (thread-safe) */
    public void send(RtmpPacket... rtmpPackets) {                
        writeQueue.addAll(Arrays.asList(rtmpPackets));
        synchronized (lock) {
            lock.notify();
        }
    }

    public void shutdown() {
        L.d("WriteThread: Stopping write thread...");
        active = false;
        synchronized (lock) {
            lock.notify();
        }
    }
}
