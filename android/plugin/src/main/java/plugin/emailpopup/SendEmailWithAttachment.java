package plugin.emailpopup;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.text.Html;

import androidx.core.content.FileProvider;

import com.ansca.corona.CoronaEnvironment;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaType;
import com.naef.jnlua.NamedJavaFunction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SendEmailWithAttachment implements NamedJavaFunction {

    @Override
    public String getName() {
        return "_show";
    }

    @Override
    public int invoke(LuaState L) {
        if (!L.isTable(1)) return 0;

        final Activity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) return 0;

        final String[] to = readStringOrArray(L, 1, "to");
        final String[] cc = readStringOrArray(L, 1, "cc");
        final String[] bcc = readStringOrArray(L, 1, "bcc");
        final String subject = readString(L, 1, "subject");
        final String body = readString(L, 1, "body");
        final boolean isBodyHtml = readBoolean(L, 1, "isBodyHtml");
        final List<Attachment> attachments = readAttachments(L, 1, "attachment");

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                launch(activity, to, cc, bcc, subject, body, isBodyHtml, attachments);
            }
        });

        return 0;
    }

    private static void launch(Activity activity, String[] to, String[] cc, String[] bcc,
                               String subject, String body, boolean isBodyHtml,
                               List<Attachment> attachments) {
        ArrayList<Uri> uris = new ArrayList<>();
        String authority = activity.getPackageName() + ".fileprovider";

        for (Attachment a : attachments) {
            File f = new File(a.absolutePath);
            if (!f.exists()) continue;
            try {
                uris.add(FileProvider.getUriForFile(activity, authority, f));
            } catch (IllegalArgumentException ignored) {
                // file outside any configured FileProvider path — skip
            }
        }

        // Find mail apps. ACTION_SENDTO + mailto: only resolves to email handlers
        // (Gmail, Outlook, ProtonMail, etc.) — never Messages, Drive, Telegram, etc.
        // We use this list to build per-package explicit ACTION_SEND intents so the
        // chooser never falls back to the generic share sheet.
        PackageManager pm = activity.getPackageManager();
        Intent mailtoProbe = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        List<ResolveInfo> mailApps = pm.queryIntentActivities(mailtoProbe, 0);

        if (mailApps.isEmpty()) {
            // No email app installed — fall back to plain share sheet so user
            // still has *some* way to dispatch the message (per spec).
            Intent fallback = buildSendIntent(to, cc, bcc, subject, body, isBodyHtml, uris, attachments);
            grantUris(activity, fallback, uris);
            activity.startActivity(Intent.createChooser(fallback, null));
            return;
        }

        List<Intent> perAppIntents = new ArrayList<>(mailApps.size());
        for (ResolveInfo ri : mailApps) {
            String mailPkg = ri.activityInfo.packageName;
            Intent i = buildSendIntent(to, cc, bcc, subject, body, isBodyHtml, uris, attachments);
            i.setPackage(mailPkg);
            // Pre-grant attachment URIs to every candidate, since the user may
            // pick any of them and grants done after startActivity arrive too late.
            for (Uri u : uris) {
                activity.grantUriPermission(mailPkg, u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            perAppIntents.add(i);
        }

        if (perAppIntents.size() == 1) {
            activity.startActivity(perAppIntents.get(0));
            return;
        }

        Intent primary = perAppIntents.remove(0);
        Intent chooser = Intent.createChooser(primary, null);
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                perAppIntents.toArray(new Intent[0]));
        activity.startActivity(chooser);
    }

    private static Intent buildSendIntent(String[] to, String[] cc, String[] bcc,
                                          String subject, String body, boolean isBodyHtml,
                                          ArrayList<Uri> uris, List<Attachment> attachments) {
        Intent intent;
        String mime = attachments.isEmpty() || attachments.get(0).type == null
                ? "message/rfc822" : attachments.get(0).type;

        if (uris.isEmpty()) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
        } else if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType(mime);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }

        if (to != null) intent.putExtra(Intent.EXTRA_EMAIL, to);
        if (cc != null) intent.putExtra(Intent.EXTRA_CC, cc);
        if (bcc != null) intent.putExtra(Intent.EXTRA_BCC, bcc);
        if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (body != null) {
            intent.putExtra(Intent.EXTRA_TEXT, isBodyHtml ? fromHtml(body) : body);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private static void grantUris(Activity activity, Intent intent, List<Uri> uris) {
        if (uris.isEmpty()) return;
        List<ResolveInfo> resolved = activity.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : resolved) {
            String targetPkg = ri.activityInfo.packageName;
            for (Uri u : uris) {
                activity.grantUriPermission(targetPkg, u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static CharSequence fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        }
        return Html.fromHtml(html);
    }

    // --- Lua table readers ---

    private static String readString(LuaState L, int tableIdx, String field) {
        L.getField(tableIdx, field);
        String v = L.isString(-1) ? L.toString(-1) : null;
        L.pop(1);
        return v;
    }

    private static boolean readBoolean(LuaState L, int tableIdx, String field) {
        L.getField(tableIdx, field);
        boolean v = L.isBoolean(-1) && L.toBoolean(-1);
        L.pop(1);
        return v;
    }

    private static String[] readStringOrArray(LuaState L, int tableIdx, String field) {
        L.getField(tableIdx, field);
        try {
            if (L.type(-1) == LuaType.STRING) {
                return new String[]{ L.toString(-1) };
            }
            if (L.type(-1) == LuaType.TABLE) {
                int n = L.length(-1);
                if (n == 0) return null;
                String[] out = new String[n];
                for (int i = 1; i <= n; i++) {
                    L.rawGet(-1, i);
                    out[i - 1] = L.toString(-1);
                    L.pop(1);
                }
                return out;
            }
            return null;
        } finally {
            L.pop(1);
        }
    }

    private static List<Attachment> readAttachments(LuaState L, int tableIdx, String field) {
        List<Attachment> out = new ArrayList<>();
        L.getField(tableIdx, field);
        try {
            if (L.type(-1) != LuaType.TABLE) return out;
            int n = L.length(-1);
            for (int i = 1; i <= n; i++) {
                L.rawGet(-1, i);
                if (L.type(-1) == LuaType.TABLE) {
                    L.getField(-1, "absolutePath");
                    String path = L.isString(-1) ? L.toString(-1) : null;
                    L.pop(1);
                    L.getField(-1, "type");
                    String type = L.isString(-1) ? L.toString(-1) : null;
                    L.pop(1);
                    if (path != null) out.add(new Attachment(path, type));
                }
                L.pop(1);
            }
            return out;
        } finally {
            L.pop(1);
        }
    }

    private static final class Attachment {
        final String absolutePath;
        final String type;
        Attachment(String absolutePath, String type) {
            this.absolutePath = absolutePath;
            this.type = type;
        }
    }
}
