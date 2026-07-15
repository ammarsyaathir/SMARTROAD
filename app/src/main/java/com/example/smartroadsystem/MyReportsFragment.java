package com.example.smartroadsystem;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MyReportsFragment extends Fragment {

    private RecyclerView recyclerMyReports;
    private TextView tvEmptyReports;
    private View reportProgressBar;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private ReportHistoryAdapter adapter;

    private final List<HazardReport> reportList =
            new ArrayList<>();

    public MyReportsFragment() {
        // Required empty public constructor.
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_my_reports,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(
                view,
                savedInstanceState
        );

        initialiseViews(view);
        initialiseFirebase();
        setupRecyclerView();
        loadUserReports();
    }

    private void initialiseViews(
            @NonNull View view
    ) {
        recyclerMyReports =
                view.findViewById(
                        R.id.recyclerMyReports
                );

        tvEmptyReports =
                view.findViewById(
                        R.id.tvEmptyReports
                );

        reportProgressBar =
                view.findViewById(
                        R.id.reportProgressBar
                );
    }

    private void initialiseFirebase() {
        firebaseAuth =
                FirebaseAuth.getInstance();

        firestore =
                FirebaseFirestore.getInstance();
    }

    private void setupRecyclerView() {
        adapter =
                new ReportHistoryAdapter(
                        reportList,
                        this::showReportDetailsDialog
                );

        recyclerMyReports.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );

        recyclerMyReports.setHasFixedSize(true);

        recyclerMyReports.setAdapter(
                adapter
        );
    }

    private void loadUserReports() {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            showLoading(false);

            showEmptyState(
                    "Please sign in to view your reports."
            );

            return;
        }

        showLoading(true);

        String currentUserId =
                currentUser.getUid();

        /*
         * No orderBy() is used here.
         *
         * This avoids requiring a Firestore composite index.
         * Reports are sorted locally after downloading.
         */
        firestore.collection("reports")
                .whereEqualTo(
                        "userId",
                        currentUserId
                )
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (!isAdded()) {
                        return;
                    }

                    reportList.clear();

                    for (
                            DocumentSnapshot document :
                            querySnapshot.getDocuments()
                    ) {
                        try {
                            HazardReport report =
                                    document.toObject(
                                            HazardReport.class
                                    );

                            if (report == null) {
                                continue;
                            }

                            /*
                             * Firestore document ID is not automatically
                             * included inside the model.
                             */
                            report.setId(
                                    document.getId()
                            );

                            reportList.add(
                                    report
                            );

                        } catch (Exception exception) {

                            /*
                             * Skip only the malformed document rather than
                             * failing the entire report-history page.
                             */
                            exception.printStackTrace();
                        }
                    }

                    /*
                     * Sort newest reports first using the submit timestamp.
                     */
                    sortReportsByNewest();

                    adapter.notifyDataSetChanged();

                    showLoading(false);

                    if (reportList.isEmpty()) {
                        showEmptyState(
                                "No reports submitted yet."
                        );
                    } else {
                        showReportList();
                    }
                })
                .addOnFailureListener(exception -> {

                    if (!isAdded()) {
                        return;
                    }

                    showLoading(false);

                    String errorMessage =
                            exception.getMessage();

                    if (
                            errorMessage == null ||
                                    errorMessage.trim().isEmpty()
                    ) {
                        errorMessage =
                                "Unknown Firestore error";
                    }

                    Toast.makeText(
                            requireContext(),
                            "Unable to load reports:\n" +
                                    errorMessage,
                            Toast.LENGTH_LONG
                    ).show();

                    showEmptyState(
                            "Unable to load your reports."
                    );
                });
    }

    private void sortReportsByNewest() {
        Collections.sort(
                reportList,
                (firstReport, secondReport) -> {

                    Timestamp firstTimestamp =
                            firstReport.getSubmit();

                    Timestamp secondTimestamp =
                            secondReport.getSubmit();

                    if (
                            firstTimestamp == null &&
                                    secondTimestamp == null
                    ) {
                        return 0;
                    }

                    if (firstTimestamp == null) {
                        return 1;
                    }

                    if (secondTimestamp == null) {
                        return -1;
                    }

                    /*
                     * Descending order:
                     * newest report appears first.
                     */
                    return secondTimestamp
                            .toDate()
                            .compareTo(
                                    firstTimestamp.toDate()
                            );
                }
        );
    }

    private void showReportDetailsDialog(
            @NonNull HazardReport report
    ) {
        String hazardType =
                safeText(
                        report.getHazardType(),
                        "Road Hazard"
                );

        String description =
                safeText(
                        report.getDescription(),
                        "No description provided."
                );

        String status =
                safeText(
                        report.getStatus(),
                        "New"
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

        String reportedBy =
                safeText(
                        report.getReportedBy(),
                        "SMARTROAD User"
                );

        String submittedDate =
                formatTimestamp(
                        report.getSubmit()
                );

        String updatedDate =
                formatTimestamp(
                        report.getUpdatedAt()
                );

        String reportId =
                safeText(
                        report.getId(),
                        "Unavailable"
                );

        String details =
                "Report ID\n" +
                        reportId +
                        "\n\n" +

                        "Status\n" +
                        status +
                        "\n\n" +

                        "Description\n" +
                        description +
                        "\n\n" +

                        "Coordinates\n" +
                        coordinates +
                        "\n\n" +

                        "Reported By\n" +
                        reportedBy +
                        "\n\n" +

                        "Submitted\n" +
                        submittedDate +
                        "\n\n" +

                        "Last Updated\n" +
                        updatedDate +
                        "\n\n" ;

        new MaterialAlertDialogBuilder(
                requireContext()
        )
                .setTitle(hazardType)
                .setMessage(details)
                .setPositiveButton(
                        "Close",
                        null
                )
                .show();
    }

    private String formatTimestamp(
            @Nullable Timestamp timestamp
    ) {
        if (timestamp == null) {
            return "Not available";
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

    private String safeText(
            @Nullable String value,
            @NonNull String fallback
    ) {
        if (
                value == null ||
                        value.trim().isEmpty()
        ) {
            return fallback;
        }

        return value.trim();
    }

    private void showLoading(
            boolean loading
    ) {
        if (
                reportProgressBar == null ||
                        recyclerMyReports == null ||
                        tvEmptyReports == null
        ) {
            return;
        }

        reportProgressBar.setVisibility(
                loading
                        ? View.VISIBLE
                        : View.GONE
        );

        if (loading) {
            recyclerMyReports.setVisibility(
                    View.GONE
            );

            tvEmptyReports.setVisibility(
                    View.GONE
            );
        }
    }

    private void showReportList() {
        if (
                recyclerMyReports == null ||
                        tvEmptyReports == null ||
                        reportProgressBar == null
        ) {
            return;
        }

        reportProgressBar.setVisibility(
                View.GONE
        );

        tvEmptyReports.setVisibility(
                View.GONE
        );

        recyclerMyReports.setVisibility(
                View.VISIBLE
        );
    }

    private void showEmptyState(
            @NonNull String message
    ) {
        if (
                recyclerMyReports == null ||
                        tvEmptyReports == null ||
                        reportProgressBar == null
        ) {
            return;
        }

        reportProgressBar.setVisibility(
                View.GONE
        );

        recyclerMyReports.setVisibility(
                View.GONE
        );

        tvEmptyReports.setText(
                message
        );

        tvEmptyReports.setVisibility(
                View.VISIBLE
        );
    }

    @Override
    public void onResume() {
        super.onResume();

        /*
         * Reload reports when the user returns to this page.
         */
        if (
                firestore != null &&
                        firebaseAuth != null &&
                        recyclerMyReports != null
        ) {
            loadUserReports();
        }
    }
}