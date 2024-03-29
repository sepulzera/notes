package de.sepulzera.notes.ui.widgets.rundo;

import androidx.annotation.NonNull;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * <code>RunDo</code> implementations monitor and manipulate {@link EditText} fields, by
 * periodically saving snippets of text to {@link java.util.Collection}s and reinstating them
 * through {@link #undo()} and {@link #redo()} calls.
 *
 * @author Tom Calver
 */
@SuppressWarnings("unused")
public interface RunDo extends TextWatcher, WriteToArrayDeque {

    String TAG = "RunDo";

    String IDENT_TAG = "ident";

    String UNDO_TAG = "undo_queue";
    String REDO_TAG = "redo_queue";
    String OLD_TEXT_TAG = "old_text";
    String CONFIG_CHANGE_TAG = "return_from_config_change";
    String TRACKING_TAG = "tracking_state";

    int DEFAULT_QUEUE_SIZE = 10;
    int DEFAULT_TIMER_LENGTH = 2000;

    int TRACKING_STARTED = 12;
    int TRACKING_CURRENT = TRACKING_STARTED + 1;
    int TRACKING_ENDED = TRACKING_CURRENT + 1;

    /**
     * @return The given ident for the instance or {@code null}.
     */
    String getIdent();

    /**
     * Sets size of Undo and Redo queues. Default size is {@value #DEFAULT_QUEUE_SIZE}.
     * Calling this clears any elements already in the queues.
     * @param size New queue size
     */
    void setQueueSize(int size);

    /**
     * Sets time in milliseconds before text is committed to the undo queue. This timer begins
     * immediately after text entry stops, and is reset if text changes before the timer can
     * complete. Default value is {@value #DEFAULT_TIMER_LENGTH}.
     * @param lengthInMillis Time in milliseconds before text is committed to undo queue
     */
    void setTimerLength(long lengthInMillis);

    /**
     * @return True if any change is available to undo.
     * @see #undo()
     */
    boolean canUndo();

    /**
     * @return True if any change is available to redo.
     * @see #redo()
     */
    boolean canRedo();

    /**
     * Updates attached {@link EditText} with text from the last entry in the undo queue, such that
     * it reverts to an earlier state.
     */
    void undo();

    /**
     * Reverts changes made by the last {@link #undo()} call.
     */
    void redo();

    /**
     * Removes all entries from both undo and redo queues.
     */
    void clearAllQueues();

    /**
     * Used by{@link RunDo} implementations to establish a link with an {@link EditText}
     */
    interface TextLink {

        /**
         *
         * @return The {@link EditText} to be monitored and updated by a {@link RunDo}
         * implementation.
         */
        EditText getEditTextForRunDo(String ident);

    }

    /**
     * Implement to receive callbacks whenever {@link #undo()} or {@link #redo()} methods are called
     */
    @SuppressWarnings("EmptyMethod")
    interface Callbacks {

        /**
         * {@link #undo()} called
         */
        void undoCalled();

        /**
         * {@link #redo()} called
         */
        void redoCalled();

        /**
         * Fired when there are no further changes to undo
         */
        void undoEmpty();

        /**
         * Fired when there are no further changes to redo
         */
        void redoEmpty();

        /**
         * Fired when any change becomes available to undo.
         * Only fired when previously no undo was available (see {@link #undoEmpty()})
         */
        void undoAvailable();

        /**
         * Fired when any change becomes available to redo.
         * Only fired when previously no redo was available (see {@link #redoEmpty()})
         */
        void redoAvailable();
    }

    /**
     * Returns a {@link RunDo} implementation which extends either
     * {@link Fragment} or {@link android.app.Fragment}.
     */
    final class Factory {

        private Factory() { throw new AssertionError(); }

        public static RunDo getInstance(@NonNull FragmentManager fm, String ident) {

            String tag = ident == null || ident.isEmpty() ? RunDo.TAG : RunDo.TAG + '_' + ident;

            RunDoSupport frag = (RunDoSupport) fm.findFragmentByTag(tag);

            if (frag == null) {
                frag = RunDoSupport.newInstance(ident);
                fm.beginTransaction().add(frag, tag).commit();
            }

            return frag;

        }
    }

}
