package de.sepulzera.notes.ui.activity.note;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.util.List;
import java.util.Objects;

import de.sepulzera.notes.R;
import de.sepulzera.notes.bf.helper.StringUtil;
import de.sepulzera.notes.ds.model.Note;
import de.sepulzera.notes.ui.widgets.EditTextSelectable;
import de.sepulzera.notes.ui.widgets.rundo.RunDo;
import de.sepulzera.notes.ui.widgets.rundo.RunDoSupport;

public class NoteEditFragment extends Fragment implements EditTextSelectable.SelectionChangedListener {

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);

    final View view = (container == null)? getView() : inflater.inflate(R.layout.frag_note, container, false);
    if (view == null) {
      throw new IllegalStateException("Could not find view!");
    }

    if (null != savedInstanceState) {
      restoreState(savedInstanceState);
    }

    if (null == mNote || mIndex == -1) {
      throw new IllegalStateException("Initialization was not called!");
    }

    mView = view.findViewById(R.id.main_content);

    mEditMsg = mView.findViewById(R.id.note_msg);
    mEditMsg.addTextChangedListener(new TextWatcher() {
      public void afterTextChanged(Editable s) {
        mListener.onTextChanged(s.toString(), mEditMsg.hasFocus(), mEditMsg.getSelectionStart(), mEditMsg.getSelectionEnd());
      }
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
    });
    mEditMsg.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View view, boolean hasFocus) {
        if (mEditMsg.hasFocus()) {
          mListener.onTextChanged(getMsg(), true, mEditMsg.getSelectionStart(), mEditMsg.getSelectionEnd());
        }
      }
    });
    mEditMsg.addSelectionChangedListener(this);

    setEditable(mIsEditable);
    setMsg(mMsg != null? mMsg : mNote.getMsg());

    // only show keyboard on new notes
    if (0L == mNote.getId()) {
      mEditMsg.requestFocus();
    } else {
      mView.requestFocus();
    }

    return mView;
  }

  private void restoreState(@NonNull final Bundle savedInstanceState) {
    mIndex      = savedInstanceState.getInt(KEY_INDEX, -1);

    mNote       = (Note) savedInstanceState.getSerializable(Note.TAG_NOTE);
    mMsg        = savedInstanceState.getString(KEY_MSG);
    mIsEditable = savedInstanceState.getBoolean(KEY_EDITABLE, true);

    FragmentManager fragmentManager = getFragmentManager();
    if (fragmentManager == null) {
      throw new IllegalStateException("No FragmentManager!");
    }
    final List<Fragment> frags = fragmentManager.getFragments();
    if (frags.size() == 0) {
      throw new IllegalStateException("RunDo Fragment is lost");
    }
    // may have additional frags, but only the RunDo is needed
    for (final Fragment frag : frags) {
      if (frag instanceof RunDoSupport && String.valueOf(mIndex).equals(((RunDoSupport)frag).getIdent())) {
        mRunDo = (RunDoSupport)frag;
        break;
      }
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull final Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(KEY_INDEX, mIndex);
    outState.putSerializable(Note.TAG_NOTE, mNote);
    outState.putBoolean(KEY_EDITABLE, mIsEditable);
  }

  public void initialize(int index, @NonNull final Note note) {
    mIndex = index;
    mNote = note;
    mIsEditable = note.getId() == 0L || !NoteTabViewerActivity.mOpenNotesReadonly;
  }
  public Note getNote() { return mNote; }
  public int getIndex() { return mIndex; }

  public void copyToClipboard() {
    ClipboardManager cman = ((ClipboardManager) Objects.requireNonNull(getActivity()).getSystemService(Context.CLIPBOARD_SERVICE));
    if (cman != null) {
      cman.setPrimaryClip(
          ClipData.newPlainText("notes_" + mNote.getTitle(), getMsg()));
    }
  }

  public void clearNote() {
    setMsg("");
  }

  public void revert() {
    setMsg(mNote.getMsg());
  }

  public void deleteSelectedLines() {
    String oldMsg = getMsg();

    // fix selection

    int selStart = mEditMsg.getSelectionStart();
    int selEnd   = mEditMsg.getSelectionEnd();

    List<String> lines = StringUtil.getLines(oldMsg);
    int[] selectedLines = StringUtil.getSelectedLines(oldMsg, selStart, selEnd);

    boolean firstLineDeleted = selectedLines[0] == 0;
    boolean lastLineDeleted   = selectedLines[selectedLines.length - 1] == (lines.size() - 1);

    int posOfLine = oldMsg.lastIndexOf(lines.get(selectedLines[0]), selStart);
    int posInLine = selStart - posOfLine;

    int selPos;

    if (firstLineDeleted && lastLineDeleted) {
      // everything deleted
      selPos = 0;
    } else if (lastLineDeleted) {
      // last line deleted -> go up
      String previousLine = lines.get(selectedLines[0] - 1);
      int previousStartPos = oldMsg.lastIndexOf(previousLine, posOfLine - 1);

      if (previousLine.length() - 1 < posInLine) {
        selPos = previousStartPos + previousLine.length();
      } else {
        selPos = previousStartPos + posInLine;
      }
    } else {
      // stay
      String nextLine = lines.get(selectedLines[selectedLines.length - 1] + 1);
      if (nextLine.length() - 1 < posInLine) {
        selPos = posOfLine + (nextLine.length() > 0 ? nextLine.length() : 0);
      } else {
        selPos = posOfLine + posInLine;
      }
    }

    String msgDeletedLine = StringUtil.deleteLines(getMsg(), mEditMsg.getSelectionStart(), mEditMsg.getSelectionEnd());
    setMsg(msgDeletedLine);
    mEditMsg.setSelection(selPos, selPos);
  }

  public void duplicateSelectedLines() {
    String msgCopiedLine = StringUtil.duplicateLines(getMsg(), mEditMsg.getSelectionStart(), mEditMsg.getSelectionEnd());

    int selStart = mEditMsg.getSelectionStart();
    int selEnd   = mEditMsg.getSelectionEnd();

    setMsg(msgCopiedLine);
    int len = msgCopiedLine.length();

    selStart = (selStart > len? len : selStart);
    selEnd = (selEnd > len? len : selEnd);
    mEditMsg.setSelection(selStart, selEnd);
  }

  public void moveSelectedLinesUp() {
    String msgMovedLine = StringUtil.moveLinesUp(getMsg(), mEditMsg.getSelectionStart(), mEditMsg.getSelectionEnd());

    String oldMsg = getMsg();
    int selStart = mEditMsg.getSelectionStart();
    int selEnd   = mEditMsg.getSelectionEnd();

    List<String> lines = StringUtil.getLines(oldMsg);
    int[] selectedLines = StringUtil.getSelectedLines(oldMsg, selStart, selEnd);
    if (selectedLines[0] == 0) return;
    String movedLine = lines.get(selectedLines[0] - 1);

    setMsg(msgMovedLine);
    mEditMsg.setSelection(selStart - movedLine.length() - 1, selEnd - movedLine.length() - 1);
  }

  public void moveSelectedLinesDown() {
    String msgMovedLine = StringUtil.moveLinesDown(getMsg(), mEditMsg.getSelectionStart(), mEditMsg.getSelectionEnd());

    String oldMsg = getMsg();

    int selStart = mEditMsg.getSelectionStart();
    int selEnd   = mEditMsg.getSelectionEnd();

    List<String> lines = StringUtil.getLines(oldMsg);
    int[] selectedLines = StringUtil.getSelectedLines(oldMsg, selStart, selEnd);
    if (selectedLines[selectedLines.length - 1] == (lines.size() - 1)) return;
    String movedLine = lines.get(selectedLines[selectedLines.length - 1] + 1);

    setMsg(msgMovedLine);
    mEditMsg.setSelection(selStart + movedLine.length() + 1, selEnd + movedLine.length() + 1);
  }

  public boolean isEditable() { return mIsEditable; }

  public void setEditable(boolean editable) {
    mIsEditable = editable;
    if (editable) {
      mEditMsg.setEnabled(true);
    } else {
      mEditMsg.setEnabled(false);
    }

    if (editable && mRunDo == null) {
      FragmentManager fragmentManager = getFragmentManager();
      if (fragmentManager == null) {
        throw new IllegalStateException("No FragmentManager!");
      }
      mRunDo = RunDo.Factory.getInstance(fragmentManager, String.valueOf(mIndex));
    }

    mView.setBackgroundColor(getResources().getColor(editable? R.color.colorNoteBg : R.color.colorNoteBgReadonly, null));
  }

  public boolean isChanged() {
    return !StringUtil.equals(getMsg(), mNote.getMsg());
  }

  public String getMsg() {
    @Nullable Editable text = mEditMsg.getText();
    return text == null ? "" : text.toString();
  }

  private void setMsg(@NonNull String msg) {
    mEditMsg.setText(msg);
  }

  public boolean hasFocus() {
    return mEditMsg.hasFocus();
  }

  public int getSelectionStart() {
    return mEditMsg.getSelectionStart();
  }

  public int getSelectionEnd() {
    return mEditMsg.getSelectionEnd();
  }

  public boolean canUndo() {
    return mRunDo != null && mRunDo.canUndo();
  }

  public void undo() {
    if (mRunDo != null) {
      mRunDo.undo();
    }
  }

  public boolean canRedo() {
    return mRunDo != null && mRunDo.canRedo();
  }

  public void redo() {
    if (mRunDo != null) {
      mRunDo.redo();
    }
  }

  @Override
  public void onSelectionChanged(int selStart, int selEnd) {
    if (mEditMsg.hasFocus()) {
      mListener.onTextChanged(getMsg(), true, selStart, selEnd);
    }
  }

  public EditText getRef() {
    return mEditMsg;
  }


  /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*
   *  CALLBACK INTERFACE
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

  public interface NoteEditFragmentListener {
    void onTextChanged(String msg, boolean hasFocus, int selectionStart, int SelectionEnd);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (context instanceof NoteEditFragmentListener ) {
      mListener = (NoteEditFragmentListener)context;
    } else {
      throw new RuntimeException(context.toString()
          + " must implement TeamCreateFragmentListener");
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mListener = null;
  }
  private NoteEditFragmentListener mListener;


  private EditTextSelectable mEditMsg;
  private RunDo mRunDo;

  private int mIndex = -1;
  private CoordinatorLayout mView;
  private Note mNote;
  private String mMsg;
  private boolean mIsEditable;

  private static final String KEY_INDEX    = "noteEditFrag_index";
  private static final String KEY_MSG      = "noteEditFrag_msg";
  private static final String KEY_EDITABLE = "noteEditFrag_editable";
}