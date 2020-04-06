import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class EtherDream {
    public static native int etherdream_lib_start();
    public static native int etherdream_dac_count();
    public static native Pointer etherdream_get(int index);
    public static native int etherdream_get_id(Pointer dac);
    public static native int etherdream_connect(Pointer dac);
    public static native int etherdream_is_ready(Pointer dac);
    public static native int etherdream_wait_for_ready(Pointer dac);
    public static native int etherdream_write(Pointer dac, short[] points, int num, int pps, int repeatCount);
    public static native int etherdream_stop(Pointer dac);
    
    static {
        Native.register("etherdream");
    }
}
