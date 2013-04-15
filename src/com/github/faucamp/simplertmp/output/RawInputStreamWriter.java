package com.github.faucamp.simplertmp.output;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import com.github.faucamp.simplertmp.util.L;

/**
 * Simple writer class providing raw RTMP packets via an InputStream
 * 
 * @author francois
 */
public class RawInputStreamWriter extends RawOutputStreamWriter implements InputStreamWrapper  {

    private PipedInputStream inputStream;

    public RawInputStreamWriter() throws IOException {
        inputStream = new PipedInputStream();
        out = new PipedOutputStream(inputStream);
    }

    @Override
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
