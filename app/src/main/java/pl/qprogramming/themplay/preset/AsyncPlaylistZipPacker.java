package pl.qprogramming.themplay.preset;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.SneakyThrows;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.util.AsyncTaskExecutorService;

/**
 * Async Task to save all playlists into zip file
 */
public class AsyncPlaylistZipPacker extends AsyncTaskExecutorService<Playlist, Void, ExportResult> {
    private static final String TAG = AsyncPlaylistZipPacker.class.getSimpleName();
    private static final String BACKGROUND = "background.jpg";
    private final Uri uri;
    private final DocumentFile documentFile;


    public AsyncPlaylistZipPacker(Uri uri, StringBuilder logs, Context context) {
        super(logs, context);
        this.uri = uri;
        this.documentFile = DocumentFile.fromSingleUri(context, uri);
    }

    /**
     * Save each preset into zip file set by user
     */
    @Override
    @SneakyThrows
    protected ExportResult doInBackground(Playlist... entries) {
        boolean overallSuccess = true;
        try (val outputStream = context.getContentResolver().openOutputStream(uri); val zip = new ZipOutputStream(outputStream)) {
            val list = new StringBuilder();
            //create zip and list preset with it's songs
            for (Playlist playlist : entries) {
                list.append("\n-----------\n");
                list.append(playlist.getName());
                saveBackgroundToZip(zip, playlist);
                playlist.getSongs().forEach(song -> {
                    list.append("\n- ")
                            .append(song.getFilename())
                            .append(" (")
                            .append(song.getFilePath())
                            .append(")");
                    saveSongToFile(logs, zip, playlist, song);
                });
            }
            val bgEntry = new ZipEntry("preset_content.txt");
            zip.putNextEntry(bgEntry);
            zip.write(list.toString().getBytes());
            zip.closeEntry();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file ", e);
            logs.append("\nFailed to save file ");
            logs.append(e);
        }
        return new ExportResult(overallSuccess, logs.length() > 0 ? logs.toString() : null);
    }

    /**
     * Once save operation is done, check if there was something within logs
     */
    @Override
    @SneakyThrows
    protected void onPostExecute(ExportResult result) {
        super.onPostExecute(result);
        if (logs.length() > 0) {
//            File logFile = new File(Environment.getExternalStorageDirectory() + "/themplay_export_errors_" + (System.currentTimeMillis() / 1000) + ".txt");
//            try (val bw = new BufferedWriter(new FileWriter(logFile))) {
//                bw.write(logs.toString());
//            }
            //TODO this needs fix
//            val msg = MessageFormat.format(context.getString(R.string.presets_saved_errors), documentFile.getName(), logFile.getName());
//            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        } else {
            val msg = MessageFormat.format(context.getString(R.string.presets_saved), documentFile.getName());
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }

    }

    /**
     * If playlists has a background , save it to file
     */
    private void saveBackgroundToZip(ZipOutputStream zip, Playlist playlist) throws IOException {
        if (playlist.getBackgroundImage() != null) {
            val bgEntry = new ZipEntry(playlist.getName() + "/" + BACKGROUND);
            zip.putNextEntry(bgEntry);
            zip.write(Base64.decode(playlist.getBackgroundImage(), Base64.DEFAULT));
            zip.closeEntry();
        }
    }

    /**
     * Load file based on it's song uri and add it to zip file
     */
    private void saveSongToFile(StringBuilder logs, ZipOutputStream zip, Playlist playlist, Song song) {
        val entry = new ZipEntry(playlist.getName() + "/" + song.getFilename());
        val contentResolver = context.getContentResolver();
        try {
            zip.putNextEntry(entry);
            val songUri = Uri.parse(song.getFileUri());
            contentResolver.takePersistableUriPermission(songUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            val fd = contentResolver.openFileDescriptor(songUri, "r");
            try (val fis = new FileInputStream(fd.getFileDescriptor())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zip.write(buffer, 0, len);
                }
            }
            zip.closeEntry();
        } catch (IOException ex) {
            Log.e(TAG, "Error while trying to save file " + song.getFilename());
            Log.e(TAG, ex.toString());
            logs.append("\nFailed to save file ");
            logs.append(ex);
        }
    }
}
