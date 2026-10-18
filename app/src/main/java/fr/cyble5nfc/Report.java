package fr.cyble5nfc;

import java.text.NumberFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Transforme un résultat de décodage en lignes d'affichage (texte seulement, sans Android). */
public final class Report {

    public enum Kind { HEADING, ROW, ALARM_ON, ALARM_OFF, NOTE }

    public static final class Line {
        public final Kind kind;
        public final String a;
        public final String b;
        Line(Kind kind, String a, String b) { this.kind = kind; this.a = a; this.b = b; }
    }

    private static final String[][] ALARMS_NOW = {
        {"Sens inverse", "Écoulement à l'envers détecté (montage inversé ou retour d'eau)."},
        {"Compteur bloqué", "Aucune rotation détectée alors qu'un écoulement est attendu."},
        {"Pile faible", "La capacité restante de la pile est basse."},
        {"Défaut de détection", "Le capteur de comptage ne fournit plus de signal fiable."},
        {"Fraude magnétique", "Un champ magnétique fort a été détecté près du compteur."},
        {"Démontage mémorisé", "Le retrait du module a été détecté et conservé en mémoire."},
        {"Crédit wM-Bus épuisé", "Le crédit d'émissions radio wM-Bus est consommé."},
        {"Crédit NFC épuisé", "Le crédit de lectures NFC est consommé."},
    };
    private static final String[][] ALARMS_DAY = {
        {"Retour d'eau (jour)", "Écoulement inverse constaté sur la journée."},
        {"Fuite (jour)", "Écoulement continu sur la journée, signe d'une fuite."},
        {"Débit de pointe (jour)", "Le seuil de débit maximal a été dépassé dans la journée."},
        {"Eau trop chaude (jour)", "Température de l'eau au-dessus du seuil configuré."},
        {"Eau trop froide (jour)", "Température de l'eau sous le seuil configuré."},
        {"Synchro horaire LoRaWAN", "La synchronisation de l'heure par le réseau LoRaWAN a échoué."},
    };
    private static final String[] RADIO = {
        "Mobile seul", "Mobile + OMS", "Mobile + LoRaWAN", "Mobile + Sigfox", "Mobile + cellulaire",
        "Aucune radio", "EquaScan V1", "EquaScan V2", "OMS (wM-Bus)", "LoRaWAN",
        "EquaScan V1 + LoRaWAN", "EquaScan V2 + LoRaWAN",
    };
    private static final String[] MODE = {
        "Arrêt", "Veille avant arrêt", "En service", "Veille avant mode muet", "Mode muet", "Retour usine (RMA)",
    };

    private Report() { }

    private static String radioName(int v) { return v < RADIO.length ? RADIO[v] : "code inconnu " + v; }
    private static String modeName(int v) { return v < MODE.length ? MODE[v] : "code inconnu " + v; }

    private static String num(double v, int dec) {
        NumberFormat f = NumberFormat.getInstance(Locale.FRANCE);
        f.setMinimumFractionDigits(dec);
        f.setMaximumFractionDigits(dec);
        return f.format(v);
    }

    public static List<Line> build(Decoder.Result r) {
        List<Line> out = new ArrayList<>();

        out.add(new Line(Kind.HEADING, "Compteur", null));
        out.add(new Line(Kind.ROW, "Identifiant", r.id.isEmpty() ? "(vide)" : r.id));
        if (r.pulseCodeValid) {
            out.add(new Line(Kind.ROW, "Index", num(r.m3, r.decimals) + " m³"));
            out.add(new Line(Kind.ROW, "Impulsions",
                NumberFormat.getInstance(Locale.FRANCE).format(r.pulses) + " × " + num(Math.pow(10, r.pulseCode - 7), r.decimals) + " m³"));
        } else {
            out.add(new Line(Kind.ROW, "Index (impulsions)", NumberFormat.getInstance(Locale.FRANCE).format(r.pulses)));
            out.add(new Line(Kind.NOTE, "Code de poids d'impulsion invalide : " + r.pulseCode, null));
        }
        out.add(new Line(Kind.ROW, "Chiffres du registre", String.valueOf(r.digits)));
        String date = r.date == null ? "Non définie"
            : DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.FRANCE).withZone(ZoneOffset.UTC).format(r.date) + " UTC";
        out.add(new Line(Kind.ROW, "Date et heure interne", date));
        out.add(new Line(Kind.ROW, "Firmware", r.firmware[0] + "." + r.firmware[1] + "." + r.firmware[2] + "." + r.firmware[3]));
        out.add(new Line(Kind.ROW, "Matériel", r.hardware[0] + "." + r.hardware[1]));

        out.add(new Line(Kind.HEADING, "Alarmes instantanées", null));
        for (int i = 0; i < ALARMS_NOW.length; i++) {
            boolean on = ((r.alarmsNow >> i) & 1) == 1;
            out.add(new Line(on ? Kind.ALARM_ON : Kind.ALARM_OFF, ALARMS_NOW[i][0], ALARMS_NOW[i][1]));
        }
        out.add(new Line(Kind.HEADING, "Alarmes quotidiennes", null));
        for (int i = 0; i < ALARMS_DAY.length; i++) {
            boolean on = ((r.alarmsDay >> i) & 1) == 1;
            out.add(new Line(on ? Kind.ALARM_ON : Kind.ALARM_OFF, ALARMS_DAY[i][0], ALARMS_DAY[i][1]));
        }
        out.add(new Line(Kind.NOTE, "Le décalage des alarmes quotidiennes (octet 0xAA) est déduit du code de l'appli Itron, sans lecture sur un compteur réel.", null));

        out.add(new Line(Kind.HEADING, "Communication", null));
        out.add(new Line(Kind.ROW, "Mode radio", radioName(r.radioMode) + " (code " + r.radioMode + ")"));
        out.add(new Line(Kind.ROW, "Mode de fonctionnement", modeName(r.meterMode) + " (code " + r.meterMode + ")"));
        out.add(new Line(Kind.ROW, "Accès NFC du microcontrôleur", r.mcuEnabled ? "Activé" : "Désactivé"));
        out.add(new Line(Kind.ROW, "Crédit de rechargement quotidien", String.valueOf(r.creditReload)));
        out.add(new Line(Kind.ROW, "Crédits du mode muet", String.valueOf(r.creditMute)));
        out.add(new Line(Kind.ROW, "Compteur d'erreurs NFC", String.valueOf(r.nfcErrors)));
        out.add(new Line(Kind.NOTE, "Les périodes d'émission, adresses radio et paramètres LoRaWAN ou OMS ne sont pas dans la table d'adresses connue.", null));
        return out;
    }

    /** Dump au format « [ 42:20:00:00 ] Addr. 08 : DATA », lisible par la page web Décodeur Cyble 5. */
    public static String exportDump(byte[] mem) {
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a * 4 + 3 < mem.length; a++) {
            sb.append(String.format(Locale.ROOT, "[ %02X:%02X:%02X:%02X ] Addr. %02X : DATA\n",
                mem[a * 4] & 0xFF, mem[a * 4 + 1] & 0xFF, mem[a * 4 + 2] & 0xFF, mem[a * 4 + 3] & 0xFF, a));
        }
        return sb.toString();
    }
}
