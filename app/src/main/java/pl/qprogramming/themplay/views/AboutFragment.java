package pl.qprogramming.themplay.views;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import lombok.val;
import pl.qprogramming.themplay.BuildConfig;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * About fragment
 */
public class AboutFragment extends Fragment {

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
        textView.setText(getString(R.string.about));
        view
                .findViewById(R.id.include)
                .setOnClickListener(clicked -> requireActivity()
                        .getSupportFragmentManager()
                        .popBackStack());
        val versionTxt = (TextView) requireView().findViewById(R.id.version);
        versionTxt.setText(String.format("v%s", BuildConfig.VERSION_NAME));
        val mWebView = (WebView) view.findViewById(R.id.help_content);
        mWebView.setBackgroundColor(Color.TRANSPARENT);
        val webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
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
        mWebView.loadUrl("file:///android_asset/about.html");
    }
}