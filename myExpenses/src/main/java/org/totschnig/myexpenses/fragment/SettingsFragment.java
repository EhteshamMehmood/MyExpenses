package org.totschnig.myexpenses.fragment;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.CommonCommands;
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity;
import org.totschnig.myexpenses.activity.FolderBrowser;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.activity.MyPreferenceActivity;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.preference.CalendarListPreferenceDialogFragmentCompat;
import org.totschnig.myexpenses.preference.FontSizeDialogFragmentCompat;
import org.totschnig.myexpenses.preference.FontSizeDialogPreference;
import org.totschnig.myexpenses.preference.PasswordPreference;
import org.totschnig.myexpenses.preference.PasswordPreferenceDialogFragmentCompat;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.preference.SecurityQuestionDialogFragmentCompat;
import org.totschnig.myexpenses.preference.TimePreference;
import org.totschnig.myexpenses.preference.TimePreferenceDialogFragmentCompat;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.sync.ServiceLoader;
import org.totschnig.myexpenses.sync.SyncBackendProviderFactory;
import org.totschnig.myexpenses.ui.PreferenceDividerItemDecoration;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.AppDirHelper;
import org.totschnig.myexpenses.util.DistribHelper;
import org.totschnig.myexpenses.util.FileUtils;
import org.totschnig.myexpenses.util.LicenceHandler;
import org.totschnig.myexpenses.util.ShareUtils;
import org.totschnig.myexpenses.util.ShortcutHelper;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.widget.AbstractWidget;

import java.net.URI;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

