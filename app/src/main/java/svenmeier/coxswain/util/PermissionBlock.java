package svenmeier.coxswain.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PermissionBlock {

	private final Context context;

	private String[] permissions;

	private BroadcastReceiverImpl receiver;

	public PermissionBlock(Context context) {
		this.context = context;
	}

	public void acquirePermissions(String... permissions) {
		unregister();

		this.permissions = filterUnavailable(permissions);

		if (this.permissions.length == 0) {
			onPermissionsApproved();
			return;
		}

		for (String permission : this.permissions) {
			if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions();
				return;
			}
		}

		onPermissionsApproved();
	}

	protected final void abortPermissions() {
		unregister();
	}

	protected void onPermissionsApproved() {
	}

	protected void onRejected() {
	}

	private void requestPermissions() {

		IntentFilter filter = PermissionActivity.filter();

		receiver = new BroadcastReceiverImpl();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			context.registerReceiver(receiver, filter);
		}

		// start after registration, otherwise a rejected result could be missed
		PermissionActivity.start(context, permissions);
	}

	/**
	 * Drop permissions which do not exist on this API level or are not
	 * granted to this app anymore (e.g. WRITE_EXTERNAL_STORAGE on API 33+).
	 */
	private String[] filterUnavailable(String... permissions) {
		List<String> available = new ArrayList<>();
		for (String permission : permissions) {
			try {
				context.getPackageManager().getPermissionInfo(permission, 0);
			} catch (Exception unknown) {
				continue;
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
					&& android.Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
				// scoped storage: not needed for app-specific directories
				continue;
			}
			available.add(permission);
		}
		return available.toArray(new String[0]);
	}

	private void unregister() {
		if (receiver != null) {
			context.unregisterReceiver(receiver);
			receiver = null;
		}
	}

	private class BroadcastReceiverImpl extends BroadcastReceiver {
		@Override
		public final void onReceive(Context context, Intent intent) {

			String[] permissions = intent.getStringArrayExtra(PermissionActivity.PERMISSIONS);
			if (Arrays.equals(PermissionBlock.this.permissions, permissions) == false) {
				return;
			}

			unregister();

			boolean granted = intent.getBooleanExtra(PermissionActivity.GRANTED, false);
			if (granted) {
				onPermissionsApproved();
			} else {
				onRejected();
			}
		}
	}
}