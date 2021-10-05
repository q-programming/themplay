package pl.qprogramming.themplay.views;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import lombok.val;
import pl.qprogramming.themplay.BuildConfig;
import pl.qprogramming.themplay.R;

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        val textView = (TextView) getView().findViewById(R.id.header_title);
        textView.setText(getString(R.string.about));
        getView()
                .findViewById(R.id.Header)
                .setOnClickListener(clicked -> getActivity()
                        .getSupportFragmentManager()
                        .popBackStack());
        val versionTxt = (TextView) getView().findViewById(R.id.version);
        versionTxt.setText(String.format("v%s", BuildConfig.VERSION_NAME));
    }
}