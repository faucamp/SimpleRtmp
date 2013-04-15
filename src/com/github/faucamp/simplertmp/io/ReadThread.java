package com.github.faucamp.simplertmp.io;

import java.io.InputStream;
import com.github.faucamp.simplertmp.io.packets.RtmpPacket;
import com.github.faucamp.simplertmp.util.L;

/**
 * RTMPConnection's read thread
 * 
 * @author francois
 */
public class ReadThread extends Thread {

    private RtmpDecoder rtmpDecoder;
    private InputStream in;
    private PacketRxHandler packetRxHandler;
    private ThreadController threadController;

    public ReadThread(RtmpSessionInfo rtmpSessionInfo, InputStream in, PacketRxHandler packetRxHandler, ThreadController threadController) {
        super("RtmpReadThread");
        this.in = in;
        this.packetRxHandler = packetRxHandler;
        this.rtmpDecoder = new RtmpDecoder(rtmpSessionInfo);
        this.threadController = threadController;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                RtmpPacket rtmpPacket = rtmpDecoder.readPacket(in);
                if (rtmpPacket != null) {
                    // Pass to handler
                    packetRxHandler.handleRxPacket(rtmpPacket);
                }
//            } catch (WindowAckRequired war) {
//                L.i("ReadThread: Window Acknowledgment required, notifying packet handler...");
//                packetRxHandler.notifyWindowAckRequired(war.getBytesRead());
//                if (war.getRtmpPacket() != null) {
//                    // Pass to handler
//                    packetRxHandler.handleRxPacket(war.getRtmpPacket());
//                }
            } catch (Exception ex) {
                if (!this.isInterrupted()) {
                    L.e("ReadThread: Caught exception while reading/decoding packet, shutting down...", ex);
                    this.interrupt();
                }
            }
        }
        // Close inputstream
        try {
            in.close();
        } catch (Exception ex) {
            L.w("ReadThread: Failed to close inputstream", ex);
        }
        L.i("ReadThread: exiting");
        if (threadController != null) {
            threadController.threadHasExited(this);
        }
    }

    public void shutdown() {
        L.d("ReadThread: Stopping read thread...");
        this.interrupt();
    }
}
