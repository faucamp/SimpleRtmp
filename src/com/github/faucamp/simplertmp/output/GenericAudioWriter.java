package com.github.faucamp.simplertmp.output;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import com.github.faucamp.simplertmp.io.packets.ContentData;
import com.github.faucamp.simplertmp.io.packets.Data;
import com.github.faucamp.simplertmp.io.packets.RtmpHeader;
import com.github.faucamp.simplertmp.util.L;

/**
 * Detects the audio stream type and writes the audio data to the specified OutputStream.
 * 
 * Formats currently supported: MP3 and AAC
 * 
 * @author francois
 */
public class GenericAudioWriter extends RtmpStreamWriter {

    public static enum AudioFormat {

        /** Sound format constant for MP3 in an FLV tag; see: http://opensource.adobe.com/svn/opensource/osmf/trunk/framework/OSMF/org/osmf/net/httpstreaming/flv/FLVTagAudio.as */
        MP3(2),
        /** Sound format constant for AAC in an FLV tag; see: http://opensource.adobe.com/svn/opensource/osmf/trunk/framework/OSMF/org/osmf/net/httpstreaming/flv/FLVTagAudio.as */
        AAC(10);
        private int flvValue;
        private static final Map<Integer, AudioFormat> quickLookupMap = new HashMap<Integer, AudioFormat>();

        static {
            for (AudioFormat soundFormat : AudioFormat.values()) {
                quickLookupMap.put(soundFormat.getFlvValue(), soundFormat);
            }
        }

        private AudioFormat(int flvValue) {
            this.flvValue = flvValue;
        }

        /** Returns the FLV sub-header integer value of this sound format */
        public int getFlvValue() {
            return flvValue;
        }

        public static AudioFormat valueOf(int flvValue) {
            if (quickLookupMap.containsKey(flvValue)) {
                return quickLookupMap.get(flvValue);
            } else {
                throw new IllegalArgumentException("Unknown sound format FLV value: " + flvValue);
            }
        }
    }
    /** The wrapped audio format-specific writer */
    private RtmpStreamWriter delegateWriter;
    protected OutputStream out;
    private AudioFormat audioFormat;

    protected GenericAudioWriter() {
    }

    public GenericAudioWriter(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(Data dataPacket) throws IOException {
        // TODO: parse metadata to determine delegate writer type here
    }

    /** @return the audio format, or <code>null</code> if not yet known */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    @Override
    public void write(ContentData packet) throws IOException {
        try {
            delegateWriter.write(packet);
        } catch (NullPointerException ex) { // will happen on first attempt (before delegate writer has been initialized
            if (packet.getHeader().getMessageType() == RtmpHeader.MessageType.AUDIO) {
                byte[] data = packet.getData();
                if (data.length > 0) {
                    try {
                        parseFlvSequenceHeader(packet.getData());
                    } catch (UnsupportedOperationException ex2) {
                        throw new IOException(ex2.getMessage());
                    }
                    delegateWriter.write(packet);
                } else {
                    L.w("write(): Zero-length audio data packet found; ignoring");
                }
            }
        }
    }

    /**
     * Parses the 2-byte packet sub-header that is sent with each FLV audio packet (and sets writer state accordingly)
     */
    protected void parseFlvSequenceHeader(byte[] data) throws UnsupportedOperationException, IOException {
        // Sound format: first 4 bits
        final int soundFormatInt = (data[0] >>> 4) & 0x0f;
        try {
            audioFormat = AudioFormat.valueOf(soundFormatInt);
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedOperationException("Sound format not supported: " + soundFormatInt);
        }
        switch (audioFormat) {
            case AAC:
                delegateWriter = new AacWriter(out);
                break;
            case MP3:
                delegateWriter = new Mp3Writer(out);
                break;
            default:
                throw new UnsupportedOperationException("Sound format not supported: " + audioFormat);
        }
    }
}
