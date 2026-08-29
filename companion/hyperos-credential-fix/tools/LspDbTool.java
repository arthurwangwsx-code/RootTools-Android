import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** One-shot root-side helper used only to enable/scope the local LSPosed hotfix module. */
public final class LspDbTool {
    private static final String DB = "/data/adb/lspd/config/modules_config.db";
    private static final String MODULE = "com.arthur.hyperos.credentialfix";
    private static final String TARGET = "com.android.credentialmanager";
    private static final int TARGET_USER = 999;

    public static void main(String[] args) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(DB, null, SQLiteDatabase.OPEN_READWRITE);
        db.beginTransaction();
        try {
            long mid = -1;
            try (Cursor cursor = db.rawQuery(
                    "SELECT mid FROM modules WHERE module_pkg_name=?",
                    new String[]{MODULE})) {
                if (cursor.moveToFirst()) {
                    mid = cursor.getLong(0);
                }
            }
            if (mid < 0) {
                throw new IllegalStateException("LSPosed module row not found: " + MODULE);
            }

            ContentValues moduleValues = new ContentValues();
            moduleValues.put("enabled", 1);
            if (db.update("modules", moduleValues, "mid=?", new String[]{Long.toString(mid)}) != 1) {
                throw new IllegalStateException("Failed to enable module row " + mid);
            }

            db.delete("scope", "mid=?", new String[]{Long.toString(mid)});
            ContentValues scopeValues = new ContentValues();
            scopeValues.put("mid", mid);
            scopeValues.put("app_pkg_name", TARGET);
            scopeValues.put("user_id", TARGET_USER);
            if (db.insertOrThrow("scope", null, scopeValues) < 0) {
                throw new IllegalStateException("Failed to insert scope");
            }

            db.setTransactionSuccessful();
            System.out.println("configured mid=" + mid + " enabled=1 scope=" + TARGET + " user=" + TARGET_USER);
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
