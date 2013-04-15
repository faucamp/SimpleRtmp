package com.github.faucamp.simplertmp.output;

import java.io.IOException;
import java.io.OutputStream;
import com.github.faucamp.simplertmp.Util;
import com.github.faucamp.simplertmp.io.packets.ContentData;
import com.github.faucamp.simplertmp.io.packets.Data;
import com.github.faucamp.simplertmp.io.packets.RtmpHeader;
import com.github.faucamp.simplertmp.io.packets.RtmpHeader.ChunkType;
import com.github.faucamp.simplertmp.io.packets.RtmpPacket;
import com.github.faucamp.simplertmp.util.L;

/**
 * Simple writer class for piping raw RTMP packets to an OutputStream
 * 
 * @author francois
 */
public class RawOutputStreamWriter extends RtmpStreamWriter {

    protected OutputStream out;

    protected RawOutputStreamWriter() {
    }

    public RawOutputStreamWriter(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(Data dataPacket) throws IOException {
        writeHeader(dataPacket);
        dataPacket.writeBody(out);
    }

    @Override
    public void write(ContentData packet) throws IOException {
        writeHeader(packet);
        packet.writeBody(out);
    }

    @Override
    public void close() {
        super.close();
        try {
            out.close();
        } catch (IOException ex) {
            L.e("Failed to close wrapped OutputStream", ex);
        }
    }

    private void writeHeader(RtmpPacket packet) throws IOException {
        RtmpHeader header = packet.getHeader();
        // Write basic header byte        
        ChunkType chunkType = header.getChunkType();
        out.write(((byte) (chunkType.getValue() << 6) | header.getChunkStreamId()));
        switch (chunkType) {
            case TYPE_0_FULL: { //  b00 = 12 byte header (full header)                
                Util.writeUnsignedInt24(out, header.getAbsoluteTimestamp());
                Util.writeUnsignedInt24(out, header.getPacketLength());
                out.write(header.getMessageType().getValue());
                Util.writeUnsignedInt32LittleEndian(out, header.getMessageStreamId());
                break;
            }
            case TYPE_1_RELATIVE_LARGE: { // b01 = 8 bytes - like type 0. not including message ID (4 last bytes)
                Util.writeUnsignedInt24(out, header.getTimestampDelta());
                Util.writeUnsignedInt24(out, header.getPacketLength());
                out.write(header.getMessageType().getValue());
                break;
            }
            case TYPE_2_RELATIVE_TIMESTAMP_ONLY: { // b10 = 4 bytes - Basic Header and timestamp (3 bytes) are included                
                Util.writeUnsignedInt24(out, header.getTimestampDelta());
                break;
            }
            case TYPE_3_RELATIVE_SINGLE_BYTE: { // b11 = 1 byte: basic header only                
                break;
            }
            default:
                throw new IOException("Invalid chunk type: " + chunkType);
        }
    }
}
