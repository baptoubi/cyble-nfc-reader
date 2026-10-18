import fr.cyble5nfc.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Check {
    public static void main(String[] a) throws Exception {
        String t = Files.readString(Path.of(a[0]));
        Matcher m = Pattern.compile("\\[ (\\w\\w):(\\w\\w):(\\w\\w):(\\w\\w) \\] Addr\\. (\\w+)").matcher(t);
        byte[] mem = new byte[1024];
        while (m.find()) {
            int ad = Integer.parseInt(m.group(5), 16);
            for (int i = 0; i < 4; i++) mem[ad * 4 + i] = (byte) Integer.parseInt(m.group(1 + i), 16);
        }
        Decoder.Result r = Decoder.decode(mem);
        for (Report.Line l : Report.build(r)) System.out.println(l.kind + " | " + l.a + (l.b != null ? " | " + l.b : ""));
        String exp = Report.exportDump(mem);
        System.out.println("roundtrip identical lines: " + exp.lines().limit(254).toList().equals(t.lines().filter(x -> x.startsWith("[")).map(x -> x.trim()).toList()));
        System.out.println(Decoder.decode(new byte[10]));
    }
}
