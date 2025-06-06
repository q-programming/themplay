package pl.qprogramming.themplay.activities;

import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_CHANGE_BACKGROUND;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.HEIGHT;
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
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

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
import java.io.IOException;

import lombok.val;
import pl.qprogramming.themplay.domain.Playlist;
import pl.qprogramming.themplay.playlist.PlaylistService;

/**
 * Activity to load image from gallery, crop it and save it as base64 string into playlist
 */
public class ChangeBackgroundActivity extends AppCompatActivity {
    private static final String TAG = ChangeBackgroundActivity.class.getSimpleName();
    private Playlist playlist;
    private int itemPosition; // Renamed from 'position'
    private int targetWidth;
    private int targetHeight;
    private int aspectRatioX = 1; // Default
    private int aspectRatioY = 1; // Default

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
                Log.d(TAG, "No image selected from gallery.");
                finish();
            }
        });
        cropImageLauncher = registerForActivityResult(new CropImageContract(), result -> {
            if (result.isSuccessful()) {
                Uri croppedUri = result.getUriContent();
                if (croppedUri != null) {
                    handleCroppedImage(croppedUri);
                } else {
                    Log.e(TAG, "Cropped URI is null.");
                    finish();
                }
            } else {
                Exception exception = result.getError();
                Log.e(TAG, "Image cropping failed: " + (exception != null ? exception.getMessage() : "Unknown error"), exception);
                finish();
            }
        });
        Intent launchingIntent = getIntent();
        Bundle args = launchingIntent.getBundleExtra(ARGS);
        if (args == null) {
            Log.d(TAG, "No arguments passed into activity, finishing");
            finish();
            return;
        }
        playlist = (Playlist) args.getSerializable(PLAYLIST);
        if (playlist == null) {
            Log.d(TAG, "No playlist was passed into activity or type mismatch, finishing");
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
            Log.e(TAG, "Invalid width or height passed. width: " + targetWidth + ", height: " + targetHeight + ". Finishing.");
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

    @SuppressLint("CheckResult")
    private void handleCroppedImage(Uri croppedUri) {
        Bitmap bitmap = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getApplicationContext().getContentResolver(), croppedUri));
            } else {
                //noinspection deprecation
                bitmap = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), croppedUri);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error decoding bitmap from URI", e);
            finish();
            return;
        }

        if (bitmap == null) {
            Log.e(TAG, "Bitmap could not be decoded.");
            finish();
            return;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            byte[] imageBytes = baos.toByteArray();
            String imageString = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            if (playlist != null) {
                playlist.setBackgroundImage(imageString);
                playlistService.save(playlist,
                        updated -> {
                            Log.d(TAG, "Playlist background updated successfully for  " + updated.getName());
                            sendUpdateBroadcast();
                            finish();
                        }, throwable -> {
                            Log.e(TAG, "Error updating playlist background for " + playlist.getName(), throwable);
                            finish();
                        });
            } else {
                Log.e(TAG, "Playlist became null before saving.");
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing cropped image for Base64 conversion", e);
            finish();
        }
    }

    private void sendUpdateBroadcast() {
        Intent intent = new Intent(PLAYLIST_CHANGE_BACKGROUND.getCode()); // Use defined action string
        Bundle argsBundle = new Bundle();
        argsBundle.putInt(POSITION, itemPosition);
        intent.putExtra(ARGS, argsBundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Playlist change background broadcast sent for position: " + itemPosition);
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