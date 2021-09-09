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
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        val textView = (TextView) getView().findViewById(R.id.header_title);
        textView.setText(getString(R.string.about));
        getView()
                .findViewById(R.id.header_title)
                .setOnClickListener(clicked -> getActivity()
                        .getSupportFragmentManager()
                        .popBackStack());
    }
}