package svenmeier.coxswain.io;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import svenmeier.coxswain.Coxswain;
import svenmeier.coxswain.Gym;
import svenmeier.coxswain.R;
import svenmeier.coxswain.gym.Program;
import svenmeier.coxswain.util.PermissionBlock;

/**
 */
public class ProgramExport extends Export<Program> {

	public static final String SUFFIX = ".coxswain";

	private final boolean share;

	private Handler handler = new Handler();

	private final Gym gym;

	public ProgramExport(Context context, boolean share) {
		super(context);

		this.share = share;

		this.handler = new Handler();

		this.gym = Gym.instance(context);
	}

	@Override
	public void start(Program program, boolean automatic) {
		new Writing(program);
	}

	private class Writing extends PermissionBlock implements Runnable {

		private final Program program;

		public Writing(Program program) {
			super(context);

			this.program = program;

			if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
				acquirePermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			} else {
				onPermissionsApproved();
			}
		}

		@Override
		protected void onRejected() {
			toast(context.getString(R.string.program_export_failed));
		}

		@Override
		protected void onPermissionsApproved() {
			new Thread(this).start();
		}

		@Override
		public void run() {
			toast(context.getString(R.string.program_export_starting));
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			File file;
			try {
				file = write();
			} catch (IOException e) {
				Log.e(Coxswain.TAG, "export failed", e);
				toast(context.getString(R.string.program_export_failed));
				return;
			}

			// make file visible over USB / file browsers
			android.media.MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);

			if (share) {
				share(file);
			} else {
				toast(String.format(context.getString(R.string.program_export_finished), file.getAbsolutePath()));
			}
		}

		private void share(File file) {
			Intent shareIntent = new Intent(Intent.ACTION_SEND);

			shareIntent.setType("text/xml");
			setFile(context, file, shareIntent);
			shareIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName());

			context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.program_export)));
		}

		public String getFileName() {
			StringBuilder name = new StringBuilder();

			name.append(program.name.get().replaceAll("[\\/]", " "));
			name.append(SUFFIX);

			return name.toString();
		}

		private File write() throws IOException {
			File dir = Coxswain.getExternalFilesDir(context);
			dir.mkdirs();
			dir.setReadable(true, false);

			File file = new File(dir, getFileName());

			Writer writer = new BufferedWriter(new FileWriter(file));
			try {
				new Program2Json(writer).document(program);
			} finally {
				writer.close();
			}

			return file;
		}
	}

	private void toast(final String text) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(context, text, Toast.LENGTH_LONG).show();
			}
		});
	}
}
