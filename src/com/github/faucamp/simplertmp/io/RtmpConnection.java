package com.github.faucamp.simplertmp.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.github.faucamp.simplertmp.RtmpClient;
import com.github.faucamp.simplertmp.amf.AmfNull;
import com.github.faucamp.simplertmp.amf.AmfNumber;
import com.github.faucamp.simplertmp.amf.AmfObject;
import com.github.faucamp.simplertmp.io.packets.Abort;
import com.github.faucamp.simplertmp.io.packets.Acknowledgement;
import com.github.faucamp.simplertmp.io.packets.Handshake;
import com.github.faucamp.simplertmp.io.packets.Command;
import com.github.faucamp.simplertmp.io.packets.ContentData;
import com.github.faucamp.simplertmp.io.packets.Data;
import com.github.faucamp.simplertmp.io.packets.UserControl;
import com.github.faucamp.simplertmp.io.packets.RtmpPacket;
import com.github.faucamp.simplertmp.io.packets.WindowAckSize;
import com.github.faucamp.simplertmp.output.RtmpStreamWriter;
import com.github.faucamp.simplertmp.util.L;

/**
 * Main RTMP connection implementation class
 * 
 * @author francois
 */
public class RtmpConnection implements RtmpClient, PacketRxHandler, ThreadController {

    private String appName;
    private String host;
    private String streamName;
    private String swfUrl = "http://localhost:5080/demos/ofla_demo.swf";
    private String tcUrl = "rtmp://localhost/oflaDemo";
    private String pageUrl = "http://localhost:5080";
    private int port;
    private Socket socket;
    private RtmpSessionInfo rtmpSessionInfo;
    private int transactionIdCounter = 0;
    private static final int SOCKET_CONNECT_TIMEOUT_MS = 3000;
    private WriteThread writeThread;
    private final ConcurrentLinkedQueue<RtmpPacket> rxPacketQueue;
    private final Object lock = new Object();
    private boolean active = false;
    private RtmpStreamWriter rtmpStreamWriter;
    private volatile boolean fullyConnected = false;
    private final Object connectingLock = new Object();
    private volatile boolean connecting = false;
    private int currentStreamId = -1;
    /** Timestamp at which stream was paused; 0 if not stream is not paused */
    private int pauseTimeStamp = -1;
    /** Used to track stream position for pause/resume */
    private int streamPosition = 0;

    public RtmpConnection(String host, int port, String appName) {
        this.host = host;
        this.port = port;
        this.appName = appName;
        rtmpSessionInfo = new RtmpSessionInfo();
        rxPacketQueue = new ConcurrentLinkedQueue<RtmpPacket>();
    }

    @Override
    public void connect() throws IOException {

        L.d("RtmpConnection.connect() called. Host: " + host + ", port: " + port + ", appName: " + appName + ", playPath: " + streamName);
        socket = new Socket();
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        socket.connect(socketAddress, SOCKET_CONNECT_TIMEOUT_MS);
        BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
        BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        L.d("RtmpConnection.connect(): socket connection established, doing handhake...");
        handshake(in, out);
        active = true;
        L.d("RtmpConnection.connect(): handshake done");
        ReadThread readThread = new ReadThread(rtmpSessionInfo, in, this, this);
        writeThread = new WriteThread(rtmpSessionInfo, out, this);
        readThread.start();
        writeThread.start();

        // Start the "main" handling thread
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    L.d("RtmpConnection: starting main rx handler loop");
                    handleRxPacketLoop();
                } catch (IOException ex) {
                    Logger.getLogger(RtmpConnection.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }).start();

