package com.aricneto.twistytimer.fragment;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.aricneto.twistify.R;
import com.aricneto.twistytimer.TwistyTimer;
import com.aricneto.twistytimer.adapter.TimeCursorAdapter;
import com.aricneto.twistytimer.database.DatabaseHandler;
import com.aricneto.twistytimer.database.TimeTaskLoader;
import com.aricneto.twistytimer.items.Solve;
import com.aricneto.twistytimer.layout.Fab;
import com.aricneto.twistytimer.utils.PuzzleUtils;
import com.aricneto.twistytimer.utils.ThemeUtils;
import com.gordonwong.materialsheetfab.DimOverlayFrameLayout;
import com.gordonwong.materialsheetfab.MaterialSheetFab;
import com.gordonwong.materialsheetfab.MaterialSheetFabEventListener;

import org.joda.time.DateTime;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

import static com.aricneto.twistytimer.utils.TTIntent.*;

public class TimerListFragment extends BaseFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String PUZZLE         = "puzzle";
    private static final String PUZZLE_SUBTYPE = "puzzle_type";
    private static final String HISTORY        = "history";

    private static final int TASK_LOADER_ID = 14;
    public MaterialSheetFab<Fab> materialSheetFab;
    // True if you want to search history, false if you only want to search session
    boolean         history;

    private Unbinder mUnbinder;
    @BindView(R.id.list)                 RecyclerView          recyclerView;
    @BindView(R.id.nothing_here)         ImageView             nothingHere;
    @BindView(R.id.nothing_text)         TextView              nothingText;
    @BindView(R.id.send_to_history_card) CardView              moveToHistory;
    @BindView(R.id.clear_button)         TextView              clearButton;
    @BindView(R.id.divider01)            View                  dividerView;
    @BindView(R.id.archive_button)       TextView              archiveButton;
    @BindView(R.id.fab_button)           Fab                   fabButton;
    @BindView(R.id.overlay)              DimOverlayFrameLayout overlay;
    @BindView(R.id.fab_sheet)            CardView              fabSheet;
    @BindView(R.id.fab_share_ao12)       TextView              fabShareAo12;
    @BindView(R.id.fab_share_ao5)        TextView              fabShareAo5;
    @BindView(R.id.fab_share_histogram)  TextView              fabShareHistogram;
    @BindView(R.id.fab_add_time)         TextView              fabAddTime;
    @BindView(R.id.fab_scroll)           ScrollView            fabScroll;

    private String            currentPuzzle;
    private String            currentPuzzleSubtype;
    private TimeCursorAdapter timeCursorAdapter;
    private Context           mContext;

    View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final DatabaseHandler dbHandler = TwistyTimer.getDBHandler();

            switch (view.getId()) {
                case R.id.fab_share_ao12:
                    if (!PuzzleUtils.shareAverageOf(12, currentPuzzle, currentPuzzleSubtype, dbHandler, getContext())) {
                        Toast.makeText(getContext(), R.string.fab_share_error, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case R.id.fab_share_ao5:
                    if (!PuzzleUtils.shareAverageOf(5, currentPuzzle, currentPuzzleSubtype, dbHandler, getContext())) {
                        Toast.makeText(getContext(), R.string.fab_share_error, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case R.id.fab_share_histogram:
                    if (!PuzzleUtils.shareHistogramOf(currentPuzzle, currentPuzzleSubtype, dbHandler, getContext())) {
                        Toast.makeText(getContext(), R.string.fab_share_error, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case R.id.fab_add_time:
                    new MaterialDialog.Builder(getContext())
                        .title(R.string.add_time)
                        .input(getString(R.string.add_time_hint), "", false, new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                int time = PuzzleUtils.parseTime(input.toString());
                                if (time != 0) {
                                    dbHandler.addSolve(new Solve(time, currentPuzzle,
                                        currentPuzzleSubtype, new DateTime().getMillis(), "", PuzzleUtils.NO_PENALTY, "", false));
                                    broadcast(CATEGORY_TIME_DATA_CHANGES, ACTION_TIME_ADDED);
                                }

                            }
                        })
                        .positiveText(R.string.action_add)
                        .negativeText(R.string.action_cancel)
                        .show();
                    break;
            }
        }
    };

    // Receives broadcasts from the timer
    private TTFragmentBroadcastReceiver mTimeDataChangedReceiver
            = new TTFragmentBroadcastReceiver(this, CATEGORY_TIME_DATA_CHANGES) {
        @Override
        public void onReceiveWhileAdded(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_TIME_ADDED:
                    // When "history" is enabled, the list of times does not include times from
                    // the current session. Times are only added to the current session, so
                    // there is no need to refresh the "history" list on adding a session time.
                    if (! history)
                        reloadList();
                    break;

                case ACTION_TIMES_MODIFIED:
                    reloadList();
                    break;

                case ACTION_HISTORY_TIMES_SHOWN:
                    history = true;
                    reloadList();
                    break;

                case ACTION_SESSION_TIMES_SHOWN:
                    history = false;
                    reloadList();
                    break;

                case ACTION_DELETE_SELECTED_TIMES:
                    // Operation will delete times and then broadcast "ACTION_TIMES_MODIFIED".
                    timeCursorAdapter.deleteAllSelected();
                    break;
            }
        }
    };

    public TimerListFragment() {
        // Required empty public constructor
    }

    // We have to put a boolean history here because it resets when we change puzzles.
    public static TimerListFragment newInstance(String puzzle, String puzzleType, boolean history) {
        TimerListFragment fragment = new TimerListFragment();
        Bundle args = new Bundle();
        args.putString(PUZZLE, puzzle);
        args.putBoolean(HISTORY, history);
        args.putString(PUZZLE_SUBTYPE, puzzleType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentPuzzle = getArguments().getString(PUZZLE);
            currentPuzzleSubtype = getArguments().getString(PUZZLE_SUBTYPE);
            history = getArguments().getBoolean(HISTORY);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_time_list, container, false);
        mUnbinder = ButterKnife.bind(this, rootView);
        mContext = getActivity().getApplicationContext();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean clearEnabled = sharedPreferences.getBoolean("clearEnabled", false);

        if (clearEnabled) {
            dividerView.setVisibility(View.VISIBLE);
            clearButton.setVisibility(View.VISIBLE);
            archiveButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_timer_sand_black_18dp, 0, 0, 0);
        }

        materialSheetFab = new MaterialSheetFab<>(
            fabButton, fabSheet, overlay, Color.WHITE, ThemeUtils.fetchAttrColor(getActivity(), R.attr.colorPrimary));

        materialSheetFab.setEventListener(new MaterialSheetFabEventListener() {
            @Override
            public void onSheetShown() {
                super.onSheetShown();
                fabScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        fabScroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });

        fabShareAo12.setOnClickListener(clickListener);
        fabShareAo5.setOnClickListener(clickListener);
        fabShareHistogram.setOnClickListener(clickListener);
        fabAddTime.setOnClickListener(clickListener);

        archiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Spannable text = new SpannableString(getString(R.string.move_solves_to_history_content) + "  ");
                ImageSpan icon = new ImageSpan(getContext(), R.drawable.ic_icon_history_demonstration);
                text.setSpan(icon, text.length() - 1, text.length(), 0);

                new MaterialDialog.Builder(getContext())
                    .title(R.string.move_solves_to_history)
                    .content(text)
                    .positiveText(R.string.action_move)
                    .negativeText(R.string.action_cancel)
                    .neutralColor(ContextCompat.getColor(getContext(), R.color.black_secondary))
                    .negativeColor(ContextCompat.getColor(getContext(), R.color.black_secondary))
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(MaterialDialog dialog, DialogAction which) {
                            TwistyTimer.getDBHandler().moveAllSolvesToHistory(
                                    currentPuzzle, currentPuzzleSubtype);
                            broadcast(CATEGORY_TIME_DATA_CHANGES, ACTION_TIMES_MOVED_TO_HISTORY);
                            reloadList();
                        }
                    })
                    .show();
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new MaterialDialog.Builder(getContext())
                    .title(R.string.remove_session_title)
                    .content(R.string.remove_session_confirmation_content)
                    .positiveText(R.string.action_remove)
                    .negativeText(R.string.action_cancel)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(MaterialDialog dialog, DialogAction which) {
                            TwistyTimer.getDBHandler().deleteAllFromSession(
                                    currentPuzzle, currentPuzzleSubtype);
                            reloadList();
                        }
                    })
                    .show();
            }
        });

        setupRecyclerView();
        getLoaderManager().initLoader(TASK_LOADER_ID, null, this);

        // Register a receiver to update if something has changed
        registerReceiver(mTimeDataChangedReceiver);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUnbinder.unbind();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // To fix memory leaks
        unregisterReceiver(mTimeDataChangedReceiver);
        getLoaderManager().destroyLoader(TASK_LOADER_ID);
    }

    public void reloadList() {
        getLoaderManager().restartLoader(TASK_LOADER_ID, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new TimeTaskLoader(mContext, currentPuzzle, currentPuzzleSubtype, history);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        timeCursorAdapter.swapCursor(cursor);
        recyclerView.getAdapter().notifyDataSetChanged();
        setEmptyState(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        timeCursorAdapter.swapCursor(null);
    }

    public void setEmptyState(Cursor cursor) {
        if (cursor.getCount() == 0) {
            nothingHere.setVisibility(View.VISIBLE);
            nothingText.setVisibility(View.VISIBLE);
            moveToHistory.setVisibility(View.GONE);
            if (history) {
                nothingHere.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.notherehistory));
                nothingText.setText(R.string.list_empty_state_message_history);
            } else {
                nothingHere.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.nothere2));
                nothingText.setText(R.string.list_empty_state_message);
            }
        } else {
            nothingHere.setVisibility(View.INVISIBLE);
            nothingText.setVisibility(View.INVISIBLE);
            if (history)
                moveToHistory.setVisibility(View.GONE);
            else
                moveToHistory.setVisibility(View.VISIBLE);
        }
    }

    private void setupRecyclerView() {
        Activity parentActivity = getActivity();

        timeCursorAdapter = new TimeCursorAdapter(getActivity(), null, this);

        // Set different managers to support different orientations
        StaggeredGridLayoutManager gridLayoutManagerHorizontal =
            new StaggeredGridLayoutManager(6, StaggeredGridLayoutManager.VERTICAL);
        StaggeredGridLayoutManager gridLayoutManagerVertical =
            new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL);

        // Adapt to orientation
        if (parentActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
            recyclerView.setLayoutManager(gridLayoutManagerVertical);
        else
            recyclerView.setLayoutManager(gridLayoutManagerHorizontal);

        recyclerView.setAdapter(timeCursorAdapter);

    }
}
