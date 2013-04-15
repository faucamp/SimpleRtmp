package com.github.faucamp.simplertmp.io;

import java.io.IOException;
import java.io.InputStream;
import com.github.faucamp.simplertmp.io.packets.Abort;
import com.github.faucamp.simplertmp.io.packets.Audio;
import com.github.faucamp.simplertmp.io.packets.Command;
import com.github.faucamp.simplertmp.io.packets.Data;
import com.github.faucamp.simplertmp.io.packets.RtmpHeader;
import com.github.faucamp.simplertmp.io.packets.RtmpPacket;
import com.github.faucamp.simplertmp.io.packets.SetChunkSize;
import com.github.faucamp.simplertmp.io.packets.SetPeerBandwidth;
import com.github.faucamp.simplertmp.io.packets.UserControl;
import com.github.faucamp.simplertmp.io.packets.Video;
import com.github.faucamp.simplertmp.io.packets.WindowAckSize;
import com.github.faucamp.simplertmp.util.L;

/**
 *
 * @author francois
 */
public class RtmpDecoder {

    private RtmpSessionInfo rtmpSessionInfo;

    public RtmpDecoder(RtmpSessionInfo rtmpSessionInfo) {
        this.rtmpSessionInfo = rtmpSessionInfo;
    }

    public RtmpPacket readPacket(InputStream in) throws IOException {

        L.d("\n====  readPacket(): called =====");
        RtmpHeader header = RtmpHeader.readHeader(in, rtmpSessionInfo);
        RtmpPacket rtmpPacket;
        L.d("readPacket(): header.messageType: " + header.getMessageType());

        ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(header.getChunkStreamId());

        chunkStreamInfo.setPrevHeaderRx(header);

        if (header.getPacketLength() > rtmpSessionInfo.getChunkSize()) {
            L.d("readPacket(): packet size (" + header.getPacketLength() + ") is bigger than chunk size (" + rtmpSessionInfo.getChunkSize() + "); storing chunk data");
            // This packet consists of more than one chunk; store the chunks in the chunk stream until everything is read
            if (!chunkStreamInfo.storePacketChunk(in, rtmpSessionInfo.getChunkSize())) {
                L.d(" readPacket(): returning null because of incomplete packet");                
                return null; // packet is not yet complete
            } else {
                L.d(" readPacket(): stored chunks complete packet; reading packet");
                in = chunkStreamInfo.getStoredPacketInputStream();
            }
        } else {
            L.d("readPacket(): packet size (" + header.getPacketLength() + ") is LESS than chunk size (" + rtmpSessionInfo.getChunkSize() + "); reading packet fully");
        }

        switch (header.getMessageType()) {

            case SET_CHUNK_SIZE: {
                SetChunkSize setChunkSize = new SetChunkSize(header);
                setChunkSize.readBody(in);
                L.d("readPacket(): Setting chunk size to: " + setChunkSize.getChunkSize());
                rtmpSessionInfo.setChunkSize(setChunkSize.getChunkSize());                
                return null;
            }
            case ABORT:
                rtmpPacket = new Abort(header);
                break;
            case USER_CONTROL_MESSAGE:
                rtmpPacket = new UserControl(header);
                break;
            case WINDOW_ACKNOWLEDGEMENT_SIZE:
                rtmpPacket = new WindowAckSize(header);
                break;
            case SET_PEER_BANDWIDTH:
                rtmpPacket = new SetPeerBandwidth(header);
                break;
            case AUDIO:
                rtmpPacket = new Audio(header);
                break;
            case VIDEO:
                rtmpPacket = new Video(header);
            case COMMAND_AMF0:
                rtmpPacket = new Command(header);
                break;
            case DATA_AMF0:
                rtmpPacket = new Data(header);
                break;
            default:
                throw new IOException("No packet body implementation for message type: " + header.getMessageType());
        }                
        rtmpPacket.readBody(in);                        
        return rtmpPacket;
    }
}
