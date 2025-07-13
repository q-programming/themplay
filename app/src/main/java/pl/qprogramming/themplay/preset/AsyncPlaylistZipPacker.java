package pl.qprogramming.themplay.preset;

import static pl.qprogramming.themplay.settings.Property.LOGS_DIRECTORY_NAME;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.SneakyThrows;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;
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
            Logger.e(TAG, "Failed to write to file ", e);
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
            File externalFilesDir = new File(context.getExternalFilesDir(null), LOGS_DIRECTORY_NAME);
            File logFile = new File(externalFilesDir + "/themplay_export_errors_" + (System.currentTimeMillis() / 1000) + ".txt");
            try (val bw = new BufferedWriter(new FileWriter(logFile))) {
                bw.write(logs.toString());
            }
            Logger.e(TAG, "Logs saved to " + logFile.getAbsolutePath());
            val msg = MessageFormat.format(context.getString(R.string.presets_saved_errors), documentFile.getName(), logFile.getName());
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        } else {
            val msg = MessageFormat.format(context.getString(R.string.presets_saved), documentFile.getName());
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }

    }

    /**
     * Saves the background image of a playlist to the ZIP archive.
     * <p>
     * The method first checks if the playlist has background image data. If not, it logs a message and returns.
     * Otherwise, it creates a ZIP entry for the background image, named "playlistName/background.jpg".
     * <p>
     * It then attempts to read the image data from a file path. If the {@code backgroundImageData}
     * string points to an existing file, the file's content is read and written to the ZIP stream.
     * <p>
     * If reading from the file path fails or is not applicable (e.g., the path is invalid or the data
     * is not a file path), the method attempts to decode the {@code backgroundImageData} as a Base64
     * encoded string. If successful, the decoded bytes are written to the ZIP stream.
     * <p>
     * If both attempts (file path and Base64 decoding) fail, or if the data is not valid for either,
     * appropriate log messages are generated.
     * <p>
     * Finally, the current ZIP entry is closed.
     *
     * @param zip      The ZipOutputStream to which the background image will be written.
     * @param playlist The Playlist object containing the background image data.
     * @throws IOException If an I/O error occurs while writing to the ZIP stream.
     */
    private void saveBackgroundToZip(ZipOutputStream zip, Playlist playlist) throws IOException {
        String backgroundImageData = playlist.getBackgroundImage();
        if (backgroundImageData == null || backgroundImageData.isEmpty()) {
            Logger.d(TAG, "No background image data for playlist: " + playlist.getName());
            return;
        }
        String entryName = playlist.getName() + "/" + BACKGROUND;
        ZipEntry bgEntry = new ZipEntry(entryName);
        zip.putNextEntry(bgEntry);
        boolean success = false;
        File imageFile = new File(backgroundImageData);
        if (imageFile.exists() && imageFile.isFile()) {
            Logger.d(TAG, "Attempting to save background from file path: " + backgroundImageData);
            try (FileInputStream fis = new FileInputStream(imageFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zip.write(buffer, 0, length);
                }
                success = true;
                Logger.d(TAG, "Successfully wrote background from file path to zip for: " + playlist.getName());
            } catch (IOException | SecurityException e) {
                Logger.e(TAG, "Error while reading image file for zipping: " + backgroundImageData, e);
            }
        }
        if (!success) {
            Logger.d(TAG, "File path attempt failed or skipped, attempting to decode as Base64 for: " + playlist.getName());
            try {
                byte[] imageBytes = Base64.decode(backgroundImageData, Base64.DEFAULT);
                zip.write(imageBytes);
                Logger.d(TAG, "Successfully wrote background from Base64 to zip for: " + playlist.getName());
            } catch (IllegalArgumentException e) {
                Logger.w(TAG, "Data for " + playlist.getName() + " was not a valid file path and not valid Base64: " + e.getMessage());
            }
        }
        zip.closeEntry();
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
            Logger.e(TAG, "Error while trying to save file " + song.getFilename());
            Logger.e(TAG, ex.toString());
            logs.append("\nFailed to save file ");
            logs.append(ex);
        }
    }
}
