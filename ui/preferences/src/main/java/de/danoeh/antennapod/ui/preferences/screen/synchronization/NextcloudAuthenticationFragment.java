package de.danoeh.antennapod.ui.preferences.screen.synchronization;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.DialogFragment;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.model.SingleSignOnAccount;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.preferences.R;
import de.danoeh.antennapod.ui.preferences.databinding.NextcloudAuthDialogBinding;

/**
 * Guides the user through the authentication process.
 */
public class NextcloudAuthenticationFragment extends DialogFragment
        {
    public static final String TAG = "NextcloudAuthenticationFragment";
    private static final int COLOR_SECONDARY_TEXT = 0x88888888;
    private NextcloudAuthDialogBinding viewBinding;
    private boolean shouldDismiss = false;
    private boolean didRequestAccountChooser = false;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(R.string.gpodnetauth_login_butLabel);
        dialog.setNegativeButton(R.string.cancel_label, null);
        dialog.setCancelable(false);
        this.setCancelable(false);

        viewBinding = NextcloudAuthDialogBinding.inflate(getLayoutInflater());
        dialog.setView(viewBinding.getRoot());

        viewBinding.serverUrlTextInput.setVisibility(View.GONE);
        viewBinding.chooseHostButton.setVisibility(View.GONE);
        viewBinding.loginWithNextcloudAppButton.setOnClickListener(v -> openAccountChooser());
        return dialog.create();
    }

    private void openAccountChooser() {
        try {
            AccountImporter.pickNewAccount(this);
        } catch (NextcloudFilesAppNotInstalledException | AndroidGetAccountsPermissionNotGranted e) {
            showErrorDialog(e.getLocalizedMessage());
        }
    }

    private void startLoginFlow() {
        viewBinding.loginWithNextcloudAppButton.setVisibility(View.GONE);
        viewBinding.loginProgressContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this, account -> {
                if (account == null) {
                    showErrorDialog(getString(R.string.nextcloud_login_error_generic));
                    return;
                }
                onNextcloudAccountSelected(account);
            });
        } catch (AccountImportCancelledException e) {
            if (isResumed()) {
                dismiss();
            } else {
                shouldDismiss = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private void onNextcloudAccountSelected(@NonNull SingleSignOnAccount account) {
        startLoginFlow();
        SynchronizationSettings.setSelectedSyncProvider(
                SynchronizationProvider.NEXTCLOUD_GPODDER.getIdentifier());
        SynchronizationCredentials.clear();
        SynchronizationQueue.getInstance().clear();
        SynchronizationCredentials.setHosturl(account.url);
        SynchronizationCredentials.setNextcloudAccountName(account.name);
        SynchronizationQueue.getInstance().fullSync();
        if (isResumed()) {
            dismiss();
        } else {
            shouldDismiss = true;
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!didRequestAccountChooser) {
            didRequestAccountChooser = true;
            startLoginFlow();
            openAccountChooser();
        }
        if (shouldDismiss) {
            dismiss();
        }
    }

    private void showErrorDialog(@Nullable String errorMessage) {
        final MaterialAlertDialogBuilder errorDialog = new MaterialAlertDialogBuilder(getContext());
        errorDialog.setTitle(R.string.error_label);
        String genericMessage = getString(R.string.nextcloud_login_error_generic);
        String message = (errorMessage == null || errorMessage.trim().isEmpty())
                ? genericMessage
                : genericMessage + "\n\n" + errorMessage;
        SpannableString combinedMessage = new SpannableString(message);
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            combinedMessage.setSpan(new ForegroundColorSpan(COLOR_SECONDARY_TEXT),
                    genericMessage.length(), combinedMessage.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        errorDialog.setMessage(combinedMessage);
        errorDialog.setPositiveButton(android.R.string.ok, null);
        errorDialog.show();
    }
}
