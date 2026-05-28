package org.runnerup.features;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import androidx.appcompat.app.AlertDialog;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.runnerup.sync.SyncManager;

/** Upload/sync account state for {@link DetailActivity}. */
final class DetailSyncController {

  final HashSet<String> pendingSynchronizers = new HashSet<>();
  final HashSet<String> alreadySynched = new HashSet<>();
  final Map<String, String> synchedExternalId = new HashMap<>();

  void openAccountUrl(DetailActivity activity, SyncManager syncManager, String name) {
    if (synchedExternalId.containsKey(name) && !TextUtils.isEmpty(synchedExternalId.get(name))) {
      String url =
          syncManager.getSynchronizerByName(name).getActivityUrl(synchedExternalId.get(name));
      if (url != null) {
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
      }
    }
  }

  View.OnLongClickListener createClearUploadListener(
      DetailActivity activity, SyncManager syncManager, long activityId, Runnable onCleared) {
    return arg0 -> {
      final String name = (String) arg0.getTag();
      new AlertDialog.Builder(activity)
          .setTitle("Clear upload for " + name)
          .setMessage(org.runnerup.common.R.string.Are_you_sure)
          .setPositiveButton(
              org.runnerup.common.R.string.Yes,
              (dialog, which) -> {
                dialog.dismiss();
                syncManager.clearUpload(name, activityId);
                onCleared.run();
              })
          .setNegativeButton(
              org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
          .show();
      return false;
    };
  }

  View.OnClickListener createUploadListener(
      SyncManager syncManager,
      long activityId,
      UploadingState uploading,
      Runnable onComplete) {
    return v -> {
      uploading.set(true);
      syncManager.startUploading(
          (synchronizerName, status) -> {
            uploading.set(false);
            onComplete.run();
          },
          pendingSynchronizers,
          activityId);
    };
  }

  OnCheckedChangeListener createSendCheckedListener(Runnable updateUploadVisibility) {
    return new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
        final String name = (String) arg0.getTag();
        if (alreadySynched.contains(name)) {
          arg0.setChecked(true);
        } else {
          if (arg1) {
            pendingSynchronizers.add(name);
          } else {
            pendingSynchronizers.remove(name);
          }
          updateUploadVisibility.run();
        }
      }
    };
  }

  interface UploadingState {
    void set(boolean uploading);
  }
}
