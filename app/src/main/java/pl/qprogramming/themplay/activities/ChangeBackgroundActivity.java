package pl.qprogramming.themplay.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.theartofdev.edmodo.cropper.CropImage;

import java.io.ByteArrayOutputStream;

import androidx.annotation.Nullable;
import io.reactivex.schedulers.Schedulers;
import lombok.SneakyThrows;
import lombok.val;
import pl.qprogramming.themplay.playlist.Playlist;
import pl.qprogramming.themplay.util.Utils;

import static pl.qprogramming.themplay.playlist.EventType.PLAYLIST_CHANGE_BACKGROUND;
import static pl.qprogramming.themplay.util.Utils.ARGS;
import static pl.qprogramming.themplay.util.Utils.PLAYLIST;
import static pl.qprogramming.themplay.util.Utils.POSITION;

/**
 * Activity to load image from galery , crop it and save it as base64 string into playlist
 */
public class ChangeBackgroundActivity extends Activity {
    private static final String TAG = ChangeBackgroundActivity.class.getSimpleName();
    private final int GALLERY_ACTIVITY_CODE = 200;
    private Playlist playlist;
    private int position;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        val launchingIntent = getIntent();
        Bundle args = launchingIntent.getBundleExtra(ARGS);
        playlist = (Playlist) args.getSerializable(PLAYLIST);
        position = (int) args.getSerializable(POSITION);
        if (playlist != null) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, GALLERY_ACTIVITY_CODE);
        } else {
            Log.d(TAG, "No playlist was passed into activity, finishing");
            finish();
        }
    }

    @SuppressLint("CheckResult")
    @SneakyThrows
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GALLERY_ACTIVITY_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val uri = data.getData();
                CropImage.activity(uri)
//                        .setAspectRatio(25, 5)
                        .start(this);
            } else {
                finish();
            }
        }
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri resultUri = result.getUri();
                Bitmap bitmap;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getApplicationContext().getContentResolver(), resultUri));
                } else {
                    bitmap = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), resultUri);
                }
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    byte[] imageBytes = baos.toByteArray();
                    String imageString = Base64.encodeToString(imageBytes, Base64.DEFAULT);
                    playlist.setBackgroundImage(imageString);
                    playlist.saveAsync().subscribeOn(Schedulers.io()).subscribe(updated -> {
                        Intent intent = new Intent(PLAYLIST_CHANGE_BACKGROUND.getCode());
                        val args = new Bundle();
                        args.putSerializable(Utils.POSITION, position);
                        intent.putExtra(ARGS, args);
                        sendBroadcast(intent);
                        finish();
                    });
                }
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
                Log.e(TAG, error.getMessage());
            }
        }

    }
}
