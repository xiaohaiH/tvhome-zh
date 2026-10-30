package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The API 21 TV Input Framework boundary. Only plain row data crosses back to MainActivity. */
final class TvInputCatalog {
    // These compile-time integers are inlined; using row status never loads a TV framework class.
    @SuppressLint("InlinedApi")
    static final int CONNECTED = TvInputManager.INPUT_STATE_CONNECTED;
    @SuppressLint("InlinedApi")
    static final int DISCONNECTED = TvInputManager.INPUT_STATE_DISCONNECTED;

    static final class Item {
        final String id, label;
        final int icon, state;
        final boolean separator;

        Item(String id, String label, int icon, int state, boolean separator) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.state = state;
            this.separator = separator;
        }
    }

    final List<Item> items = new ArrayList<>();
    Map<String, Integer> vendorNumbers = Collections.emptyMap();
    String tunerId;

    private TvInputCatalog() {
    }

    @RequiresApi(21)
    static TvInputCatalog load(Context context) {
        TvInputCatalog out = new TvInputCatalog();
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
        out.vendorNumbers = VendorInputs.isChinaFirmware(context)
                ? VendorInputs.sourceNumbers(inputs) : Collections.emptyMap();
        Collections.sort(inputs, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(context)).compareToIgnoreCase(String.valueOf(b.loadLabel(context)));
        });

        // Some sets report every HDMI socket as connected. Hide misleading status on those sets.
        int hdmiPorts = 0, hdmiConnected = 0;
        Set<String> ids = new HashSet<>();
        for (TvInputInfo info : inputs) {
            ids.add(info.getId());
            if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
            hdmiPorts++;
            if (tim.getInputState(info.getId()) == CONNECTED) hdmiConnected++;
        }
        boolean bogusState = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
        Map<String, List<TvInputInfo>> cec = new HashMap<>();
        for (TvInputInfo info : inputs) {
            String parent = info.getParentId();
            if (parent == null || !ids.contains(parent) || !info.isPassthroughInput()) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(context)) continue;
            List<TvInputInfo> devices = cec.get(parent);
            if (devices == null) cec.put(parent, devices = new ArrayList<>());
            devices.add(info);
        }

        boolean sawNonHdmi = false;
        for (TvInputInfo info : inputs) {
            if (info.getType() == TvInputInfo.TYPE_TUNER
                    && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (out.tunerId == null || info.getId().compareTo(out.tunerId) < 0)) {
                out.tunerId = info.getId();
            }
            if (!info.isPassthroughInput()) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(context)) continue;
            if (info.getParentId() != null && ids.contains(info.getParentId())) continue;
            CharSequence name = info.loadLabel(context);
            String label = name == null ? info.getId() : name.toString();
            boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            CharSequence custom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? info.loadCustomLabel(context) : null;
            if (custom != null && custom.length() > 0 && !custom.toString().equals(label)) {
                label = custom + " \u00B7 " + label;
            }
            boolean separator = !hdmi && !out.items.isEmpty() && !sawNonHdmi;
            if (!hdmi) sawNonHdmi = true;
            List<TvInputInfo> devices = cec.get(info.getId());
            if (devices != null) {
                // A CEC device replaces its parent port's row rather than duplicating that port.
                for (TvInputInfo device : devices) {
                    CharSequence deviceName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                            ? device.loadCustomLabel(context) : null;
                    String deviceLabel = deviceName == null || deviceName.length() == 0
                            ? label : deviceName + " \u00B7 " + label;
                    int state = !hdmi || bogusState ? -1 : tim.getInputState(device.getId());
                    out.items.add(new Item(device.getId(), deviceLabel, R.drawable.ic_input, state, separator));
                    separator = false;
                }
            } else {
                // Analog AV has no meaningful hotplug state.
                int state = !hdmi || bogusState ? -1 : tim.getInputState(info.getId());
                out.items.add(new Item(info.getId(), label, hdmi ? R.drawable.ic_input : R.drawable.ic_av,
                        state, separator));
            }
        }
        return out;
    }

    @RequiresApi(21)
    static String parentId(Context context, String inputId) {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            try {
                TvInputInfo info = tim.getTvInputInfo(inputId);
                if (info != null && info.getParentId() != null) return info.getParentId();
            } catch (RuntimeException ignored) {
                // An input may disappear while its menu is open; use the original id.
            }
        }
        return inputId;
    }

    @RequiresApi(21)
    static Uri channelUri(String inputId) {
        return TvContract.buildChannelUriForPassthroughInput(inputId);
    }
}
