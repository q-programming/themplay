package pl.qprogramming.themplay.views;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.MessageFormat;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import pl.qprogramming.themplay.R;
import pl.qprogramming.themplay.preset.Preset;
import pl.qprogramming.themplay.settings.Property;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static pl.qprogramming.themplay.playlist.EventType.PRESET_ACTIVATED;
import static pl.qprogramming.themplay.util.Utils.getThemeColor;

public class PresetViewAdapter extends RecyclerView.Adapter<PresetViewAdapter.ViewHolder> {
    @Setter
    private List<Preset> presets;
    @Getter
    @Setter
    private boolean multiple;

    public PresetViewAdapter(List<Preset> items) {
        presets = items;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.preset_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        val sp = getDefaultSharedPreferences(holder.mView.getContext());
        val context = holder.mView.getContext();
        val preset = presets.get(position);
        holder.preset = preset;
        holder.presetName.setText(preset.getName());
        holder.activate.setOnClickListener(clicked -> {
            val spEdit = getDefaultSharedPreferences(context).edit();
            spEdit.putString(Property.CURRENT_PRESET, preset.getName());
            spEdit.apply();
            val msg = MessageFormat.format(context.getString(R.string.presets_activated), preset.getName());
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(PRESET_ACTIVATED.getCode());
            holder.mView.getContext().sendBroadcast(intent);
            //TODO emit event to :
            // stop playback
            // reload all playlists for that preset
            // make one active
        });
        //TODO add click listeners

        val currentPreset = sp.getString(Property.CURRENT_PRESET, null);
        if (preset.getName().equals(currentPreset)) {
            holder.mView.setBackgroundColor(getThemeColor(holder.mView, R.attr.colorSecondary));
            holder.activate.setVisibility(View.INVISIBLE);
        } else {
            holder.mView.setBackgroundColor(getThemeColor(holder.mView, R.attr.colorOnPrimary));
            holder.activate.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public int getItemCount() {
        return presets.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView presetName;
        public final Button activate;
        public final ImageView delete;
        public Preset preset;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            presetName = view.findViewById(R.id.preset_name);
            activate = view.findViewById(R.id.activate_preset);
            delete = view.findViewById(R.id.delete_preset);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + presetName.getText() + "'";
        }
    }
}