public class SettingsFragment extends PreferenceFragmentCompat implements
    Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener {

  private long pickFolderRequestStart;
  private static final int RESTORE_REQUEST = 1;
  private static final int PICK_FOLDER_REQUEST = 2;
  private static final int CONTRIB_PURCHASE_REQUEST = 3;
  private static final int PICK_FOLDER_REQUEST_LEGACY = 4;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (MyApplication.isInstrumentationTest()) {
      getPreferenceManager().setSharedPreferencesName(MyApplication.getTestId());
    }
  }

  private Preference.OnPreferenceClickListener homeScreenShortcutPrefClickHandler =
      preference -> {
        Bundle extras = new Bundle();
        extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET, true);
        extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, true);
        int nameId = 0, iconId = 0, operationType = 0;
        if (preference.getKey().equals(PrefKey.SHORTCUT_CREATE_TRANSACTION.getKey())) {
          nameId = R.string.transaction;
          iconId = R.drawable.shortcut_create_transaction_icon;
          operationType = MyExpenses.TYPE_TRANSACTION;
        }
        if (preference.getKey().equals(PrefKey.SHORTCUT_CREATE_TRANSFER.getKey())) {
          nameId = R.string.transfer;
          iconId = R.drawable.shortcut_create_transfer_icon;
          operationType = MyExpenses.TYPE_TRANSFER;
        }
        if (preference.getKey().equals(PrefKey.SHORTCUT_CREATE_SPLIT.getKey())) {
          nameId = R.string.split_transaction;
          iconId = R.drawable.shortcut_create_split_icon;
          operationType = MyExpenses.TYPE_SPLIT;
        }
        if (nameId != 0) {
          addShortcut(nameId, iconId, operationType);
          return true;
        }
        return false;
      };

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.preferences, rootKey);
    Preference pref;

    if (rootKey == null) {//ROOT screen
      configureContribPrefs();
      findPreference(PrefKey.SEND_FEEDBACK.getKey())
          .setOnPreferenceClickListener(this);
      findPreference(PrefKey.MORE_INFO_DIALOG.getKey())
          .setOnPreferenceClickListener(this);

      pref = findPreference(PrefKey.RESTORE.getKey());
      pref.setTitle(getString(R.string.pref_restore_title) + " (ZIP)");
      pref.setOnPreferenceClickListener(this);

      pref = findPreference(PrefKey.RESTORE_LEGACY.getKey());
      pref.setTitle(getString(R.string.pref_restore_title) + " (" + getString(R.string.pref_restore_alternative) + ")");
      pref.setOnPreferenceClickListener(this);

      findPreference(PrefKey.RATE.getKey()).setOnPreferenceClickListener(this);

      pref = findPreference(PrefKey.CUSTOM_DECIMAL_FORMAT.getKey());
      pref.setOnPreferenceChangeListener(this);
      if (PrefKey.CUSTOM_DECIMAL_FORMAT.getString("").equals("")) {
        setDefaultNumberFormat(((EditTextPreference) pref));
      }

      findPreference(PrefKey.APP_DIR.getKey())
          .setOnPreferenceClickListener(this);
      setAppDirSummary();

      final PreferenceCategory categoryManage =
          ((PreferenceCategory) findPreference(PrefKey.CATEGORY_MANAGE.getKey()));
      final Preference prefStaleImages = findPreference(PrefKey.MANAGE_STALE_IMAGES.getKey());
      categoryManage.removePreference(prefStaleImages);

      pref = findPreference(PrefKey.IMPORT_QIF.getKey());
      pref.setSummary(getString(R.string.pref_import_summary, "QIF"));
      pref.setTitle(getString(R.string.pref_import_title, "QIF"));
      pref = findPreference(PrefKey.IMPORT_CSV.getKey());
      pref.setSummary(getString(R.string.pref_import_summary, "CSV"));
      pref.setTitle(getString(R.string.pref_import_title, "CSV"));
      pref.setOnPreferenceClickListener(this);

      findPreference(getString(R.string.pref_manage_sync_backends_key)).setSummary(
          getString(R.string.pref_manage_sync_backends_summary,
              Stream.of(ServiceLoader.load(getContext()))
                  .map(SyncBackendProviderFactory::getLabel)
                  .collect(Collectors.joining(", "))) +
              " " + ContribFeature.SYNCHRONIZATION.buildRequiresString(getActivity()));

      new AsyncTask<Void, Void, Boolean>() {
        @Override
        protected Boolean doInBackground(Void... params) {
          if (getActivity() == null) return false;
          Cursor c = getActivity().getContentResolver().query(
              TransactionProvider.STALE_IMAGES_URI,
              new String[]{"count(*)"},
              null, null, null);
          if (c == null)
            return false;
          boolean hasImages = false;
          if (c.moveToFirst() && c.getInt(0) > 0)
            hasImages = true;
          c.close();
          return hasImages;
        }

        @Override
        protected void onPostExecute(Boolean result) {
          if (getActivity() != null && !getActivity().isFinishing() && result)
            categoryManage.addPreference(prefStaleImages);
        }
      }.execute();

      findPreference(PrefKey.UI_HOME_SCREEN_SHORTCUTS.getKey()).setOnPreferenceClickListener(this);
    }
    //SHORTCUTS screen
    else if (rootKey.equals(getString(R.string.pref_ui_home_screen_shortcuts_key))) {
      findPreference(PrefKey.SHORTCUT_CREATE_TRANSACTION.getKey())
          .setOnPreferenceClickListener(homeScreenShortcutPrefClickHandler);
      findPreference(PrefKey.SHORTCUT_CREATE_TRANSFER.getKey())
          .setOnPreferenceClickListener(homeScreenShortcutPrefClickHandler);
      pref = findPreference(PrefKey.SHORTCUT_CREATE_SPLIT.getKey());
      pref.setOnPreferenceClickListener(homeScreenShortcutPrefClickHandler);
      pref.setEnabled(MyApplication.getInstance().getLicenceHandler().isContribEnabled());
      pref.setSummary(
          getString(R.string.pref_shortcut_summary) + " " +
              ContribFeature.SPLIT_TRANSACTION.buildRequiresString(getActivity()));

    }
    //Password screen
    else if (rootKey.equals(getString(R.string.pref_screen_protection))) {
      setProtectionDependentsState();
      findPreference(PrefKey.SECURITY_QUESTION.getKey()).setSummary(
          getString(R.string.pref_security_question_summary) + " " +
              ContribFeature.SECURITY_QUESTION.buildRequiresString(getActivity()));
    }
    //SHARE screen
    else if (rootKey.equals(getString(R.string.pref_perform_share_key))) {
      pref = findPreference(PrefKey.SHARE_TARGET.getKey());
      //noinspection AuthLeak
      pref.setSummary(getString(R.string.pref_share_target_summary) + ":\n" +
          "ftp: \"ftp://login:password@my.example.org:port/my/directory/\"\n" +
          "mailto: \"mailto:john@my.example.com\"");
      pref.setOnPreferenceChangeListener(this);
    }
    //BACKUP screen
    else if (rootKey.equals(getString(R.string.pref_auto_backup_key))) {
      pref = findPreference(getString(R.string.pref_auto_backup_info_key));
      String summary = getString(R.string.pref_auto_backup_summary) + " " +
          ContribFeature.AUTO_BACKUP.buildRequiresString(getActivity());
      pref.setSummary(summary);
    }
    //GROUP start screen
    else if (rootKey.equals(getString(R.string.pref_grouping_start))) {
      ListPreference startPref =
          (ListPreference) findPreference(getString(R.string.pref_group_week_starts_key));
      final Locale locale = Locale.getDefault();
      DateFormatSymbols dfs = new DateFormatSymbols(locale);
      String[] entries = new String[7];
      System.arraycopy(dfs.getWeekdays(), 1, entries, 0, 7);
      startPref.setEntries(entries);
      startPref.setEntryValues(new String[]{
          String.valueOf(Calendar.SUNDAY),
          String.valueOf(Calendar.MONDAY),
          String.valueOf(Calendar.TUESDAY),
          String.valueOf(Calendar.WEDNESDAY),
          String.valueOf(Calendar.THURSDAY),
          String.valueOf(Calendar.FRIDAY),
          String.valueOf(Calendar.SATURDAY),
      });
      if (!PrefKey.GROUP_WEEK_STARTS.isSet()) {
        startPref.setValue(String.valueOf(Utils.getFirstDayOfWeek(locale)));
      }

      startPref =
          (ListPreference) findPreference(getString(R.string.pref_group_month_starts_key));
      String[] daysEntries = new String[31], daysValues = new String[31];
      for (int i = 1; i <= 31; i++) {
        daysEntries[i - 1] = Utils.toLocalizedString(i);
        daysValues[i - 1] = String.valueOf(i);
      }
      startPref.setEntries(daysEntries);
      startPref.setEntryValues(daysValues);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    final MyPreferenceActivity activity = (MyPreferenceActivity) getActivity();
    final ActionBar actionBar = activity.getSupportActionBar();
    PreferenceScreen screen = getPreferenceScreen();
    CharSequence title = screen.getKey().equals(getString(R.string.pref_root_screen)) ?
        Utils.concatResStrings(activity, " ", R.string.app_name, R.string.menu_settings) :
        screen.getTitle();
    actionBar.setTitle(title);
    boolean hasMasterSwitch = handleScreenWithMasterSwitch(PrefKey.PERFORM_SHARE);
    hasMasterSwitch = handleScreenWithMasterSwitch(PrefKey.AUTO_BACKUP) || hasMasterSwitch;
    if (!hasMasterSwitch) {
      actionBar.setCustomView(null);
    }
    if (screen.getKey().equals(getString(R.string.pref_root_screen))) {
      setOnOffSummary(getString(R.string.pref_screen_protection),
          PrefKey.PERFORM_PROTECTION.getBoolean(false));
      Preference preference = findPreference(PrefKey.PLANNER_CALENDAR_ID.getKey());
      if (preference != null) {
        preference.setSummary(
            ((MyPreferenceActivity) getActivity()).calendarPermissionPermanentlyDeclined() ?
                R.string.calendar_permission_required : R.string.pref_planning_calendar_summary);
      }
    }
    activity.setFragment(this);
  }

  /**
   * Configures the current screen with a Master Switch, if it has the given key
   * if we are on the root screen, the preference summary for the given key is updated with the
   * current value (On/Off)
   *
   * @param prefKey
   * @return true if we have handle the given key as a subscreen
   */
  private boolean handleScreenWithMasterSwitch(final PrefKey prefKey) {
    PreferenceScreen screen = getPreferenceScreen();
    final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    final boolean status = prefKey.getBoolean(false);
    if (screen.getKey().equals(prefKey.getKey())) {
      //noinspection InflateParams
      SwitchCompat actionBarSwitch = (SwitchCompat) getActivity().getLayoutInflater().inflate(
          R.layout.pref_master_switch, null);
      actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
          ActionBar.DISPLAY_SHOW_CUSTOM);
      actionBar.setCustomView(actionBarSwitch);
      actionBarSwitch.setChecked(status);
      actionBarSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          if (prefKey.equals(PrefKey.AUTO_BACKUP)) {
            if (isChecked && !ContribFeature.AUTO_BACKUP.hasAccess()) {
              CommonCommands.showContribDialog(getActivity(), ContribFeature.AUTO_BACKUP, null);
              if (ContribFeature.AUTO_BACKUP.usagesLeft() <= 0) {
                buttonView.setChecked(false);
                return;
              }
            }
          }
          prefKey.putBoolean(isChecked);
          updateDependents(isChecked);
        }
      });
      updateDependents(status);
      return true;
    } else if (screen.getKey().equals(getString(R.string.pref_root_screen))) {
      setOnOffSummary(prefKey);
    }
    return false;
  }

  private void setOnOffSummary(PrefKey prefKey) {
    setOnOffSummary(prefKey.getKey(), prefKey.getBoolean(false));
  }

  private void setOnOffSummary(String key, boolean status) {
    findPreference(key).setSummary(status ?
        getString(R.string.switch_on_text) : getString(R.string.switch_off_text));
  }

  private void updateDependents(boolean enabled) {
    int count = getPreferenceScreen().getPreferenceCount();
    for (int i = 0; i < count; ++i) {
      Preference pref = getPreferenceScreen().getPreference(i);
      pref.setEnabled(enabled);
    }
  }

  public void showPreference(String prefKey) {
    //noinspection RestrictedApi
    findPreference(prefKey).performClick();
  }

  public void configureContribPrefs() {
    Preference requestLicencePref = findPreference(PrefKey.REQUEST_LICENCE.getKey()),
        contribPurchasePref = findPreference(PrefKey.CONTRIB_PURCHASE.getKey());
    String contribPurchaseTitle, contribPurchaseSummary;
    LicenceHandler licenceHandler = MyApplication.getInstance().getLicenceHandler();
    if (licenceHandler.isNoLongerUpgradeable()) {
      PreferenceCategory cat = ((PreferenceCategory) findPreference(PrefKey.CATEGORY_CONTRIB.getKey()));
      if (requestLicencePref != null) {
        cat.removePreference(requestLicencePref);
      }
      contribPurchaseTitle = getString(R.string.licence_status) + ": " + getString(
          licenceHandler.isExtendedEnabled() ? R.string.extended_key : R.string.contrib_key);
      contribPurchaseSummary = getString(R.string.thank_you);
    } else {
      if (requestLicencePref != null) {
        requestLicencePref.setOnPreferenceClickListener(this);
        requestLicencePref.setSummary(getString(R.string.pref_request_licence_summary, Settings.Secure.getString(getActivity().getContentResolver(), Settings.Secure.ANDROID_ID)));
      }
      boolean contribEnabled = licenceHandler.isContribEnabled();
      if (contribEnabled) {
        contribPurchaseTitle = getString(R.string.licence_status) + ": " + getString(R.string.contrib_key);
        contribPurchaseSummary = getString(R.string.pref_contrib_purchase_title_upgrade);
      } else {
        int baseTitle = R.string.pref_contrib_purchase_title;
        contribPurchaseTitle = getString(baseTitle);
        if (!DistribHelper.isGithub()) {
          contribPurchaseTitle += " (" + getString(R.string.pref_contrib_purchase_title_in_app) + ")";
        }
        contribPurchaseSummary = getString(R.string.pref_contrib_purchase_summary);
      }
      contribPurchasePref.setOnPreferenceClickListener(this);
    }
    contribPurchasePref.setSummary(contribPurchaseSummary);
    contribPurchasePref.setTitle(contribPurchaseTitle);
  }

  public void setProtectionDependentsState() {
    boolean isProtected = PrefKey.PERFORM_PROTECTION.getBoolean(false);
    findPreference(PrefKey.SECURITY_QUESTION.getKey()).setEnabled(MyApplication.getInstance().getLicenceHandler().isContribEnabled() && isProtected);
    findPreference(PrefKey.PROTECTION_DELAY_SECONDS.getKey()).setEnabled(isProtected);
    findPreference(PrefKey.PROTECTION_ENABLE_ACCOUNT_WIDGET.getKey()).setEnabled(isProtected);
    findPreference(PrefKey.PROTECTION_ENABLE_TEMPLATE_WIDGET.getKey()).setEnabled(isProtected);
    findPreference(PrefKey.PROTECTION_ENABLE_DATA_ENTRY_FROM_WIDGET.getKey()).setEnabled(isProtected);
  }

  @Override
  public boolean onPreferenceChange(Preference pref, Object value) {
    String key = pref.getKey();
    if (key.equals(PrefKey.SHARE_TARGET.getKey())) {
      String target = (String) value;
      URI uri;
      if (!target.equals("")) {
        uri = ShareUtils.parseUri(target);
        if (uri == null) {
          Toast.makeText(getActivity(), getString(R.string.ftp_uri_malformed, target), Toast.LENGTH_LONG).show();
          return false;
        }
        String scheme = uri.getScheme();
        if (!(scheme.equals("ftp") || scheme.equals("mailto"))) {
          Toast.makeText(getActivity(), getString(R.string.share_scheme_not_supported, scheme), Toast.LENGTH_LONG).show();
          return false;
        }
        Intent intent;
        if (scheme.equals("ftp")) {
          intent = new Intent(Intent.ACTION_SENDTO);
          intent.setData(android.net.Uri.parse(target));
          if (!Utils.isIntentAvailable(getActivity(), intent)) {
            getActivity().showDialog(R.id.FTP_DIALOG);
          }
        }
      }
    } else if (key.equals(PrefKey.CUSTOM_DECIMAL_FORMAT.getKey())) {
      if (TextUtils.isEmpty((String) value)) {
        Utils.setNumberFormat(NumberFormat.getCurrencyInstance());
        return true;
      }
      try {
        DecimalFormat nf = new DecimalFormat();
        nf.applyLocalizedPattern(((String) value));
        Utils.setNumberFormat(nf);
      } catch (IllegalArgumentException e) {
        Toast.makeText(getActivity(), R.string.number_format_illegal, Toast.LENGTH_LONG).show();
        return false;
      }
    }
    return true;
  }

  private void setDefaultNumberFormat(EditTextPreference pref) {
    String pattern = ((DecimalFormat) NumberFormat.getCurrencyInstance()).toLocalizedPattern();
    //Log.d(MyApplication.TAG,pattern);
    pref.setText(pattern);
  }

  @Override
  public boolean onPreferenceClick(Preference preference) {
    if (preference.getKey().equals(PrefKey.CONTRIB_PURCHASE.getKey())) {
      if (MyApplication.getInstance().getLicenceHandler().isExtendedEnabled()) {
        //showDialog(R.id.DONATE_DIALOG);//currently nothing to do
      } else {
        Intent i = new Intent(getActivity(), ContribInfoDialogActivity.class);
        if (DistribHelper.isGithub()) {
          startActivityForResult(i, CONTRIB_PURCHASE_REQUEST);
        } else {
          startActivity(i);
        }
      }
      return true;
    }
    if (preference.getKey().equals(PrefKey.REQUEST_LICENCE.getKey())) {
      CommonCommands.dispatchCommand(getActivity(), R.id.REQUEST_LICENCE_COMMAND, null);
      return true;
    }
    if (preference.getKey().equals(PrefKey.SEND_FEEDBACK.getKey())) {
      CommonCommands.dispatchCommand(getActivity(), R.id.FEEDBACK_COMMAND, null);
      return true;
    }
    if (preference.getKey().equals(PrefKey.RATE.getKey())) {
      PrefKey.NEXT_REMINDER_RATE.putLong(-1);
      CommonCommands.dispatchCommand(getActivity(), R.id.RATE_COMMAND, null);
      return true;
    }
    if (preference.getKey().equals(PrefKey.MORE_INFO_DIALOG.getKey())) {
      getActivity().showDialog(R.id.MORE_INFO_DIALOG);
      return true;
    }
    if (preference.getKey().equals(PrefKey.RESTORE.getKey()) ||
        preference.getKey().equals(PrefKey.RESTORE_LEGACY.getKey())) {
      startActivityForResult(preference.getIntent(), RESTORE_REQUEST);
      return true;
    }
    if (preference.getKey().equals(PrefKey.APP_DIR.getKey())) {
      DocumentFile appDir = AppDirHelper.getAppDir();
      if (appDir == null) {
        preference.setSummary(R.string.external_storage_unavailable);
        preference.setEnabled(false);
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          //noinspection InlinedApi
          Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
          try {
            pickFolderRequestStart = System.currentTimeMillis();
            startActivityForResult(intent, PICK_FOLDER_REQUEST);
            return true;
          } catch (ActivityNotFoundException e) {
            AcraHelper.report(e);
            //fallback to FolderBrowser
          }
        }
        startLegacyFolderRequest(appDir);
      }
      return true;
    }
    if (preference.getKey().equals(PrefKey.IMPORT_CSV.getKey())) {
      if (ContribFeature.CSV_IMPORT.hasAccess()) {
        ((MyPreferenceActivity) getActivity()).contribFeatureCalled(ContribFeature.CSV_IMPORT, null);
      } else {
        CommonCommands.showContribDialog(getActivity(), ContribFeature.CSV_IMPORT, null);
      }
      return true;
    }
    return false;
  }

  protected void startLegacyFolderRequest(DocumentFile appDir) {
    Intent intent;
    intent = new Intent(getActivity(), FolderBrowser.class);
    intent.putExtra(FolderBrowser.PATH, appDir.getUri().getPath());
    startActivityForResult(intent, PICK_FOLDER_REQUEST_LEGACY);
  }

  private void setAppDirSummary() {
    Preference pref = findPreference(PrefKey.APP_DIR.getKey());
    if (AppDirHelper.isExternalStorageAvailable()) {
      DocumentFile appDir = AppDirHelper.getAppDir();
      if (appDir != null) {
        if (AppDirHelper.dirExistsAndIsWritable(appDir)) {
          pref.setSummary(FileUtils.getPath(getActivity(), appDir.getUri()));
        } else {
          pref.setSummary(getString(R.string.app_dir_not_accessible,
              FileUtils.getPath(MyApplication.getInstance(), appDir.getUri())));
        }
      }
    } else {
      pref.setSummary(R.string.external_storage_unavailable);
      pref.setEnabled(false);
    }
  }

  // credits Financisto
  // src/ru/orangesoftware/financisto/activity/PreferencesActivity.java
  private void addShortcut(int nameId, int iconId, int operationType) {
    Intent shortcutIntent = ShortcutHelper.createIntentForNewTransaction(getContext(), operationType);

    Intent intent = new Intent();
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(nameId));
    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, Utils.getTintedBitmapForTheme(getActivity(), iconId, R.style.ThemeDark));
    intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");

    if (Utils.isIntentReceiverAvailable(getActivity(), intent)) {
      getActivity().sendBroadcast(intent);
      Toast.makeText(getActivity(), getString(R.string.pref_shortcut_added), Toast.LENGTH_LONG).show();
    } else {
      Toast.makeText(getActivity(), getString(R.string.pref_shortcut_not_added), Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    DialogFragment fragment = null;
    String key = preference.getKey();
    if (key.equals(PrefKey.PLANNER_CALENDAR_ID.getKey())) {
      if (ContextCompat.checkSelfPermission(getContext(),
          Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
        fragment = CalendarListPreferenceDialogFragmentCompat.newInstance(key);
      } else {
        ((ProtectedFragmentActivity) getActivity()).requestCalendarPermission();
        return;
      }
    } else if (preference instanceof FontSizeDialogPreference) {
      fragment = FontSizeDialogFragmentCompat.newInstance(key);
    } else if (preference instanceof TimePreference) {
      fragment = TimePreferenceDialogFragmentCompat.newInstance(key);
    } else if (preference instanceof PasswordPreference) {
      fragment = PasswordPreferenceDialogFragmentCompat.newInstance(key);
    } else if (key.equals(PrefKey.SECURITY_QUESTION.getKey())) {
      fragment = SecurityQuestionDialogFragmentCompat.newInstance(key);
    } else if (key.equals(PrefKey.AUTO_BACUP_CLOUD.getKey())) {
      if (((ListPreference) preference).getEntries().length == 1) {
        Toast.makeText(getContext(), R.string.auto_backup_cloud_create_backend, Toast.LENGTH_LONG).show();
        return;
      }
    }
    if (fragment != null) {
      fragment.setTargetFragment(this, 0);
      fragment.show(getFragmentManager(),
          "android.support.v7.preference.PreferenceFragment.DIALOG");
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }

  @Override
  public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    RecyclerView result = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
    result.addItemDecoration(
        new PreferenceDividerItemDecoration(getActivity())
    );
    return result;
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Override
  public void onActivityResult(int requestCode, int resultCode,
                               Intent intent) {
    if (requestCode == RESTORE_REQUEST && resultCode == Activity.RESULT_FIRST_USER) {
      getActivity().setResult(resultCode);
      getActivity().finish();
    } else if (requestCode == PICK_FOLDER_REQUEST) {
      if (resultCode == Activity.RESULT_OK) {
        Uri dir = intent.getData();
        getActivity().getContentResolver().takePersistableUriPermission(dir,
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        PrefKey.APP_DIR.putString(intent.getData().toString());
        setAppDirSummary();
      } else {
        //we try to determine if we get here due to abnormal failure (observed on Xiaomi) of request, or if user canceled
        long pickFolderRequestDuration = System.currentTimeMillis() - pickFolderRequestStart;
        if (pickFolderRequestDuration < 250) {
          //String error = String.format(Locale.ROOT, "PICK_FOLDER_REQUEST returned after %d millis with request code %d",
          //    pickFolderRequestDuration, requestCode);
          //AcraHelper.report(new Exception(error));
          startLegacyFolderRequest(AppDirHelper.getAppDir());
        }
      }
    } else if (requestCode == PICK_FOLDER_REQUEST_LEGACY && resultCode == Activity.RESULT_OK) {
      setAppDirSummary();
    }
  }
}