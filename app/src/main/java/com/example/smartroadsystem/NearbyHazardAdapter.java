package com.example.smartroadsystem;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class NearbyHazardAdapter extends
        RecyclerView.Adapter<
                NearbyHazardAdapter.NearbyHazardViewHolder> {

    public interface OnHazardClickListener {
        void onHazardClick(
                @NonNull HazardReport report
        );
    }

    private final List<HazardReport> hazardList;
    private final OnHazardClickListener listener;

    public NearbyHazardAdapter(
            @NonNull List<HazardReport> hazardList,
            @NonNull OnHazardClickListener listener
    ) {
        this.hazardList = hazardList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NearbyHazardViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                R.layout.item_nearby_hazard,
                                parent,
                                false
                        );

        return new NearbyHazardViewHolder(
                view
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull NearbyHazardViewHolder holder,
            int position
    ) {
        holder.bind(
                hazardList.get(position),
                listener
        );
    }

    @Override
    public int getItemCount() {
        return hazardList.size();
    }

    static class NearbyHazardViewHolder
            extends RecyclerView.ViewHolder {

        private final TextView tvNearbyHazardType;
        private final TextView tvNearbyDescription;
        private final TextView tvNearbyDistance;
        private final TextView tvNearbyStatus;

        public NearbyHazardViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            tvNearbyHazardType =
                    itemView.findViewById(
                            R.id.tvNearbyHazardType
                    );

            tvNearbyDescription =
                    itemView.findViewById(
                            R.id.tvNearbyDescription
                    );

            tvNearbyDistance =
                    itemView.findViewById(
                            R.id.tvNearbyDistance
                    );

            tvNearbyStatus =
                    itemView.findViewById(
                            R.id.tvNearbyStatus
                    );
        }

        private void bind(
                @NonNull HazardReport report,
                @NonNull OnHazardClickListener listener
        ) {
            String hazardType =
                    report.getHazardType();

            String description =
                    report.getDescription();

            String status =
                    report.getStatus();

            if (
                    hazardType == null ||
                            hazardType.trim().isEmpty()
            ) {
                hazardType = "Road Hazard";
            }

            if (
                    description == null ||
                            description.trim().isEmpty()
            ) {
                description =
                        "No description provided.";
            }

            if (
                    status == null ||
                            status.trim().isEmpty()
            ) {
                status = "New";
            }

            tvNearbyHazardType.setText(
                    hazardType
            );

            tvNearbyDescription.setText(
                    description
            );

            tvNearbyStatus.setText(
                    status
            );

            float distanceMeters =
                    report.getDistanceMeters();

            if (distanceMeters < 1000f) {
                tvNearbyDistance.setText(
                        String.format(
                                Locale.US,
                                "%.0f m away",
                                distanceMeters
                        )
                );
            } else {
                tvNearbyDistance.setText(
                        String.format(
                                Locale.US,
                                "%.2f km away",
                                distanceMeters / 1000f
                        )
                );
            }

            itemView.setOnClickListener(
                    view ->
                            listener.onHazardClick(
                                    report
                            )
            );
        }
    }
}