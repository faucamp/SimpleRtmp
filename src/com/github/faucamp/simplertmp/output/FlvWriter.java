package com.github.faucamp.simplertmp.output;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import com.github.faucamp.simplertmp.Util;
import com.github.faucamp.simplertmp.io.packets.ContentData;
import com.github.faucamp.simplertmp.io.packets.Data;
import com.github.faucamp.simplertmp.io.packets.RtmpHeader;
import com.github.faucamp.simplertmp.util.L;

public class FlvWriter extends RtmpStreamWriter {

    // Signature == "FLV
    private static final byte[] HEADER_SIGNATURE = new byte[]{(byte) 0x46, (byte) 0x4C, (byte) 0x56};
    private static final byte HEADER_VERSION = 0x01; // version 1
    private static final byte HEADER_FLAGS = 0x05; // flags: 5 is audio+video
    private static final int HEADER_SIZE = 9; // always 9 for known FLV files    
    /** 4 zeros for reserved blocks */
    private static final byte[] RESERVED = new byte[]{0, 0, 0, 0};
    protected OutputStream out;

    public FlvWriter() {
    }
    
    public FlvWriter(OutputStream out) throws IOException {
        this.out = out;
        writeHeader();
    }

    public void open(String filename) throws FileNotFoundException, IOException {
        out = new BufferedOutputStream(new FileOutputStream(filename));
        writeHeader();
    }

    @Override
    public void close() {
        super.close();
        try {
            out.close();
        } catch (Exception e) {
            L.e("Caught exception while attempting to close output stream", e);
        }
    }

    protected void writeHeader() throws IOException {
        out.write(HEADER_SIGNATURE);
        out.write(HEADER_VERSION);
        out.write(HEADER_FLAGS);
        Util.writeUnsignedInt32(out, HEADER_SIZE);
        // Write "previous" tag size (== 0 because no tags have been written yet)
        Util.writeUnsignedInt32(out, 0);
    }
    
    @Override
    public void write(Data dataPacket) throws IOException {
        final RtmpHeader header = dataPacket.getHeader();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(header.getPacketLength());
        dataPacket.writeBody(baos);
        write(header.getMessageType(), baos.toByteArray(), header.getAbsoluteTimestamp());
    }

    @Override
    public void write(ContentData packet) throws IOException {
        final RtmpHeader header = packet.getHeader();
        write(header.getMessageType(), packet.getData(), header.getAbsoluteTimestamp());
    }

    private void write(final RtmpHeader.MessageType packetType, final byte[] data, final int packetTimestamp) throws IOException {
        out.write(packetType.getValue());
        // Write packet size
        Util.writeUnsignedInt24(out, data.length);
        // Write absolute time
        Util.writeUnsignedInt24(out, packetTimestamp);
        // Write reserved block
        out.write(RESERVED);
        // Write actual data
        out.write(data);

        // Now write previous tag size
        Util.writeUnsignedInt32(out, data.length + 11);
    }
}
