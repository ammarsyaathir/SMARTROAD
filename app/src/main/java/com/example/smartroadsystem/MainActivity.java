package com.example.smartroadsystem;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    /*
     * Tracks which main bottom-navigation page is active.
     */
    private int currentNavigationItem = R.id.nav_map;

    /*
     * Used when MapFragment opens ReportFragment with coordinates.
     *
     * Calling setSelectedItemId(nav_report) triggers the bottom-navigation
     * listener. This flag prevents the listener from creating another empty
     * ReportFragment and replacing the one containing coordinates.
     */
    private boolean openingReportFromMap = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(
                R.id.bottom_navigation
        );

        setupBottomNavigation();
        setupBackButton();

        if (savedInstanceState == null) {
            /*
             * Open MapFragment once when MainActivity starts.
             */
            currentNavigationItem = R.id.nav_map;

            replaceFragment(
                    new MapFragment(),
                    false
            );

            /*
             * Update only the checked appearance.
             */
            bottomNavigation
                    .getMenu()
                    .findItem(R.id.nav_map)
                    .setChecked(true);

        } else {
            /*
             * Restore the correct bottom-navigation appearance
             * after rotation or activity recreation.
             */
            synchronizeNavigation();
        }

        /*
         * Keep navigation synchronized after fragments are restored
         * from the back stack.
         */
        getSupportFragmentManager()
                .addOnBackStackChangedListener(
                        this::synchronizeNavigation
                );
    }

    private void setupBottomNavigation() {

        bottomNavigation.setOnItemSelectedListener(
                menuItem -> {

                    int itemId = menuItem.getItemId();

                    /*
                     * MapFragment already opened a ReportFragment containing
                     * latitude and longitude. Do not replace it with a new
                     * empty ReportFragment.
                     */
                    if (
                            openingReportFromMap &&
                                    itemId == R.id.nav_report
                    ) {
                        openingReportFromMap = false;
                        currentNavigationItem = R.id.nav_report;
                        return true;
                    }

                    /*
                     * Do not recreate the currently selected main fragment.
                     */
                    if (itemId == currentNavigationItem) {
                        return true;
                    }

                    Fragment selectedFragment;

                    if (itemId == R.id.nav_map) {

                        selectedFragment = new MapFragment();

                    } else if (itemId == R.id.nav_report) {

                        selectedFragment = new ReportFragment();

                    } else if (itemId == R.id.nav_profile) {

                        selectedFragment = new ProfileFragment();

                    } else {

                        return false;
                    }

                    /*
                     * Remove temporary pages such as MyReportsFragment
                     * or a ReportFragment opened from the map.
                     */
                    clearBackStack();

                    currentNavigationItem = itemId;

                    replaceFragment(
                            selectedFragment,
                            false
                    );

                    return true;
                }
        );

        /*
         * Selecting the same navigation item again keeps the
         * current fragment and its current state.
         */
        bottomNavigation.setOnItemReselectedListener(
                menuItem -> {
                    // No action required.
                }
        );
    }

    /**
     * Called by MapFragment when the user presses
     * "Report Hazard at This Location".
     */
    public void openReportFragment(
            @NonNull ReportFragment reportFragment
    ) {
        openingReportFromMap = true;
        currentNavigationItem = R.id.nav_report;

        /*
         * This updates the selected navigation item.
         *
         * openingReportFromMap prevents the listener from replacing
         * the supplied ReportFragment.
         */
        bottomNavigation.setSelectedItemId(
                R.id.nav_report
        );

        replaceFragment(
                reportFragment,
                true
        );
    }

    /**
     * Opens MyReportsFragment from ProfileFragment.
     *
     * My Reports belongs under Profile, so the Profile item stays selected.
     */
    public void openMyReportsFragment() {
        currentNavigationItem = R.id.nav_profile;

        /*
         * Update only the checked visual state.
         *
         * Do not call setSelectedItemId(nav_report), because that would
         * open the hazard-submission page.
         */
        bottomNavigation
                .getMenu()
                .findItem(R.id.nav_profile)
                .setChecked(true);

        replaceFragment(
                new MyReportsFragment(),
                true
        );
    }

    private void replaceFragment(
            @NonNull Fragment fragment,
            boolean addToBackStack
    ) {
        FragmentManager fragmentManager =
                getSupportFragmentManager();

        androidx.fragment.app.FragmentTransaction transaction =
                fragmentManager
                        .beginTransaction()
                        .setCustomAnimations(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        )
                        .replace(
                                R.id.fragment_container,
                                fragment
                        );

        if (addToBackStack) {
            transaction.addToBackStack(
                    fragment.getClass().getSimpleName()
            );
        }

        transaction.commit();
    }

    private void setupBackButton() {

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        FragmentManager fragmentManager =
                                getSupportFragmentManager();

                        /*
                         * MyReportsFragment returns to ProfileFragment.
                         *
                         * ReportFragment opened from MapFragment returns
                         * to MapFragment.
                         */
                        if (
                                fragmentManager
                                        .getBackStackEntryCount() > 0
                        ) {
                            fragmentManager.popBackStack();
                            return;
                        }

                        /*
                         * Pressing Back on the main Report or Profile page
                         * returns the user to Map.
                         */
                        if (
                                currentNavigationItem != R.id.nav_map
                        ) {
                            currentNavigationItem = R.id.nav_map;

                            bottomNavigation.setSelectedItemId(
                                    R.id.nav_map
                            );

                            return;
                        }

                        /*
                         * Close the application only when already on Map.
                         */
                        setEnabled(false);

                        getOnBackPressedDispatcher()
                                .onBackPressed();
                    }
                }
        );
    }

    private void synchronizeNavigation() {

        Fragment currentFragment =
                getSupportFragmentManager()
                        .findFragmentById(
                                R.id.fragment_container
                        );

        if (currentFragment instanceof MapFragment) {

            currentNavigationItem = R.id.nav_map;

        } else if (currentFragment instanceof ReportFragment) {

            currentNavigationItem = R.id.nav_report;

        } else if (
                currentFragment instanceof ProfileFragment ||
                        currentFragment instanceof MyReportsFragment
        ) {

            /*
             * MyReportsFragment belongs under Profile.
             */
            currentNavigationItem = R.id.nav_profile;
        }

        /*
         * Update only the visual checked state.
         * This does not trigger page replacement.
         */
        if (
                bottomNavigation
                        .getMenu()
                        .findItem(currentNavigationItem) != null
        ) {
            bottomNavigation
                    .getMenu()
                    .findItem(currentNavigationItem)
                    .setChecked(true);
        }
    }

    private void clearBackStack() {

        FragmentManager fragmentManager =
                getSupportFragmentManager();

        if (
                fragmentManager
                        .getBackStackEntryCount() > 0
        ) {
            fragmentManager.popBackStackImmediate(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
            );
        }
    }
}