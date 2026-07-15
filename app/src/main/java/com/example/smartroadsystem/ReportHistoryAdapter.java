package com.example.smartroadsystem;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ReportHistoryAdapter
        extends RecyclerView.Adapter<
        ReportHistoryAdapter.ReportViewHolder> {

    public interface OnReportClickListener {
        void onReportClick(
                @NonNull HazardReport report
        );
    }

    private final List<HazardReport> reportList;
    private final OnReportClickListener listener;

    public ReportHistoryAdapter(
            @NonNull List<HazardReport> reportList,
            @NonNull OnReportClickListener listener
    ) {
        this.reportList = reportList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view =
                LayoutInflater.from(
                        parent.getContext()
                ).inflate(
                        R.layout.item_report_history,
                        parent,
                        false
                );

        return new ReportViewHolder(
                view
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull ReportViewHolder holder,
            int position
    ) {
        HazardReport report =
                reportList.get(position);

        holder.bind(
                report,
                listener
        );
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    static class ReportViewHolder
            extends RecyclerView.ViewHolder {

        private final TextView tvHazardType;
        private final TextView tvDescription;
        private final TextView tvStatus;
        private final TextView tvSubmittedDate;
        private final TextView tvCoordinates;

        public ReportViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            tvHazardType =
                    itemView.findViewById(
                            R.id.tvHazardType
                    );

            tvDescription =
                    itemView.findViewById(
                            R.id.tvReportDescription
                    );

            tvStatus =
                    itemView.findViewById(
                            R.id.tvReportStatus
                    );

            tvSubmittedDate =
                    itemView.findViewById(
                            R.id.tvSubmittedDate
                    );

            tvCoordinates =
                    itemView.findViewById(
                            R.id.tvReportCoordinates
                    );
        }

        private void bind(
                @NonNull HazardReport report,
                @NonNull OnReportClickListener listener
        ) {
            String hazardType =
                    report.getHazardType();

            if (
                    hazardType == null ||
                            hazardType.trim().isEmpty()
            ) {
                hazardType = "Road Hazard";
            }

            tvHazardType.setText(
                    hazardType
            );

            String description =
                    report.getDescription();

            if (
                    description == null ||
                            description.trim().isEmpty()
            ) {
                description =
                        "No description provided.";
            }

            tvDescription.setText(
                    description
            );

            String status =
                    report.getStatus();

            if (
                    status == null ||
                            status.trim().isEmpty()
            ) {
                status = "New";
            }

            tvStatus.setText(
                    status
            );

            applyStatusStyle(
                    tvStatus,
                    status
            );

            tvSubmittedDate.setText(
                    formatTimestamp(
                            report.getSubmit()
                    )
            );

            String coordinates =
                    report.getCoordinates();

            if (
                    coordinates == null ||
                            coordinates.trim().isEmpty()
            ) {
                coordinates =
                        String.format(
                                Locale.US,
                                "Lat: %.6f, Long: %.6f",
                                report.getLatitude(),
                                report.getLongitude()
                        );
            }

            tvCoordinates.setText(
                    coordinates
            );

            itemView.setOnClickListener(view ->
                    listener.onReportClick(
                            report
                    )
            );
        }

        private void applyStatusStyle(
                @NonNull TextView statusView,
                @NonNull String status
        ) {
            String normalized =
                    status.trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (
                    normalized.equals("resolved") ||
                            normalized.equals("completed") ||
                            normalized.equals("fixed") ||
                            normalized.equals("closed")
            ) {
                statusView.setTextColor(
                        Color.parseColor("#15803D")
                );

                statusView.setBackgroundResource(
                        R.drawable.bg_status_resolved
                );

            } else if (
                    normalized.equals("rejected") ||
                            normalized.equals("invalid")
            ) {
                statusView.setTextColor(
                        Color.parseColor("#B91C1C")
                );

                statusView.setBackgroundResource(
                        R.drawable.bg_status_rejected
                );

            } else if (
                    normalized.equals("in progress") ||
                            normalized.equals("in-progress") ||
                            normalized.equals("under review")
            ) {
                statusView.setTextColor(
                        Color.parseColor("#1D4ED8")
                );

                statusView.setBackgroundResource(
                        R.drawable.bg_status_progress
                );

            } else {
                statusView.setTextColor(
                        Color.parseColor("#B45309")
                );

                statusView.setBackgroundResource(
                        R.drawable.bg_status_pending
                );
            }
        }

        private String formatTimestamp(
                Timestamp timestamp
        ) {
            if (timestamp == null) {
                return "Date unavailable";
            }

            SimpleDateFormat formatter =
                    new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            Locale.getDefault()
                    );

            return formatter.format(
                    timestamp.toDate()
            );
        }
    }
}