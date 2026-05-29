package org.runnerup.features

import android.content.ContentValues
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import java.io.File
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.HRZones
import org.runnerup.core.workout.Feedback
import org.runnerup.core.workout.Workout
import org.runnerup.core.workout.WorkoutBuilder
import org.runnerup.core.workout.feedback.RUTextToSpeech
import org.runnerup.data.DBHelper
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener
import org.runnerup.ui.common.widget.TitleSpinner

class AudioCueSettingsFragment : PreferenceFragmentCompat() {

  private var started = false
  private var settingsName: String? = null
  private var adapter: AudioSchemeListAdapter? = null
  private lateinit var mDB: SQLiteDatabase
  private lateinit var newSettings: MenuItem
  private lateinit var defaultLabel: String

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mDB = DBHelper.getWritableDatabase(requireContext())
    defaultLabel = getString(org.runnerup.common.R.string.Default)
    setHasOptionsMenu(true)
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    settingsName = requireArguments().getString("name")

    if (settingsName != null) {
      val prefMgr = preferenceManager
      prefMgr.sharedPreferencesName = settingsName + AudioCueSettingsActivity.SUFFIX
      prefMgr.sharedPreferencesMode = MODE_PRIVATE
    }

    setPreferencesFromResource(R.xml.audio_cue_settings, rootKey)

    findPreference<Preference>("test_cueinfo")?.setOnPreferenceClickListener(onTestCueinfoClick)

    val hrZones = HRZones(requireContext())
    val hasHR = SettingsSensorsFragment.hasHR(requireContext())
    val hasHRZones = hrZones.isConfigured

    if (!hasHR || !hasHRZones) {
      removePrefs(
          intArrayOf(
              R.string.cueinfo_total_hrz,
              R.string.cueinfo_step_hrz,
              R.string.cueinfo_lap_hrz,
              R.string.cueinfo_current_hrz,
          ))
    }

    if (!hasHR) {
      removePrefs(
          intArrayOf(
              R.string.cueinfo_total_hr,
              R.string.cueinfo_step_hr,
              R.string.cueinfo_lap_hr,
              R.string.cueinfo_current_hr,
          ))
    }

    findPreference<Preference>("tts_settings")?.setOnPreferenceClickListener {
      startActivity(Intent().setAction("com.android.settings.TTS_SETTINGS"))
      false
    }
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    val layout = inflater.inflate(R.layout.settings_wrapper, container, false)
    val settingsContainerView = layout.findViewById<ViewGroup>(R.id.settings_container_view)

    val settingsView = super.onCreateView(inflater, settingsContainerView, savedInstanceState)
    settingsContainerView.addView(settingsView)

    val createNewItem = true
    adapter = AudioSchemeListAdapter(mDB, inflater, createNewItem)
    adapter!!.reload()

