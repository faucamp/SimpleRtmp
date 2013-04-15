package com.github.faucamp.simplertmp.output;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import com.github.faucamp.simplertmp.util.L;

/**
 * Interface for writing RTMP content streams (audio/video)
 * 
 * @author francois
 */
public class FlvInputStreamWriter extends FlvWriter implements InputStreamWrapper {

    private PipedInputStream inputStream;

    public FlvInputStreamWriter() throws IOException {
        inputStream = new PipedInputStream();
        out = new PipedOutputStream(inputStream);
        writeHeader();
    }
    
    @Override
    public void open(String filename) throws IOException  {
        throw new IOException("Not supported by this writer. Use FlvWriter instead.");
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void close() {
        try {
            inputStream.close();
        } catch (IOException ex) {
            L.e("Failed to close wrapped PipedInputStream", ex);
        }
        super.close();
    }
}
