package pl.qprogramming.themplay.activities;

import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_CHANGE_BACKGROUND;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.HEIGHT;
import static pl.qprogramming.themplay.util.Utils.IMAGES_DIR_NAME;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.POSITION;
import static pl.qprogramming.themplay.util.Utils.WIDTH;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;

import lombok.val;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.logger.Logger;
import pl.qprogramming.themplay.playlist.PlaylistService;

/**
 * Activity to load image from gallery, crop it and save it as base64 string into playlist
 */
public class ChangeBackgroundActivity extends AppCompatActivity {
    private static final String TAG = ChangeBackgroundActivity.class.getSimpleName();

    private Playlist playlist;
    private int itemPosition;
    private int targetWidth;
    private int targetHeight;
    private int aspectRatioX = 1;
    private int aspectRatioY = 1;
    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<CropImageContractOptions> cropImageLauncher;
    private PlaylistService playlistService;
    private boolean serviceIsBound;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        val playlistServiceIntent = new Intent(this, PlaylistService.class);
        bindService(playlistServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                launchCropImage(uri);
            } else {
                Logger.d(TAG, "No image selected from gallery.");
                finish();
            }
        });
        cropImageLauncher = registerForActivityResult(new CropImageContract(), result -> {
            if (result.isSuccessful()) {
                Uri croppedUri = result.getUriContent();
                if (croppedUri != null) {
                    Logger.d(TAG, "Image cropping successful for playlist" + playlist.getName() + " got URI: " + croppedUri);
                    handleCroppedImage(croppedUri);
                } else {
                    Logger.e(TAG, "Cropped URI is null.");
                    finish();
                }
            } else {
                Exception exception = result.getError();
                Logger.e(TAG, "Image cropping failed: " + (exception != null ? exception.getMessage() : "Unknown error"), exception);
                finish();
            }
        });
        Intent launchingIntent = getIntent();
        Bundle args = launchingIntent.getBundleExtra(ARGS);
        if (args == null) {
            Logger.d(TAG, "No arguments passed into activity, finishing");
            finish();
            return;
        }
        playlist = (Playlist) args.getSerializable(PLAYLIST);
        if (playlist == null) {
            Logger.d(TAG, "No playlist was passed into activity or type mismatch, finishing");
            finish();
            return;
        }
        val posObj = args.getSerializable(POSITION);
        itemPosition = (posObj != null) ? (int) posObj : 0;
        val widthObj = args.getSerializable(WIDTH);
        targetWidth = (widthObj != null) ? (int) widthObj : 0;
        val heightObj = args.getSerializable(HEIGHT);
        targetHeight = (heightObj != null) ? (int) heightObj : 0;
        if (targetWidth <= 0 || targetHeight <= 0) {
            Logger.e(TAG, "Invalid width or height passed. width: " + targetWidth + ", height: " + targetHeight + ". Finishing.");
            finish();
            return;
        }
        aspectRatioX = targetWidth;
        aspectRatioY = targetHeight;
        pickImageLauncher.launch("image/*");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceIsBound) {
            unbindService(mConnection);
            serviceIsBound = false;
        }
    }

    private void launchCropImage(Uri sourceUri) {
        CropImageOptions cropOptions = new CropImageOptions();
        cropOptions.guidelines = CropImageView.Guidelines.ON;
        cropOptions.fixAspectRatio = true;
        cropOptions.aspectRatioX = aspectRatioX;
        cropOptions.aspectRatioY = aspectRatioY;
        cropOptions.outputCompressFormat = Bitmap.CompressFormat.JPEG;
        cropOptions.outputCompressQuality = 75;
        val contractOptions = new CropImageContractOptions(sourceUri, cropOptions);
        cropImageLauncher.launch(contractOptions);
    }

    /**
     * Handles the cropped image URI.
     * <p>
     * This method performs the following steps:
     * 1. Validates that the playlist, its preset, and its name are not null.
     * 2. Creates a directory for preset images if it doesn't already exist. The directory path is determined by
     *    the external files directory, a constant {@code IMAGES_DIR_NAME}, and the playlist's preset name.
     * 3. Sanitizes the playlist name to be used as a filename by replacing characters that are not
     *    alphanumeric, period, or hyphen with an underscore.
     * 4. Creates a target {@link File} object for the scaled image, named after the sanitized playlist name with a ".jpg" extension,
     *    within the preset images directory.
     * 5. Decodes the image from the provided {@code croppedUri} into a {@link Bitmap}.
     * 6. Scales the decoded bitmap to the {@code targetWidth} and {@code targetHeight} specified for the activity.
     * 7. Compresses the scaled bitmap into JPEG format with 85% quality and writes it to the {@code targetFile}.
     * 8. Logs the success of saving the scaled image, including its path and size.
     * 9. If the {@code playlist} is still valid, updates its background image path to the absolute path of the saved file.
     * 10. Saves the updated playlist using the {@code playlistService}.
     * 11. Upon successful saving of the playlist:
     *     - Logs the successful update.
     *     - Sends a local broadcast to notify other components of the background change.
     *     - Finishes the activity.
     * 12. If saving the playlist fails, logs the error and finishes the activity.
     * 13. Handles potential {@link IOException} during image decoding or file writing, and {@link OutOfMemoryError}
     *     during image processing, by logging the error, showing a toast message, and finishing the activity.
     * 14. In a {@code finally} block, ensures that both the original and scaled bitmaps are recycled if they were created
     *     and are not already recycled, to free up memory.
     *
     */
    private void handleCroppedImage(Uri croppedUri) {
        if (playlist == null || playlist.getPreset() == null || playlist.getName() == null) {
            Logger.e(TAG, "Playlist, preset name, or playlist name is null. Cannot save image.");
            Toast.makeText(this, "Error: Playlist data missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        File presetImagesDir = new File(getExternalFilesDir(null), IMAGES_DIR_NAME + File.separator + playlist.getPreset());
        if (!presetImagesDir.exists()) {
            if (!presetImagesDir.mkdirs()) {
                Logger.e(TAG, "Failed to create directory: " + presetImagesDir.getAbsolutePath());
                Toast.makeText(this, "Error creating image directory.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        String sanitizedPlaylistName = playlist.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
        File targetFile = new File(presetImagesDir, sanitizedPlaylistName + ".jpg");
        Bitmap originalBitmap = null;
        Bitmap scaledBitmap = null;
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), croppedUri);
            originalBitmap = ImageDecoder.decodeBitmap(source);
            if (originalBitmap == null) {
                Logger.e(TAG, "Failed to decode bitmap from URI: " + croppedUri);
                Toast.makeText(this, "Error processing image.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true);
            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
                long finalCompressedFileSizeBytes = targetFile.length();
                Logger.d(TAG, "Scaled image saved successfully to: " + targetFile.getAbsolutePath() + " with size " + formatFileSize(finalCompressedFileSizeBytes));
                if (playlist != null) {
                    playlist.setBackgroundImage(targetFile.getAbsolutePath());
                    playlistService.save(playlist,
                            updated -> {
                                Logger.d(TAG, "Playlist background updated successfully for  " + updated.getName());
                                sendUpdateBroadcast();
                                finish();
                            }, throwable -> {
                                Logger.e(TAG, "Error updating playlist background for " + playlist.getName(), throwable);
                                finish();
                            });
                } else {
                    Logger.e(TAG, "Playlist became null before saving scaled image.");
                    finish();
                }
            } catch (IOException e) {
                Logger.e(TAG, "Error writing scaled image to file: " + e.getMessage(), e);
                Toast.makeText(this, "Error saving image.", Toast.LENGTH_LONG).show();
                finish();
            }

        } catch (IOException e) {
            Logger.e(TAG, "Error decoding original bitmap from URI: " + croppedUri, e);
            Toast.makeText(this, "Error loading image.", Toast.LENGTH_LONG).show();
            finish();
        } catch (OutOfMemoryError oom) {
            Logger.e(TAG, "OutOfMemoryError during image processing: " + oom.getMessage(), oom);
            Toast.makeText(this, "Image is too large, ran out of memory.", Toast.LENGTH_LONG).show();
            finish();
        } finally {
            if (originalBitmap != null && !originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            if (scaledBitmap != null && !scaledBitmap.isRecycled()) {
                if (scaledBitmap != originalBitmap) {
                    scaledBitmap.recycle();
                }
            }
        }
    }

    private String formatFileSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(sizeBytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @SuppressLint("CheckResult")
    @Deprecated(forRemoval = true, since = "build version 14")
    private void handleCroppedImageIntoBase64(Uri croppedUri) {
        Bitmap bitmap;
        try {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getApplicationContext().getContentResolver(), croppedUri));
        } catch (IOException e) {
            Logger.e(TAG, "Error decoding bitmap from URI", e);
            finish();
            return;
        }

        if (bitmap == null) {
            Logger.e(TAG, "Bitmap could not be decoded.");
            finish();
            return;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos); //no compress ?
            byte[] imageBytes = baos.toByteArray();
            String imageString = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            if (playlist != null) {
                playlist.setBackgroundImage(imageString);
                playlistService.save(playlist,
                        updated -> {
                            Logger.d(TAG, "Playlist background updated successfully for  " + updated.getName());
                            sendUpdateBroadcast();
                            finish();
                        }, throwable -> {
                            Logger.e(TAG, "Error updating playlist background for " + playlist.getName(), throwable);
                            finish();
                        });
            } else {
                Logger.e(TAG, "Playlist became null before saving.");
                finish();
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error processing cropped image for Base64 conversion", e);
            finish();
        }
    }

    private void sendUpdateBroadcast() {
        Intent intent = new Intent(PLAYLIST_CHANGE_BACKGROUND.getCode());
        Bundle argsBundle = new Bundle();
        argsBundle.putInt(POSITION, itemPosition);
        intent.putExtra(ARGS, argsBundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Logger.d(TAG, "Playlist change background broadcast sent for position: " + itemPosition);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            val binder = (PlaylistService.LocalBinder) service;
            playlistService = binder.getService();
            serviceIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            playlistService = null;
        }
    };
}