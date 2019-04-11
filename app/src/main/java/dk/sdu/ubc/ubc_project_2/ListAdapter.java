package dk.sdu.ubc.ubc_project_2;

import android.net.wifi.ScanResult;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ListViewHolder> {

    private List<ScanResult> data;

    public ListAdapter(List<ScanResult> data) {
        this.data = data;
    }

    public void add(List<ScanResult> data) {
        this.data.addAll(data);
        this.data.add(null); // breaker / divider
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ListViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.list_row, viewGroup, false);
        return new ListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ListViewHolder viewHolder, int i) {
        ScanResult scanResult = data.get(i);
        if (scanResult != null) { // breaker / divider
            viewHolder.timestamp.setText(String.valueOf(scanResult.timestamp));
            viewHolder.name.setText(scanResult.BSSID);
            viewHolder.strength.setText(String.valueOf(scanResult.level));
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public static class ListViewHolder extends RecyclerView.ViewHolder {

        public TextView timestamp;
        public TextView name;
        public TextView strength;

        public ListViewHolder(@NonNull View view) {
            super(view);
            timestamp = view.findViewById(R.id.timestamp);
            name = view.findViewById(R.id.name);
            strength = view.findViewById(R.id.strength);
        }
    }

}
