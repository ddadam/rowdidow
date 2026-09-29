package svenmeier.coxswain.util;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Helpers for API level differences (targetSdk 35).
 */
public final class Compat {

	private Compat() {
	}

	/**
	 * Flags for {@link PendingIntent}s which are never modified by others.
	 * <p>
	 * Immutability is mandatory since API 31, our minimum SDK is higher.
	 */
	public static int pendingIntentFlags(int base) {
		return base | PendingIntent.FLAG_IMMUTABLE;
	}

	/**
	 * Flags for {@link PendingIntent}s which must be mutable, because the system or the
	 * receiving app fills in extras:
	 * <ul>
	 *     <li>the callback of {@link UsbManager#requestPermission(UsbDevice, PendingIntent)}
	 *     which delivers {@link UsbManager#EXTRA_DEVICE} and
	 *     {@link UsbManager#EXTRA_PERMISSION_GRANTED}</li>
	 *     <li>the chooser result of {@link Intent#createChooser(Intent, CharSequence,
	 *     android.content.IntentSender)} which delivers the selected component</li>
	 * </ul>
	 * Mutability is only known since API 31, below that any {@link PendingIntent} is mutable.
	 */
	public static int mutablePendingIntentFlags(int base) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return base | PendingIntent.FLAG_MUTABLE;
		}
		return base;
	}

	/**
	 * Register a receiver of our own broadcasts: exported receivers are not allowed since
	 * API 33 without an explicit flag.
	 */
	public static void registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			context.registerReceiver(receiver, filter);
		}
	}

	/**
	 * The {@link UsbManager#EXTRA_DEVICE} extra.
	 */
	public static UsbDevice usbDeviceExtra(Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
		} else {
			return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		}
	}

	/**
	 * The runtime permissions needed to talk to a Bluetooth device, empty if Bluetooth
	 * permissions are granted at install time (API 30 and below).
	 */
	public static String[] bluetoothPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
		}
		return new String[0];
	}

	/**
	 * Whether we are allowed to connect a Bluetooth device.
	 */
	public static boolean hasBluetoothPermission(Context context) {
		return hasPermissions(context, bluetoothPermissions());
	}

	public static boolean hasPermissions(Context context, String... permissions) {
		for (String permission : permissions) {
			if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether Bluetooth is switched on - false if we are not even allowed to know.
	 */
	public static boolean isBluetoothEnabled(BluetoothAdapter adapter) {
		if (adapter == null) {
			return false;
		}
		try {
			return adapter.isEnabled();
		} catch (SecurityException notPermitted) {
			// API 31+ without BLUETOOTH_CONNECT
			return false;
		}
	}

	/**
	 * Whether the prerequisites of the {@code connectedDevice} foreground service type are met:
	 * since API 34 starting such a service throws a {@link SecurityException} unless we are
	 * allowed to use the Bluetooth or USB device it is connected to.
	 */
	public static boolean hasConnectedDevicePrerequisite(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			return true;
		}

		if (hasBluetoothPermission(context)) {
			return true;
		}

		UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		if (manager != null) {
			for (UsbDevice device : manager.getDeviceList().values()) {
				if (manager.hasPermission(device)) {
					return true;
				}
			}
		}

		return false;
	}
}
