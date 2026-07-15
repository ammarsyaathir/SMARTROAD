package com.example.smartroadsystem;

import com.google.firebase.Timestamp;

public class HazardReport {

    /*
     * Firestore document ID.
     * This is assigned manually after loading the document.
     */
    private String id;

    /*
     * Fields saved in the reports collection.
     */
    private String hazardType;
    private String description;
    private String status;
    private String reportedBy;
    private String userId;
    private String imageUrl;
    private String adminNote;
    private String coordinates;

    private double latitude;
    private double longitude;

    private Timestamp submit;
    private Timestamp updatedAt;

    /*
     * Used only by the Android app if you calculate nearby hazards.
     * It does not need to exist in Firestore.
     */
    private float distanceMeters;

    public HazardReport() {
        // Required empty constructor for Firestore.
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHazardType() {
        return hazardType;
    }

    public void setHazardType(String hazardType) {
        this.hazardType = hazardType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    public String getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Timestamp getSubmit() {
        return submit;
    }

    public void setSubmit(Timestamp submit) {
        this.submit = submit;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public float getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(float distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
}