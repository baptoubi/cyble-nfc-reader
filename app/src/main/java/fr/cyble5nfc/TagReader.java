package fr.cyble5nfc;

import android.nfc.Tag;
import android.nfc.tech.NfcV;

import java.io.IOException;
import java.util.Arrays;

/**
 * Lecture de la mémoire utilisateur d'un tag ISO 15693 (NfcV) par blocs de 4 octets.
 * Commandes standard : READ_SINGLE_BLOCK (0x20) et READ_MULTIPLE_BLOCKS (0x23).
 */
public final class TagReader {

    public static final int BLOCK_SIZE = 4;
    public static final int BLOCK_COUNT = 256; // blocs 0x00 à 0xFF
    private static final int CHUNK = 4;

    private static final int FLAG_HIGH_RATE = 0x02;
    private static final int FLAG_ADDRESSED = 0x20;
    private static final int CMD_READ_SINGLE = 0x20;
    private static final int CMD_READ_MULTIPLE = 0x23;
    private static final int CMD_WRITE_SINGLE = 0x21;

    /** Bloc ISO 15693 (4 octets) contenant le mode radio (adresse 0xC2, voir Decoder/Report). */
    public static final int RADIO_MODE_BLOCK = 0x30;
    /** Décalage de l'octet du mode radio à l'intérieur de ce bloc. */
    public static final int RADIO_MODE_OFFSET = 2;

    public static final class WriteResult {
        public boolean ok;
        public byte[] before; // bloc entier (4 octets) avant écriture
        public byte[] after;  // bloc relu après écriture (peut être null si la relecture échoue)
        public String error = "";
    }

    public static final class Dump {
        public final byte[] memory = new byte[BLOCK_COUNT * BLOCK_SIZE];
        public final boolean[] known = new boolean[BLOCK_COUNT];
        public int blocksRead;
        public String uid = "";
        public String lastError = "";
    }

    private final NfcV nfcv;
    private final byte[] uid;
    private boolean addressed = true;

    public TagReader(Tag tag) {
        this.nfcv = NfcV.get(tag);
        this.uid = tag.getId();
    }

    public boolean isIso15693() { return nfcv != null; }

