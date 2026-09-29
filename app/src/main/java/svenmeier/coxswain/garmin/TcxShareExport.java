package svenmeier.coxswain.garmin;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;

import propoid.db.Match;
import propoid.util.content.Preference;
import svenmeier.coxswain.R;
import svenmeier.coxswain.gym.Snapshot;
import svenmeier.coxswain.gym.Workout;
import svenmeier.coxswain.io.Export;
import svenmeier.coxswain.util.Compat;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 */
public class TcxShareExport extends TcxExport {

	public TcxShareExport(Context context) {
		super(context);
	}

	@Override
	protected void onWritten(File file) {
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("text/xml");
		shareIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
		setFile(context, file, shareIntent);

		if (automatic) {
			String sharePackage = Preference.getString(context, R.string.preference_export_tcx_share_package).get();
			if (sharePackage != null) {
				shareIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
				shareIntent.setPackage(sharePackage);

				context.startActivity(shareIntent);
				return;
			}
		}

		// the chooser remembers the selected app and reports back through this callback
		IntentSender sender = PendingIntent.getBroadcast(context, 0, ShareReceiver.newIntent(context),
				Compat.mutablePendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)).getIntentSender();

		Intent chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.garmin_export), sender);
		chooserIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(chooserIntent);
	}
}
