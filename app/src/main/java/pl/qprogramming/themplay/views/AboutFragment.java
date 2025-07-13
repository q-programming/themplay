package pl.qprogramming.themplay.views;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.util.Utils.navigateToFragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import lombok.val;
import pl.qprogramming.themplay.BuildConfig;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.settings.Property;

/**
 * The AboutFragment class is responsible for displaying information about the application.
 * It includes details such as the app version, build time, and a help section loaded from
 * an HTML file. The fragment supports multiple languages for the help content and adjusts
 * its appearance based on the dark mode setting.
 *
 * <p>Key functionalities include:
 * <ul>
 *     <li>Displaying the application version and build timestamp.</li>
 *     <li>Loading and rendering localized help content in a WebView.</li>
 *     <li>Handling navigation back to the previous screen or a default playlist view.</li>
 *     <li>Adapting the text color of the help content based on the dark mode preference.</li>
 * </ul>
 * </p>
 */
public class AboutFragment extends Fragment {

    private final Locale[] SUPPORTED_LANGUAGES = new Locale[]{new Locale("en"), new Locale("pl")};

    public AboutFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.about, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        val sp = getDefaultSharedPreferences(view.getContext());
        val darkMode = sp.getBoolean(Property.DARK_MODE, false);
        super.onViewCreated(view, savedInstanceState);
        val textView = (TextView) view.findViewById(R.id.header_title);
        val locale = getResources().getConfiguration().getLocales().get(0);
        textView.setText(getString(R.string.about));
        view.findViewById(R.id.include)
                .setOnClickListener(clickedView -> {
                    FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
                    if (fragmentManager.getBackStackEntryCount() <= 1) {
                        navigateToFragment(fragmentManager, new PlaylistFragment(), "playlist_default");
                    } else {
                        fragmentManager.popBackStack();
                    }
                });
        val versionTxt = (TextView) requireView().findViewById(R.id.version);
        String versionSummary;
        String buildTimeStr = "";
        val version = BuildConfig.VERSION_NAME;
        long buildTimestamp = BuildConfig.BUILD_TIMESTAMP;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        buildTimeStr = sdf.format(new Date(buildTimestamp));
        if (buildTimeStr.isEmpty()) {
            versionSummary = version;
        } else {
            versionSummary = version + " \n(" + buildTimeStr + ")";
        }
        versionTxt.setText(String.format("v%s", versionSummary));
        val mWebView = (WebView) view.findViewById(R.id.help_content);
        mWebView.setBackgroundColor(Color.TRANSPARENT);
        val webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                val intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                view.getContext().startActivity(intent);
                return true;
            }

            public void onPageFinished(WebView view, String url) {
                if (darkMode) {
                    mWebView.loadUrl(
                            "javascript:document.body.style.setProperty(\"color\", \"#BCBCBC\");"
                    );
                } else {
                    mWebView.loadUrl(
                            "javascript:document.body.style.setProperty(\"color\", \"#515151\");"
                    );
                }
            }
        });
        mWebView.loadUrl(getLanguageTemplate(locale));
    }


    private String getLanguageTemplate(Locale locale) {
        val optionalLocale = Arrays.stream(SUPPORTED_LANGUAGES)
                .filter(loc -> loc.getLanguage().equals(locale.getLanguage()))
                .findFirst()
                .orElseGet(() -> SUPPORTED_LANGUAGES[0]);
        return MessageFormat.format("file:///android_asset/about_{0}.html", optionalLocale.getLanguage());
    }
}