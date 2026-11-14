package az.neftcala.xerc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String[] CATS = {
            "Ərzaq", "Nəqliyyat", "Kommunal", "Əyləncə", "Sağlamlıq", "Geyim", "Digər"
    };

    private static class Db extends SQLiteOpenHelper {
        Db(Context c) {
            super(c, "xerc.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE x (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "amount REAL, cat TEXT, note TEXT, ts INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
        }
    }

    private static class Item {
        long id;
        double amount;
        String cat;
        String note;
        long ts;
    }

    private Db db;
    private final List<Item> items = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
    private TextView summary;
    private EditText amountIn;
    private EditText noteIn;
    private Spinner catIn;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Db(this);

        int p = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);

        summary = new TextView(this);
        summary.setTextSize(18);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setPadding(0, 0, 0, dp(12));
        root.addView(summary);

        amountIn = new EditText(this);
        amountIn.setHint("Məbləğ (₼)");
        amountIn.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(amountIn);

        catIn = new Spinner(this);
        ArrayAdapter<String> catAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CATS);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catIn.setAdapter(catAdapter);
        root.addView(catIn);

        noteIn = new EditText(this);
        noteIn.setHint("Qeyd (istəyə görə)");
        noteIn.setSingleLine(true);
        root.addView(noteIn);

        Button add = new Button(this);
        add.setText("Əlavə et");
        add.setOnClickListener(v -> addItem());
        root.addView(add);

        Button stats = new Button(this);
        stats.setText("Bu ay kateqoriyalar üzrə");
        stats.setOnClickListener(v -> showStats());
        root.addView(stats);

        ListView list = new ListView(this);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int pos) {
                return items.get(pos);
            }

            @Override
            public long getItemId(int pos) {
                return items.get(pos).id;
            }

            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
                }
                Item it = items.get(pos);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                t1.setText(money(it.amount) + "  •  " + it.cat);
                String sub = fmt.format(new Date(it.ts));
                if (it.note != null && !it.note.isEmpty()) {
                    sub = sub + "  —  " + it.note;
                }
                t2.setText(sub);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((parent, v, pos, rowId) -> {
            confirmDelete(items.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        reload();
    }

    private void addItem() {
        String s = amountIn.getText().toString().trim().replace(',', '.');
        double amount;
        try {
            amount = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (amount <= 0) {
            Toast.makeText(this, "Məbləği düzgün daxil edin", Toast.LENGTH_SHORT).show();
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put("amount", amount);
        cv.put("cat", CATS[catIn.getSelectedItemPosition()]);
        cv.put("note", noteIn.getText().toString().trim());
        cv.put("ts", System.currentTimeMillis());
        db.getWritableDatabase().insert("x", null, cv);
        amountIn.setText("");
        noteIn.setText("");
        reload();
    }

    private void reload() {
        items.clear();
        double total = 0;
        double month = 0;
        long start = monthStart();
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id, amount, cat, note, ts FROM x ORDER BY ts DESC", null);
        while (c.moveToNext()) {
            Item it = new Item();
            it.id = c.getLong(0);
            it.amount = c.getDouble(1);
            it.cat = c.getString(2);
            it.note = c.getString(3);
            it.ts = c.getLong(4);
            items.add(it);
            total += it.amount;
            if (it.ts >= start) {
                month += it.amount;
            }
        }
        c.close();
        summary.setText("Bu ay: " + money(month) + "     Cəmi: " + money(total));
        adapter.notifyDataSetChanged();
    }

    private void showStats() {
        long start = monthStart();
        double[] sums = new double[CATS.length];
        for (Item it : items) {
            if (it.ts < start) {
                continue;
            }
            for (int i = 0; i < CATS.length; i++) {
                if (CATS[i].equals(it.cat)) {
                    sums[i] += it.amount;
                }
            }
        }
        String[] lines = new String[CATS.length];
        for (int i = 0; i < CATS.length; i++) {
            lines[i] = CATS[i] + ":  " + money(sums[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Bu ay kateqoriyalar üzrə")
                .setItems(lines, null)
                .setPositiveButton("Bağla", null)
                .show();
    }

    private void confirmDelete(Item it) {
        new AlertDialog.Builder(this)
                .setTitle("Silinsin?")
                .setMessage(money(it.amount) + "  •  " + it.cat)
                .setPositiveButton("Sil", (d, w) -> {
                    db.getWritableDatabase().delete("x", "id=?", new String[]{String.valueOf(it.id)});
                    reload();
                })
                .setNegativeButton("Ləğv et", null)
                .show();
    }

    private long monthStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String money(double v) {
        return String.format(Locale.US, "%.2f ₼", v);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
