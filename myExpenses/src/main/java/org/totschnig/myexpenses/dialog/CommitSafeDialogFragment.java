//on some occasions, upon showing a DialogFragment we run into
//"java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState"
//we catch this here, and ignore silently, which hopefully should be save, since activity is being paused
//https://code.google.com/p/android/issues/detail?id=23096#c4

package org.totschnig.myexpenses.dialog;

import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.Toast;

import org.totschnig.myexpenses.ui.SnackbarAction;
import org.totschnig.myexpenses.util.UiUtils;

public abstract class CommitSafeDialogFragment extends DialogFragment {

  protected View dialogView;

  @Override
  public int show(FragmentTransaction transaction, String tag) {
      try {
          return super.show(transaction, tag);
      } catch (IllegalStateException ignored) {}
      return -1;
  }

  @Override
  public void show(FragmentManager manager, String tag) {
      try {
          super.show(manager, tag);
      } catch (IllegalStateException ignored) {}
  }

  @Override
  public void dismiss() {
    try {
      super.dismiss();
    } catch (IllegalStateException ignored) {}
  }

  protected void showSnackbar(int resId) {
    showSnackbar(getString(resId), Snackbar.LENGTH_LONG, null);
  }

  public void showSnackbar(CharSequence message, int duration, SnackbarAction snackbarAction) {
    if (dialogView != null) {
      Snackbar snackbar = Snackbar.make(dialogView, message, duration);
      UiUtils.configureSnackbarForDarkTheme(snackbar);
      if (snackbarAction != null) {
        snackbar.setAction(snackbarAction.resId, snackbarAction.listener);
      }
      snackbar.show();
    } else {
      Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();

    }
  }
}
