package org.firewall.protectify.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.firewall.protectify.Daedalus;
import org.firewall.protectify.util.Logger;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://firewall.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Daedalus.getPrefs().getBoolean("settings_boot", false)) {
            Daedalus.activateService(context, true);
            Logger.info("Triggered boot receiver");
        }
    }
}