    public Dump readAll() throws IOException {
        Dump d = new Dump();
        StringBuilder u = new StringBuilder();
        for (int i = uid.length - 1; i >= 0; i--) u.append(String.format("%02X", uid[i] & 0xFF));
        d.uid = u.toString();

        nfcv.connect();
        try {
            // Premier essai en mode adressé (avec l'UID), puis en mode non adressé.
            byte[] probe = readSingle(0);
            if (probe == null) {
                addressed = false;
                probe = readSingle(0);
            }
            if (probe == null) {
                d.lastError = "Le tag ne répond pas à la lecture du bloc 0.";
                return d;
            }
            for (int first = 0; first < BLOCK_COUNT; first += CHUNK) {
                int n = Math.min(CHUNK, BLOCK_COUNT - first);
                byte[] data = readMultiple(first, n);
                if (data == null) {
                    // Repli : lecture bloc par bloc
                    for (int i = 0; i < n; i++) {
                        byte[] one = readSingle(first + i);
                        if (one != null) store(d, first + i, one);
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        store(d, first + i, Arrays.copyOfRange(data, i * BLOCK_SIZE, (i + 1) * BLOCK_SIZE));
                    }
                }
            }
            if (d.blocksRead == 0) d.lastError = "Aucun bloc lisible.";
            return d;
        } finally {
            try { nfcv.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Change le mode radio (ISO 15693 WRITE_SINGLE_BLOCK, commande 0x21, comme l'appli Itron).
     * L'écriture ISO 15693 porte toujours sur un bloc entier de 4 octets : le bloc 0x30 contient
     * aussi l'état du microcontrôleur (0xC0) et le mode de fonctionnement (0xC1). On relit donc
     * le bloc, on ne change que l'octet du mode radio (0xC2, décalage 2), et on réécrit les 4
     * octets en préservant les deux autres. Une relecture après écriture vérifie le résultat.
     */
    public WriteResult writeRadioMode(int newMode) throws IOException {
        WriteResult r = new WriteResult();
        nfcv.connect();
        try {
            byte[] probe = readSingle(0);
            if (probe == null) {
                addressed = false;
                probe = readSingle(0);
            }
            if (probe == null) {
                r.error = "Le tag ne répond pas.";
                return r;
            }

            byte[] block = readSingle(RADIO_MODE_BLOCK);
            if (block == null) {
                r.error = "Lecture du bloc 0x30 (mode radio) impossible.";
                return r;
            }
            r.before = block.clone();

            byte[] newBlock = block.clone();
            newBlock[RADIO_MODE_OFFSET] = (byte) newMode;

            if (!writeSingle(RADIO_MODE_BLOCK, newBlock)) {
                r.error = "Écriture refusée par le tag (bloc verrouillé ou protégé ?).";
                return r;
            }

            byte[] reread = readSingle(RADIO_MODE_BLOCK);
            r.after = reread;
            r.ok = reread != null && Arrays.equals(reread, newBlock);
            if (!r.ok) {
                r.error = "La relecture après écriture ne correspond pas à la valeur envoyée.";
            }
            return r;
        } finally {
            try { nfcv.close(); } catch (IOException ignored) { }
        }
    }

    private static void store(Dump d, int block, byte[] four) {
        System.arraycopy(four, 0, d.memory, block * BLOCK_SIZE, BLOCK_SIZE);
        if (!d.known[block]) { d.known[block] = true; d.blocksRead++; }
    }

    private byte[] request(int cmd, byte... params) throws IOException {
        int flags = FLAG_HIGH_RATE | (addressed ? FLAG_ADDRESSED : 0);
        int uidLen = addressed ? uid.length : 0;
        byte[] frame = new byte[2 + uidLen + params.length];
        frame[0] = (byte) flags;
        frame[1] = (byte) cmd;
        if (addressed) System.arraycopy(uid, 0, frame, 2, uid.length);
        System.arraycopy(params, 0, frame, 2 + uidLen, params.length);
        return nfcv.transceive(frame);
    }

    /** Retourne les octets de données, ou null si le tag répond par une erreur ou ne répond pas. */
    private byte[] readSingle(int block) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                byte[] resp = request(CMD_READ_SINGLE, (byte) block);
                if (resp != null && resp.length == 1 + BLOCK_SIZE && resp[0] == 0) {
                    return Arrays.copyOfRange(resp, 1, resp.length);
                }
                if (resp != null && resp.length >= 1 && (resp[0] & 1) != 0) return null; // erreur du tag
            } catch (IOException e) {
                // nouvel essai
            }
        }
        return null;
    }

    private byte[] readMultiple(int first, int count) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                byte[] resp = request(CMD_READ_MULTIPLE, (byte) first, (byte) (count - 1));
                if (resp != null && resp.length == 1 + count * BLOCK_SIZE && resp[0] == 0) {
                    return Arrays.copyOfRange(resp, 1, resp.length);
                }
                if (resp != null && resp.length >= 1 && (resp[0] & 1) != 0) return null;
            } catch (IOException e) {
                // nouvel essai
            }
        }
        return null;
    }

    /** Écrit un bloc entier de 4 octets (WRITE_SINGLE_BLOCK, 0x21). Retourne false en cas d'erreur du tag. */
    private boolean writeSingle(int block, byte[] fourBytes) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                byte[] params = new byte[5];
                params[0] = (byte) block;
                System.arraycopy(fourBytes, 0, params, 1, 4);
                byte[] resp = request(CMD_WRITE_SINGLE, params);
                if (resp != null && resp.length >= 1 && resp[0] == 0) return true;
                if (resp != null && resp.length >= 1 && (resp[0] & 1) != 0) return false; // erreur explicite, inutile de réessayer
            } catch (IOException e) {
                // nouvel essai
            }
        }
        return false;
    }
}