        rtmpConnect();
    }

    @Override
    public void play(String playPath, RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException {
        rtmpPlay(playPath, rtmpStreamWriter, true);
    }

    @Override
    public void playAsync(String playPath, RtmpStreamWriter rtmpStreamWriter) throws IllegalStateException, IOException {
        rtmpPlay(playPath, rtmpStreamWriter, false);
    }

    private void rtmpPlay(String playPath, RtmpStreamWriter rtmpStreamWriter, boolean block) throws IllegalStateException, IOException {
        if (connecting) {
            synchronized (connectingLock) {
                try {
                    connectingLock.wait();
                } catch (InterruptedException ex) {
                    // do nothing
                }
            }
        }
        if (!fullyConnected) {
            throw new IllegalStateException("Not connected to RTMP server");
        }
        this.streamName = playPath;
        this.rtmpStreamWriter = rtmpStreamWriter;

        if (currentStreamId != -1) {
            // A stream object exists; play the requested stream name
            Command play = new Command("play", transactionIdCounter);
            play.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_STREAM_CHANNEL);
            play.getHeader().setMessageStreamId(currentStreamId);
            play.addData(new AmfNull());
            play.addData(playPath); // what to play
            play.addData(0); // play start position
            play.addData(-2); // play duration
            //L.i("+++++++  WRITING PLAY packet ++++++++");
            //writeThread.send(play);
            // Set buffer length of the message stream to 5000ms (just Flash Player)
            final ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.CONTROL_CHANNEL);

            // Set buffer length of message stream 0 to 5000ms (just Flash Player)
            UserControl userControl = new UserControl(UserControl.Type.SET_BUFFER_LENGTH, chunkStreamInfo);
            userControl.setEventData(0, 5000);
            writeThread.send(userControl);

            UserControl userControl2 = new UserControl(UserControl.Type.SET_BUFFER_LENGTH, chunkStreamInfo);
            userControl2.setEventData(currentStreamId, 5000);
            L.d("rtmpPlay(): Writing play & control packets");
            writeThread.send(play, userControl2);
        } else {
            // No current stream object exists; first issue the createStream command
            // - the handler for the response of that command will call rtmpPlay() again (without blocking)
            rtmpCreateStream();
        }

        if (block) {
            synchronized (rtmpStreamWriter) {
                try {
                    rtmpStreamWriter.wait();
                } catch (InterruptedException ex) {
                    throw new IOException("Thread interrupted while waiting on RTMP stream writer");
                }
            }
        }
    }

    private void rtmpCreateStream() {
        L.d("rtmpCreateStream(): Sending createStream command...");
        final ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_COMMAND_CHANNEL);
        // Send createStream() command
        Command createStream = new Command("createStream", ++transactionIdCounter, chunkStreamInfo);
        writeThread.send(createStream);
    }

    @Override
    public void closeStream() throws IllegalStateException {
        if (!fullyConnected) {
            throw new IllegalStateException("Not connected to RTMP server");
        }
        if (currentStreamId == -1) {
            throw new IllegalStateException("No current stream object exists");
        }
        streamName = null;
        L.d("closeStream(): setting current stream ID to -1");
        currentStreamId = -1;
        Command closeStream = new Command("closeStream", 0);
        closeStream.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_STREAM_CHANNEL);
        closeStream.getHeader().setMessageStreamId(currentStreamId);
        closeStream.addData(new AmfNull());  // command object: null for "closeStream"
        writeThread.send(closeStream);
    }

    @Override
    public void pause() throws IllegalStateException {
        if (!fullyConnected) {
            throw new IllegalStateException("Not connected to RTMP server");
        }
        if (currentStreamId == -1) {
            throw new IllegalStateException("No current stream object exists");
        }
        Command pause = new Command("pause", 0);
        pause.getHeader().setChunkStreamId(ChunkStreamInfo.RTMP_STREAM_CHANNEL);
        pause.getHeader().setMessageStreamId(currentStreamId);
        pause.addData(new AmfNull()); // command object: null for "pause"
        // Add pause/unpause flag pause timestamp
        if (pauseTimeStamp == -1) {
            // Pause the stream
            L.i("pause(): Pausing stream with ID: " + currentStreamId);
            pause.addData(true);
            pauseTimeStamp = streamPosition;
            pause.addData(pauseTimeStamp);
        } else {
            L.i("pause(): Resuming stream with ID: " + currentStreamId);
            // Resume the stream
            pause.addData(false);
            pause.addData(pauseTimeStamp);
            pauseTimeStamp = -1;
        }
        writeThread.send(pause);
    }

    /**
     * Performs the RTMP handshake sequence with the server 
     */
    private void handshake(InputStream in, OutputStream out) throws IOException {
        Handshake handshake = new Handshake();
        handshake.writeC0(out);
        handshake.writeC1(out); // Write C1 without waiting for S0
        out.flush();
        handshake.readS0(in);
        handshake.readS1(in);
        handshake.writeC2(out);
        handshake.readS2(in);
    }

    private void rtmpConnect() throws IOException, IllegalStateException {
        if (fullyConnected || connecting) {
            throw new IllegalStateException("Already connecting, or connected to RTMP server");
        }
        L.d("rtmpConnect(): Building 'connect' invoke packet");
        Command invoke = new Command("connect", ++transactionIdCounter, rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_COMMAND_CHANNEL));
        invoke.getHeader().setMessageStreamId(0);

        AmfObject args = new AmfObject();
        args.setProperty("app", appName);
        args.setProperty("flashVer", "LNX 11,2,202,233"); // Flash player OS: Linux, version: 11.2.202.233
        args.setProperty("swfUrl", swfUrl);
        args.setProperty("tcUrl", tcUrl);
        args.setProperty("fpad", false);
        args.setProperty("capabilities", 239);
        args.setProperty("audioCodecs", 3575);
        args.setProperty("videoCodecs", 252);
        args.setProperty("videoFunction", 1);
        args.setProperty("pageUrl", pageUrl);

        invoke.addData(args);

        connecting = true;

        L.d("rtmpConnect(): Writing 'connect' invoke packet");
        invoke.getHeader().setAbsoluteTimestamp(0);
        writeThread.send(invoke);
    }

    @Override
    public void handleRxPacket(RtmpPacket rtmpPacket) {
        L.d("handleRxPacket(): called");
        rxPacketQueue.add(rtmpPacket);
        synchronized (lock) {
            lock.notify();
        }
    }

    private void handleRxPacketLoop() throws IOException {
        L.d("handleRxPacketLoop(): called");
        // Handle all queued received RTMP packets
        while (active) {
            RtmpPacket rtmpPacket = rxPacketQueue.poll();
            while (rtmpPacket != null) {

                switch (rtmpPacket.getHeader().getMessageType()) {
                    case ABORT:
                        rtmpSessionInfo.getChunkStreamInfo(((Abort) rtmpPacket).getChunkStreamId()).clearStoredChunks();
                        break;
                    case USER_CONTROL_MESSAGE: {
                        UserControl ping = (UserControl) rtmpPacket;
                        switch (ping.getType()) {
                            case PING_REQUEST: {
                                ChunkStreamInfo channelInfo = rtmpSessionInfo.getChunkStreamInfo(ChunkStreamInfo.CONTROL_CHANNEL);
                                L.d("handleRxPacketLoop(): Sending PONG reply..");
                                UserControl pong = new UserControl(ping, channelInfo);
                                writeThread.send(pong);
                                break;
                            }
                            case STREAM_EOF:
                                L.i("handleRxPacketLoop(): Stream EOF reached, closing RTMP writer...");
                                rtmpStreamWriter.close();
                                break;
                        }
                        break;
                    }
                    case WINDOW_ACKNOWLEDGEMENT_SIZE: {
                        WindowAckSize windowAckSize = (WindowAckSize) rtmpPacket;
                        if (L.isDebugEnabled()) {
                            L.d("handleRxPacketLoop(): Setting acknowledgement window size to: " + windowAckSize.getAcknowledgementWindowSize());
                        }
                        rtmpSessionInfo.setAcknowledgmentWindowSize(windowAckSize.getAcknowledgementWindowSize());
                        break;
                    }
                    case COMMAND_AMF0:
                        handleRxInvoke((Command) rtmpPacket);
                        break;
                    case DATA_AMF0: {
                        Data data = (Data) rtmpPacket;
                        if ("onMetaData".equals(data.getType())) {
                            rtmpStreamWriter.write(data);
                        }
                        break;
                    }
                    case AUDIO:
                    case VIDEO:
                        streamPosition = rtmpPacket.getHeader().getAbsoluteTimestamp();
                        rtmpStreamWriter.write((ContentData) rtmpPacket);
                        break;
                    default:
                        L.w("handleRxPacketLoop(): Not handling unimplemented/unknown packet of type: " + rtmpPacket.getHeader().getMessageType());
                        break;
                }
                // Get next packet (if any)
                rtmpPacket = rxPacketQueue.poll();
            }
            // Wait for next received packet
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    L.w("handleRxPacketLoop: Interrupted", ex);
                }
            }
        }
        shutdownImpl();
    }

    private void handleRxInvoke(Command invoke) throws IOException {
        String commandName = invoke.getCommandName();

        if (commandName.equals("_result")) {
            // This is the result of one of the methods invoked by us
            String method = rtmpSessionInfo.takeInvokedCommand(invoke.getTransactionId());

            L.d("handleRxInvoke: Got result for invoked method: " + method);
            if ("connect".equals(method)) {
                // We can now send createStream commands
                connecting = false;
                fullyConnected = true;
                synchronized (connectingLock) {
                    connectingLock.notifyAll();
                }
            } else if ("createStream".contains(method)) {

                currentStreamId = (int) ((AmfNumber) invoke.getData().get(1)).getValue();
                if (L.isDebugEnabled()) {
                    L.d("handleRxInvoke(): Stream ID to play: " + currentStreamId);
                }
                if (streamName != null) {
                    // Start playing the requested stream immediately
                    rtmpPlay(streamName, rtmpStreamWriter, false);
                }

            } else {
                L.w("handleRxInvoke(): '_result' message received for unknown method: " + method);
            }
//        } else if (commandName.equals("onStatus")) {
//            }
        } else {
            L.e("handleRxInvoke(): Uknown/unhandled server invoke: " + invoke);
        }
    }

    @Override
    public void threadHasExited(Thread thread) {
        shutdown();
    }

    @Override
    public void shutdown() {
        active = false;
        synchronized (lock) {
            lock.notify();
        }
    }

    private void shutdownImpl() {
        // Shut down read/write threads, if necessary
        if (Thread.activeCount() > 1) {
            L.i("shutdown(): Shutting down read/write threads");
            Thread[] threads = new Thread[Thread.activeCount()];
            Thread.enumerate(threads);
            for (Thread thread : threads) {
                if (thread instanceof ReadThread && thread.isAlive()) {
                    ((ReadThread) thread).shutdown();
                } else if (thread instanceof WriteThread && thread.isAlive()) {
                    ((WriteThread) thread).shutdown();
                }
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ex) {
                L.w("shutdown(): failed to close socket", ex);
            }
        }
        if (rtmpStreamWriter != null) {
            rtmpStreamWriter.close();
        }
    }

    @Override
    public void notifyWindowAckRequired(final int numBytesReadThusFar) {
        L.i("RtmpConnection.notifyWindowAckRequired() called");
        // Create and send window bytes read acknowledgement        
        writeThread.send(new Acknowledgement(numBytesReadThusFar));
    }
}
