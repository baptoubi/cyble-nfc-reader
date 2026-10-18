package fr.cyble5nfc;

import java.time.Instant;

/**
 * Décodage de la mémoire NFC d'un Itron Cyble 5.
 * Adresses d'octet et formats relevés dans l'appli Itron NFC.FieldTool.
 * Classe Java pure (aucune dépendance Android) pour pouvoir être testée sur un PC.
 */
public final class Decoder {

    /** Taille minimale de mémoire nécessaire : le dernier bloc décodé est à 0xC2. */
    public static final int MIN_LENGTH = 0xC3;

    private static final long EPOCH_2013 = 1356998400L; // 2013-01-01T00:00:00Z

    public static final class Result {
        public String id;
        public int pulses;
        public int pulseCode;
        public int digits;
        public boolean pulseCodeValid;
        public double m3;
        public int decimals;
        public long dateSeconds;
        public Instant date; // null si 0xFFFFFFFF
        public int alarmsNow;
        public int alarmsDay;
        public int radioMode;
        public boolean mcuEnabled;
        public int meterMode;
        public int creditReload;
        public int creditMute;
        public int nfcErrors;
        public int[] firmware = new int[4];
        public int[] hardware = new int[2];
    }

    private Decoder() { }

    /** Lit le BCD comme l'appli : un octet qui n'est pas du BCD vaut 0. */
    static int bcd(int b) {
        int hi = (b >> 4) & 15, lo = b & 15;
        return (hi > 9 || lo > 9) ? 0 : hi * 10 + lo;
    }

    static long u32(byte[] m, int o) {
        return (m[o] & 0xFFL) | ((m[o + 1] & 0xFFL) << 8) | ((m[o + 2] & 0xFFL) << 16) | ((m[o + 3] & 0xFFL) << 24);
    }

    public static Result decode(byte[] m) {
        if (m == null || m.length < MIN_LENGTH) return null;
        Result r = new Result();

        for (int i = 0; i < 4; i++) r.firmware[i] = bcd(m[0x38 + i] & 0xFF);
        r.hardware[0] = bcd(m[0x3C] & 0xFF);
        r.hardware[1] = bcd(m[0x3D] & 0xFF);

        r.creditReload = m[0x48] & 0xFF;
        r.creditMute = m[0x49] & 0xFF;
        r.nfcErrors = m[0x4A] & 0xFF;

        r.dateSeconds = u32(m, 0xA0);
        r.date = r.dateSeconds == 0xFFFFFFFFL ? null : Instant.ofEpochSecond(EPOCH_2013 + r.dateSeconds);

        r.pulses = (int) u32(m, 0xA4); // entier 32 bits signé, petit-boutiste

        int reg = m[0xA8] & 0xFF;
        r.pulseCode = reg & 15;
        r.digits = reg >> 4;
        r.pulseCodeValid = r.pulseCode >= 1 && r.pulseCode <= 7;
        r.decimals = r.pulseCodeValid ? 7 - r.pulseCode : 0;
        r.m3 = r.pulseCodeValid ? r.pulses * Math.pow(10, r.pulseCode - 7) : Double.NaN;

        r.alarmsNow = m[0xA9] & 0xFF;
        r.alarmsDay = m[0xAA] & 0xFF;

        // 17 octets ASCII, lus à l'envers par l'appli (InvertBytes)
        StringBuilder sb = new StringBuilder();
        for (int i = 16; i >= 0; i--) {
            int c = m[0xAC + i] & 0xFF;
            sb.append(c >= 32 && c < 127 ? (char) c : '?');
        }
        r.id = sb.toString().trim();

        r.mcuEnabled = (m[0xC0] & 0xFF) == 0;
        r.meterMode = m[0xC1] & 0xFF;
        r.radioMode = m[0xC2] & 0xFF;
        return r;
    }
}
