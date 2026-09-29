package svenmeier.coxswain.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;

/**
 */
public class PermissionActivity extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback {

	static final String ACTION = "svenmeier.coxswain.util.permission.GRANTED";

	static final String PERMISSIONS = "permissions";

	static final String GRANTED = "granted";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		String[] permissions = getIntent().getStringArrayExtra(PERMISSIONS);

		ActivityCompat.requestPermissions(this, permissions, 1);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		boolean granted = true;
		for (int grantResult : grantResults) {
			granted &= (grantResult == PackageManager.PERMISSION_GRANTED);
		}

		Intent intent = new Intent();
		intent.setAction(ACTION);
		intent.setPackage(getPackageName());
		intent.putExtra(PERMISSIONS, permissions);
		intent.putExtra(GRANTED, granted);
		sendBroadcast(intent);

		finish();
	}

	/**
	 * The filter for permission results, must be registered before
	 * {@link #start(Context, String[])} so no result can be missed.
	 */
	public static IntentFilter filter() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION);
		return filter;
	}

	/**
	 * Start requesting the given permissions; any result is broadcast using {@link #ACTION}.
	 */
	public static void start(Context context, String[] permissions) {
		Intent intent = new Intent(context, PermissionActivity.class);

		// required for activity started from non-activity
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		intent.putExtra(PERMISSIONS, permissions);

		try {
			context.startActivity(intent);
		} catch (Exception backgroundStartDenied) {
			// Android 10+ forbids background activity starts: report as rejected
			Intent rejected = new Intent(ACTION);
			rejected.setPackage(context.getPackageName());
			rejected.putExtra(PERMISSIONS, permissions);
			rejected.putExtra(GRANTED, false);
			context.sendBroadcast(rejected);
		}
	}

	public static int receiverFlags() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return Context.RECEIVER_NOT_EXPORTED;
		}
		return 0;
	}
}
