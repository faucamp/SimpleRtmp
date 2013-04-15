package simplertmp;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import com.github.faucamp.simplertmp.DefaultRtmpClient;
import com.github.faucamp.simplertmp.RtmpClient;
import com.github.faucamp.simplertmp.io.RtmpConnection;
import com.github.faucamp.simplertmp.output.AacWriter;
import com.github.faucamp.simplertmp.output.GenericAudioWriter;
import com.github.faucamp.simplertmp.output.MultiWriter;
import com.github.faucamp.simplertmp.output.RawAudioWriter;
import com.github.faucamp.simplertmp.output.RawOutputStreamWriter;
import com.github.faucamp.simplertmp.util.L;

/**
 *
 * @author francois
 */
public class SimpleRtmp {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        L.d("SimpleRtmp.main() starting");
        System.out.println("starting");


//        String host = "216.246.37.52";
//        int port = 1935;
//        String app = "tuksfm";
//        String swfUrl = "http://infant.antfarm.co.za/tuksfm/flowplayer/flowplayer.commercial-3.1.0.swf?0.45712235092196496";
//        String pageUrl = "http://infant.antfarm.co.za";
//        String playPath = "tuksfm.stream";


//        String url = "rtmp://216.246.37.52/tuksfm?tuksfm.stream";
        String url = "rtmp://127.0.0.1/oflaDemo?aviici.mp3";



//        String host = "127.0.0.1";
//        String app = "oflaDemo";
//        String playPath = "01.m4a";
//          String playPath = "test.mp3";

        //RtmpConnection rtmpConnection = new RtmpConnection("127.0.0.1", 1935, "oflaDemo", "test.mp3");
        DefaultRtmpClient rtmpClient = new DefaultRtmpClient(url);
        try {
            rtmpClient.connect();
        } catch (IOException ex) {
            L.e("SimpleRtmp.main(): exception", ex);
        }

        L.d("SimpleRtmp.main(): playing using blocking outputwriter");
        FileOutputStream out;
        try {
            out = new FileOutputStream("/tmp/aaa.test");
        } catch (FileNotFoundException ex) {
            L.e("ERROR", ex);
            return;
        }

        try {
            //rtmpClient.play(playPath, out, false);
//            rtmpClient.play(new AacWriter(out));
            System.out.println("PLAYING");
            rtmpClient.playAsync(new GenericAudioWriter(out));
            
            try {
                System.out.println("SLEEPING");
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            
//            System.out.println("PAUSING RTMP STREAM");
//            rtmpClient.closeStream();
            
            
//            try {
//                System.out.println("SLEEPING");
//                Thread.sleep(3000);
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//            }
//            
//            System.out.println("RESUMING RTMP STREAM");
//            rtmpClient.pause();

//            try {
//                System.out.println("SLEEPING");
//                Thread.sleep(1000);
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//            }
//
//            System.out.println("CLOSING RTMP STREAM");
//            rtmpClient.closeStream();

            try {
                System.out.println("SLEEPING");
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }



            System.out.println("done");



        } catch (IOException ex) {
            L.e("main(): IOException while playing audio");
        }


//        try {
//            InputStream is = rtmpClient.play(playPath, false);
//            
//            FileOutputStream fos = new FileOutputStream("/tmp/raw_dump.flv");            
//            int b;
//            while ((b = is.read()) != -1) {
//                fos.write(b);
//            }
//            System.out.println("********* CLOSING fos");
//            fos.close();
//            
//            
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }

        L.d("SimpleRtmp.main(): shutting down RTMP client");
        rtmpClient.shutdown();

        L.d("SimpleRtmp.main(): returning");
    }
}
