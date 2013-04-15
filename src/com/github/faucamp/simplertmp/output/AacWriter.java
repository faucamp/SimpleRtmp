package com.github.faucamp.simplertmp.output;

import java.io.IOException;
import java.io.OutputStream;
import com.github.faucamp.simplertmp.Util;
import com.github.faucamp.simplertmp.io.packets.ContentData;
import com.github.faucamp.simplertmp.io.packets.Data;
import com.github.faucamp.simplertmp.io.packets.RtmpHeader;
import com.github.faucamp.simplertmp.util.L;

/**
 * Simple writer class for writing an ADTS-framed AAC audio stream to an OutputStream
 * 
 * Information on the ADTS header structure can be found at:
 * http://wiki.multimedia.cx/index.php?title=ADTS
 * 
 * @author francois
 */
public class AacWriter extends RtmpStreamWriter {

    protected OutputStream out;
    /** AAC profile; default to 2 (AAC-LC) */
    private int aacProfile = 2;
    /** MPEG-4 Sampling Frequency Index. Default: 4 (44100 Hz) */
    private int samplingFrequencyIndex = 4;
    /** MPEG-4 Channel Configuration. Default: 2 (2 channels front-left, front-right (stereo)) */
    private int channelConfiguration = 2;
    /** Shortcut cache - we always use the same values for the first 2 bytes of the ADTS header (syncword, mpeg version, layer and CRC) */
    private static final byte[] ADTS_HEADER_START = new byte[]{(byte) 0xFF, (byte) 0xF1};
    /** Sound format constant for AAC in an FLV tag; see: http://opensource.adobe.com/svn/opensource/osmf/trunk/framework/OSMF/org/osmf/net/httpstreaming/flv/FLVTagAudio.as */
    private static final int SOUND_FORMAT_AAC = 10;
    private boolean sequenceHeaderParsed = false;

    protected AacWriter() {
    }