    return layout
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundPrimary))

    val listView = view.findViewById<View>(android.R.id.list)
    if (listView is ListView) {
      listView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundPrimary))
      listView.cacheColorHint = Color.TRANSPARENT
      listView.divider =
          ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
      listView.dividerHeight = 16
    }

    val spinner = view.findViewById<TitleSpinner>(R.id.settings_spinner)
    spinner.visibility = View.VISIBLE
    spinner.setAdapter(adapter)

    val spinnerLayout = spinner.findViewById<View>(R.id.title_spinner_layout)
    if (spinnerLayout != null) {
      spinnerLayout.setBackgroundColor(
          ContextCompat.getColor(requireContext(), R.color.backgroundPrimary))
    }

    if (settingsName == null) {
      spinner.setValue(0)
    } else {
      val idx = adapter!!.find(settingsName)
      spinner.setValue(idx)
    }
    spinner.setOnSetValueListener(onSetValueListener)
  }

  private fun removePrefs(remove: IntArray) {
    val res = resources
    val group = findPreference<PreferenceGroup>("cueinfo") ?: return
    for (id in remove) {
      val key = res.getString(id)
      val pref = findPreference<Preference>(key) ?: continue
      group.removePreference(pref)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    newSettings = menu.add("New settings")
    val deleteMenuItem = menu.add("Delete settings")
    if (settingsName == null) {
      deleteMenuItem.isEnabled = false
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item == newSettings) {
      createNewAudioSchemeDialog()
      return true
    }
    AlertDialog.Builder(requireContext())
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          deleteAudioScheme()
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
    return true
  }

  private fun createNewAudioScheme(scheme: String) {
    val tmp = ContentValues()
    tmp.put(Constants.DB.AUDIO_SCHEMES.NAME, scheme)
    tmp.put(Constants.DB.AUDIO_SCHEMES.SORT_ORDER, 0)
    mDB.insert(Constants.DB.AUDIO_SCHEMES.TABLE, null, tmp)
  }

  private fun deleteAudioScheme() {
    deleteAudioSchemeImpl(settingsName)
    switchTo(null)
  }

  private fun deleteAudioSchemeImpl(name: String?) {
    val prefsFile =
        File(
            requireContext().filesDir.parent +
                File.separator +
                PREFS_DIR +
                "/" +
                name +
                AudioCueSettingsActivity.SUFFIX +
                ".xml")
    prefsFile.delete()

    val args = arrayOf(name)
    mDB.delete(
        Constants.DB.AUDIO_SCHEMES.TABLE,
        Constants.DB.AUDIO_SCHEMES.NAME + "= ?",
        args)
  }

  private fun updateSortOrder(name: String) {
    mDB.execSQL(
        "UPDATE " +
            Constants.DB.AUDIO_SCHEMES.TABLE +
            " set " +
            Constants.DB.AUDIO_SCHEMES.SORT_ORDER +
            " = (SELECT MAX(" +
            Constants.DB.AUDIO_SCHEMES.SORT_ORDER +
            ") + 1 FROM " +
            Constants.DB.AUDIO_SCHEMES.TABLE +
            ") " +
            " WHERE " +
            Constants.DB.AUDIO_SCHEMES.NAME +
            " = '" +
            name +
            "'")
  }

  private val onSetValueListener =
      object : OnSetValueListener {
        override fun preSetValue(newValue: String): String = newValue

        override fun preSetValue(newValueId: Int): Int {
          val newValue = adapter!!.getItem(newValueId) as String
          val prefMgr = preferenceManager
          when {
            newValue.contentEquals(defaultLabel) -> {
              prefMgr.sharedPreferences!!.edit().apply()
              switchTo(null)
            }
            newValue.contentEquals(getString(org.runnerup.common.R.string.New_audio_scheme)) -> {
              createNewAudioSchemeDialog()
            }
            else -> {
              prefMgr.sharedPreferences!!.edit().apply()
              updateSortOrder(newValue)
              switchTo(newValue)
            }
          }
          throw IllegalArgumentException()
        }
      }

  private fun switchTo(name: String?) {
    if (!started) {
      started = true
      return
    }

    if (name == null && settingsName == null) {
      return
    }

    if (name != null && settingsName != null && name.contentEquals(settingsName)) {
      return
    }

    val bundle = Bundle()
    if (name != null) {
      bundle.putString("name", name)
    }

    requireActivity()
        .supportFragmentManager
        .beginTransaction()
        .setReorderingAllowed(true)
        .replace(R.id.settings_fragment_container, AudioCueSettingsFragment::class.java, bundle)
        .commit()
  }

  private fun createNewAudioSchemeDialog() {
    val editText = EditText(requireContext())
    editText.minimumHeight = 48
    editText.minimumWidth = 48

    AlertDialog.Builder(requireContext())
        .setTitle(org.runnerup.common.R.string.Create_new_audio_cue_scheme)
        .setView(editText)
        .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
          val scheme = editText.text.toString()
          if (scheme.isNotEmpty()) {
            createNewAudioScheme(scheme)
            updateSortOrder(scheme)
            switchTo(scheme)
          }
        }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { _, _ -> }
        .show()
  }

  private fun createNewNoTtsAvailableDialog() {
    AlertDialog.Builder(requireContext())
        .setTitle(org.runnerup.common.R.string.tts_not_available_title)
        .setMessage(org.runnerup.common.R.string.tts_not_available)
        .setPositiveButton(org.runnerup.common.R.string.OK, null)
        .show()
  }

  private val onTestCueinfoClick =
      object : Preference.OnPreferenceClickListener {
        private var tts: TextToSpeech? = null
        private val feedback = ArrayList<Feedback>()

        private val mTTSOnInitListener =
            TextToSpeech.OnInitListener { status ->
              if (status != TextToSpeech.SUCCESS) {
                createNewNoTtsAvailableDialog()
                return@OnInitListener
              }

              val prefs: SharedPreferences =
                  if (settingsName == null || settingsName!!.contentEquals(defaultLabel)) {
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                  } else {
                    requireContext().getSharedPreferences(
                        settingsName + AudioCueSettingsActivity.SUFFIX,
                        MODE_PRIVATE)
                  }
              val mute =
                  prefs.getBoolean(resources.getString(R.string.pref_mute_bool), false)

              val w = Workout.fakeWorkoutForTestingAudioCue()
              val rutts = RUTextToSpeech(tts!!, mute, requireContext())

              val bindValues = HashMap<String, Any>()
              bindValues[Workout.KEY_TTS] = rutts
              bindValues[Workout.KEY_FORMATTER] = Formatter(requireContext())
              bindValues[Workout.KEY_HRZONES] = HRZones(requireContext())
              w.onBind(w, bindValues)
              for (f in feedback) {
                f.onInit(w)
                f.onBind(w, bindValues)
                f.emit(w, requireContext())
                rutts.emit()
              }
            }

        override fun onPreferenceClick(arg0: Preference): Boolean {
          val ctx = requireContext()
          val res = resources

          feedback.clear()
          val prefs: SharedPreferences =
              if (settingsName == null || settingsName!!.contentEquals(defaultLabel)) {
                PreferenceManager.getDefaultSharedPreferences(ctx)
              } else {
                ctx.getSharedPreferences(settingsName + AudioCueSettingsActivity.SUFFIX, MODE_PRIVATE)
              }

          WorkoutBuilder.addFeedbackFromPreferences(prefs, res, feedback)

          tts = TextToSpeech(ctx, mTTSOnInitListener)
          return false
        }
      }

  companion object {
    private const val PREFS_DIR = "shared_prefs"
  }
}
