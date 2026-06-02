import java.io.*;
import java.net.InetSocketAddress;
import java.util.Base64;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class GenerateSimpleNesDemo {
    public static void main(String[] args) throws Exception {
        boolean noSave = false;
        int servePort = -1;
        boolean printB64 = false;
        boolean autoRun = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--no-save".equals(a)) noSave = true;
            else if ("--print-b64".equals(a)) printB64 = true;
            else if ("--auto-run".equals(a)) autoRun = true;
            else if ("--serve".equals(a) && i + 1 < args.length) {
                servePort = Integer.parseInt(args[++i]);
            }
        }

        byte[] header = new byte[16];
        header[0] = 'N'; header[1] = 'E'; header[2] = 'S'; header[3] = 0x1A;
        header[4] = 1; // 1 PRG bank

        byte[] prg = new byte[16 * 1024];
        int[] code = new int[]{
            0x78,0xD8,0xA2,0x00,0xA9,0x00,0x8D,0x00,0x20,0xA9,0x18,0x8D,0x01,0x20,
            0xA9,0x3F,0x8D,0x06,0x20,0xA9,0x00,0x8D,0x06,0x20,0xA9,0x0F,0x8D,0x07,0x20,
            0xA9,0x01,0x8D,0x07,0x20,0xA9,0x11,0x8D,0x07,0x20,0xA9,0x21,0x8D,0x07,0x20,
            0xA9,0x0F,0x8D,0x07,0x20,0xA9,0x16,0x8D,0x07,0x20,0xA9,0x26,0x8D,0x07,0x20,
            0xA9,0x36,0x8D,0x07,0x20,0xA9,0x05,0x8D,0x07,0x20,
            0xA9,0x00,0x8D,0x06,0x20,0xA9,0x00,0x8D,0x06,0x20,0xA2,0x10,0xA9,0xFF,0x8D,0x07,0x20,
            0xCA,0xD0,0xFD,0xA2,0x10,0xA9,0x00,0x8D,0x07,0x20,0xCA,0xD0,0xFD,
            0xA9,0x00,0x8D,0x06,0x20,0xA9,0x02,0x8D,0x06,0x20,0xA2,0x10,0xA9,0xAA,0x8D,0x07,0x20,
            0xCA,0xD0,0xFD,0xA2,0x10,0xA9,0x55,0x8D,0x07,0x20,0xCA,0xD0,0xFD,
            0xA9,0x20,0x8D,0x06,0x20,0xA9,0x00,0x8D,0x06,0x20,0xA2,0x20,0xA9,0x00,0x8D,0x07,0x20,
            0xCA,0xD0,0xFB,0xA9,0x23,0x8D,0x06,0x20,0xA9,0xC0,0x8D,0x06,0x20,0xA2,0x08,0xA9,0x00,0x8D,0x07,0x20,
            0xCA,0xD0,0xFB,0x4C,0x00,0x80
        };

        for (int i = 0; i < code.length && i < prg.length; i++) prg[i] = (byte)code[i];
        int vecOff = prg.length - 4;
        prg[vecOff] = 0x00;
        prg[vecOff + 1] = (byte)0x80;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(header);
        baos.write(prg);
        byte[] rom = baos.toByteArray();

        if (noSave) {
            if (autoRun) {
                if (tryAutoRun(rom)) return;
            }
            if (servePort > 0) {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", servePort), 0);
                server.createContext("/simple-demo.nes", new HttpHandler() {
                    public void handle(HttpExchange exchange) throws IOException {
                        byte[] data = rom;
                        exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                        exchange.sendResponseHeaders(200, data.length);
                        try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
                    }
                });
                server.setExecutor(null);
                server.start();
                System.out.println("Serving ROM at http://127.0.0.1:" + servePort + "/simple-demo.nes");
                System.out.println("Press Ctrl-C to stop");
                // keep main thread alive
                Thread.currentThread().join();
                return;
            }
            if (printB64) {
                System.out.println(Base64.getEncoder().encodeToString(rom));
                return;
            }
            // if no flags besides --no-save, default to printing info
            System.out.println("ROM generated in memory (use --print-b64 or --serve).");
            return;
        }

        // Default: write to assets
        File out = new File("app/src/main/assets/simple-demo.nes");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(rom); }
        System.out.println("Wrote " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
    }

    static boolean tryAutoRun(byte[] rom) {
        String[] candidates = new String[]{
            "Emulator",
            "NesEmulator",
            "emu.Emulator",
            "com.example.emulator.Emulator",
            "org.nes.Emulator"
        };
        for (String clsName : candidates) {
            try {
                Class<?> cls = Class.forName(clsName);
                try {
                    java.lang.reflect.Method m = cls.getMethod("runRomBytes", byte[].class);
                    if ((m.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0) {
                        m.invoke(null, (Object)rom);
                        System.out.println("Ran ROM via " + clsName + ".runRomBytes(byte[])");
                        return true;
                    }
                } catch (NoSuchMethodException e) { }
                try {
                    java.lang.reflect.Method m2 = cls.getMethod("run", byte[].class);
                    if ((m2.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0) {
                        m2.invoke(null, (Object)rom);
                        System.out.println("Ran ROM via " + clsName + ".run(byte[])");
                        return true;
                    }
                } catch (NoSuchMethodException e) { }
            } catch (ClassNotFoundException e) {
                // ignore
            } catch (Exception e) {
                System.out.println("Error trying to auto-run with " + clsName + ": " + e);
            }
        }
        System.out.println("No known emulator class found on classpath to auto-run.");
        return false;
    }
}