    public AacWriter(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(Data dataPacket) throws IOException {
        // ignore
    }

    @Override
    public void write(ContentData packet) throws IOException {
        if (packet.getHeader().getMessageType() == RtmpHeader.MessageType.AUDIO) {
            byte[] data = packet.getData();
            if (data.length > 0) {
                if (!sequenceHeaderParsed && data[0] == (byte) 0xAF && data[1] == 0x00) {  // FLV audio sequence header/config frame (starts with 0xAF 0x00)           
                    try {
                        parseFlvSequenceHeader(data);
                    } catch (IllegalArgumentException ex) {
                        // Not valid AAC data
                        throw new IOException(ex.getMessage());
                    }
                    sequenceHeaderParsed = true;
                } else { // AAC raw data (starts with 0xAF 0x01)     
                    writeAdtsFrameHeader(packet);
                    out.write(data, 2, data.length - 2); // skip past 0xAF 0x01 FLV AAC audio tag sub-header
                }
            } else {
                L.w("write(): Zero-length audio data packet found; ignoring");
            }
        }
    }

    /**
     * Parses the 2-byte packet sub-header that is sent with each FLV AAC audio packet (and sets writer state accordingly)
     */
    private void parseFlvSequenceHeader(byte[] data) throws IllegalArgumentException {
        // Sound format: first 4 bits
        final int soundFormat = (data[0] >>> 4) & 0x0f;
        // for sound format constants, see: http://opensource.adobe.com/svn/opensource/osmf/trunk/framework/OSMF/org/osmf/net/httpstreaming/flv/FLVTagAudio.as
        if (soundFormat != SOUND_FORMAT_AAC) {
            throw new IllegalArgumentException("Failed to detect AAC audio stream");
        }
        final int soundRateIndex = (data[0] >>> 2) & 0x03;
        switch (soundRateIndex) { // The FLV sound rate index does not match up to the MPEG-4 sampling frequency index so we do that here:
            case 0: // 5512.5 Hz; not supported
                throw new IllegalArgumentException("Unsupported sound rate: 5512.5 Hz");
            case 1: // 11025 Hz
                samplingFrequencyIndex = 10;
                break;
            case 2: // 22050 Hz
                samplingFrequencyIndex = 7;
                break;
            case 3: // 44100 Hz
                samplingFrequencyIndex = 4;
                break;
            default:
                throw new IllegalArgumentException("Invalid/unknown sound rate index value: " + soundRateIndex + "; full byte: " + Util.toHexString(data[0]));
        }
        L.i("parseFlvSequenceHeader(): AAC sampling frequency index set to: " + samplingFrequencyIndex);
        final int soundSize = (data[0] >>> 1) & 0x01; // 1 == 16bit, 0 == 8 bit (ignore this)
        final int soundChannels = data[0] & 0x01; // 1 == stereo, 0 == mono
        if (soundChannels == 1) {
            channelConfiguration = 2; // 2-channel stereo
        } else {
            channelConfiguration = 1; // mono; 1 channel front-center
        }
        L.i("parseFlvSequenceHeader(): AAC channel configuration set to: " + channelConfiguration);
        final byte aacPacketType = data[1];
        if (aacPacketType == 0x00) { // Sequence header; parse this for proper information
            // Parse AAC configuration (first two bytes of packet
            parseAacAudioSpecificConfig(data);
        } // else raw AAC data; no further information
    }

    /**
     * Parses the AAC configuration that is sent with the FLV sequence header packet
     * See: http://wiki.multimedia.cx/index.php?title=MPEG-4_Audio#Audio_Specific_Config
     */
    private void parseAacAudioSpecificConfig(byte[] data) {
        // Assuming first two bytes is FLV audio packet sub-header, so ignore bytes 0 and 1 (start reading at byte 2)
        // First 5 bits defines the AAC object type        
        aacProfile = (data[2] >>> 3) & 0x1F;
        L.i("parseAacAudioSpecificConfig(): AAC profile set to: " + aacProfile);
        // Next 4 bits: sampling frequence index
        samplingFrequencyIndex = ((data[2] & 0x07) << 1) | ((data[3] & 0x80) >>> 7);
        L.i("parseAacAudioSpecificConfig(): AAC sampling frequency index set to: " + samplingFrequencyIndex);
        // Next 4 bits: channel configuration
        channelConfiguration = ((data[3] & 0x78) >>> 3);
        L.i("parseAacAudioSpecificConfig(): AAC channel configuration set to: " + channelConfiguration);
        // Next 1 bit: frame length flag (unused in this implementation)
        // Next 1 bit: depends on coder (unused in this implementation)
        // Next 1 bit: extension flag (unused in this implementation)
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

    private void writeAdtsFrameHeader(ContentData packet) throws IOException {
        // Calculate frame length
        final int frameLength = 7 + packet.getHeader().getPacketLength() - 2; // includes ADTS header length (7 bytes)

        // Structure (see http://wiki.multimedia.cx/index.php?title=ADTS)
        // What's left: AAAAAAAA AAAABCCD EEFFFFGH HHIJKLMM MMMMMMMM MMMOOOOO OOOOOOPP (QQQQQQQQ QQQQQQQQ) 
        out.write(ADTS_HEADER_START);

        // What's left: EEFFFFGH HHIJKLMM MMMMMMMM MMMOOOOO OOOOOOPP (QQQQQQQQ QQQQQQQQ)
        byte[] bytes = new byte[]{0, 0, 0, 0, (byte) 0xFC};

        // Build EEFFFFGH        
        bytes[0] |= (byte) ((aacProfile - 1) << 6); // AAC profile minus 1 (part: EE)
        bytes[0] |= (byte) (0x3c & (samplingFrequencyIndex << 2)); // Sampling freq index (part: FFFF)
        // part G is 0
        bytes[0] |= (byte) (0xff & channelConfiguration) >>> 2; // MSB of channel config (part: H)

        // Build HHIJKLMM
        bytes[1] |= (byte) (channelConfiguration << 6); // LSBs of channel config (part: HH)
        // parts I, J, K and L are 0
        bytes[1] |= (byte) (0x1FFF & frameLength >>> 11); // 2 MSBs of frame length (part: MM)

        // Build MMMMMMMM       
        bytes[2] |= (byte) (0xFF & (frameLength >> 3)); // 8 middle bits of frame length (part: MMMMMMMM)

        // Build MMMOOOOO        
        bytes[3] |= (byte) ((0x07 & frameLength) << 5); // 3 LSBs of frame length (part: MMM)
        bytes[3] |= 0x1F; // Buffer fullness set to 1s (part OOOOO)

        // Build OOOOOOPP 
        //byte oooooopp = (byte) 0xFC; // Buffer fullness (part OOOOOO) - already in array since it's constant in this implementatino
        // PP left as 0, as it is the number of AAC frames in ADTS frame, minus 1 (thus 1 AAC frame for this ADTS frame)

        out.write(bytes);
    }
}
