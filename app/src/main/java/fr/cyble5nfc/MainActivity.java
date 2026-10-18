package fr.cyble5nfc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private NfcAdapter nfc;
    private TextView status;
    private LinearLayout content;
    private Button copyBtn;
    private Button shareBtn;
    private String lastDump = "";

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Lecteur Cyble 5");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        copyBtn = new Button(this);
        copyBtn.setText("Copier le dump");
        copyBtn.setEnabled(false);
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("dump Cyble 5", lastDump));
                Toast.makeText(MainActivity.this, "Dump copié", Toast.LENGTH_SHORT).show();
            }
        });
        shareBtn = new Button(this);
        shareBtn.setText("Partager");
        shareBtn.setEnabled(false);
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, lastDump);
                startActivity(Intent.createChooser(send, "Partager le dump"));
            }
        });
        buttons.addView(copyBtn);
        buttons.addView(shareBtn);
        root.addView(buttons);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        setContentView(scroll);
        nfc = NfcAdapter.getDefaultAdapter(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc == null) {
            status.setText("Ce téléphone n'a pas de NFC.");
            return;
        }
        if (!nfc.isEnabled()) {
            status.setText("Le NFC est désactivé. Active-le dans les réglages, puis reviens ici.");
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            return;
        }
        status.setText("Pose le dos du téléphone sur le compteur (zone NFC) et garde-le immobile jusqu'à l'affichage.");
        nfc.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) nfc.disableReaderMode(this);
    }

    /** Appelé hors du fil principal quand un tag est détecté. */
    @Override
    public void onTagDiscovered(Tag tag) {
        final TagReader reader = new TagReader(tag);
        if (!reader.isIso15693()) {
            runOnUiThread(new Runnable() {
                @Override public void run() { status.setText("Ce tag n'est pas de type ISO 15693."); }
            });
            return;
        }
        runOnUiThread(new Runnable() {
            @Override public void run() { status.setText("Lecture en cours, ne bouge pas…"); }
        });
        try {
            final TagReader.Dump dump = reader.readAll();
            final Decoder.Result res = Decoder.decode(dump.memory);
            final String exported = Report.exportDump(dump.memory);
            runOnUiThread(new Runnable() {
                @Override public void run() { show(dump, res, exported); }
            });
        } catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    status.setText("Lecture interrompue (" + e.getClass().getSimpleName()
                        + "). Repose le téléphone sans bouger.");
                }
            });
        }
    }

    private void show(TagReader.Dump dump, Decoder.Result res, String exported) {
        content.removeAllViews();
        if (dump.blocksRead == 0 || res == null) {
            status.setText(dump.lastError.isEmpty() ? "Lecture impossible." : dump.lastError);
            return;
        }
        lastDump = exported;
        copyBtn.setEnabled(true);
        shareBtn.setEnabled(true);
        boolean complete = dump.blocksRead == TagReader.BLOCK_COUNT;
        status.setText("UID " + dump.uid + " — " + dump.blocksRead + " blocs lus sur " + TagReader.BLOCK_COUNT
            + (complete ? "" : " (certains blocs sont inaccessibles, les valeurs correspondantes valent 0)"));
        List<Report.Line> lines = Report.build(res);
        for (Report.Line l : lines) {
            switch (l.kind) {
                case HEADING:
                    TextView h = text(l.a, 18, Typeface.DEFAULT_BOLD, Color.BLACK);
                    h.setPadding(0, dp(20), 0, dp(4));
                    content.addView(h);
                    break;
                case ROW:
                    content.addView(text(l.a, 12, Typeface.DEFAULT, 0xFF666666));
                    TextView v = text(l.b, 17, Typeface.DEFAULT, Color.BLACK);
                    v.setPadding(0, 0, 0, dp(6));
                    content.addView(v);
                    break;
                case ALARM_ON:
                    TextView on = text("ACTIVE  " + l.a + "\n" + l.b, 15, Typeface.DEFAULT_BOLD, 0xFFB3261E);
                    on.setBackgroundColor(0xFFFBE9E7);
                    on.setPadding(dp(8), dp(6), dp(8), dp(6));
                    content.addView(on);
                    break;
                case ALARM_OFF:
                    TextView off = text("inactive  " + l.a, 15, Typeface.DEFAULT, 0xFF1F7A4D);
                    off.setPadding(dp(8), dp(3), dp(8), dp(3));
                    content.addView(off);
                    break;
                default:
                    TextView n = text(l.a, 12, Typeface.DEFAULT, 0xFF666666);
                    n.setPadding(0, dp(6), 0, dp(6));
                    content.addView(n);
            }
        }
    }

    private TextView text(String s, int sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }
}
