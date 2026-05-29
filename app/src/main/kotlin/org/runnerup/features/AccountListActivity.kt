/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.ContentValues
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.core.util.CardPressHelper
import org.runnerup.common.util.Constants
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.Bitfield
import org.runnerup.core.util.NetworkUtils.isNetworkAvailable
import org.runnerup.core.util.ViewUtil
import org.runnerup.data.DBHelper
import org.runnerup.sync.SyncManager
import org.runnerup.sync.Synchronizer
import org.runnerup.sync.Synchronizer.Status

class AccountListActivity : AppCompatActivity(), Constants {

  private var db: SQLiteDatabase? = null
  private var syncManager: SyncManager? = null
  private var showDisabled = false
  private var loadGeneration = 0
  private lateinit var adapter: AccountListAdapter
  private lateinit var editAccountLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
  private lateinit var configureLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    setContentView(R.layout.account_list)

    db = DBHelper.getReadableDatabase(this)
    syncManager = SyncManager(this)
    configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          syncManager?.handleConfigureResult(result.resultCode, result.data)
          adapter.notifyDataSetChanged()
        }
    syncManager?.setConfigureLauncher(configureLauncher)
    editAccountLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
          syncManager?.clear()
          reloadAccounts()
        }
    adapter = AccountListAdapter()
    val recyclerView = findViewById<RecyclerView>(R.id.account_list_list)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.adapter = adapter
    reloadAccounts()

    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = "Accounts"
      setBackgroundDrawable(
          android.graphics.drawable.ColorDrawable(
              getColor(R.color.backgroundPrimary),
          ),
      )
    }
    ViewUtil.Insets(findViewById(R.id.account_list_view), true)
  }

  override fun onDestroy() {
    super.onDestroy()
    loadGeneration++
    DBHelper.closeDB(db)
    syncManager?.close()
  }

  private fun reloadAccounts() {
    val database = db ?: return
    val gen = ++loadGeneration
    BgTasks.runDb(
        { queryAccountValues(database) },
        { valuesList ->
          if (gen != loadGeneration || isFinishing) return@runDb
          adapter.submit(buildDisplayRows(valuesList), showDisabled)
        },
    )
  }

  private fun buildDisplayRows(valuesList: List<ContentValues>): List<AccountListItem> {
    val rows = ArrayList<AccountListItem>()
    var prevConfigured = false
    valuesList.forEachIndexed { index, values ->
      val sync = syncManager?.add(values)
      val configured = sync != null && sync.isConfigured
      val showSection = index == 0 || configured != prevConfigured
      rows.add(AccountListItem.AccountRow(values, showSection, configured))
      prevConfigured = configured
    }
    rows.add(AccountListItem.Footer)
    return rows
  }

  private fun queryAccountValues(database: SQLiteDatabase): List<ContentValues> {
    val selection =
        if (!showDisabled) {
          DB.ACCOUNT.ENABLED + "==1 or " + DB.ACCOUNT.AUTH_CONFIG + " is not null"
        } else {
          null
        }
    val sortOrder =
        DB.ACCOUNT.AUTH_CONFIG +
            " is null, " +
            DB.ACCOUNT.NAME +
            " collate nocase," +
            DB.ACCOUNT.ENABLED +
            " desc "
    val result = ArrayList<ContentValues>()
    try {
      database
          .query(
              DB.ACCOUNT.TABLE,
              ACCOUNT_PROJECTION,
              selection,
              null,
              null,
              null,
              sortOrder,
          )
          .use { cursor ->
            while (cursor.moveToNext()) {
              result.add(DBHelper.get(cursor))
            }
          }
    } catch (ex: IllegalStateException) {
      android.util.Log.e(javaClass.name, "Query failed:", ex)
    }
    return result
  }

  private fun setFlag(synchronizerName: String, flag: Int, value: Boolean) {
    val database = db ?: return
    if (value) {
      val bitval = 1L shl flag
      database.execSQL(
          "update ${DB.ACCOUNT.TABLE} set ${DB.ACCOUNT.FLAGS} = ( ${DB.ACCOUNT.FLAGS}|$bitval) where ${DB.ACCOUNT.NAME} = '$synchronizerName'",
      )
    } else {
      val mask = (1L shl flag).inv()
      database.execSQL(
          "update ${DB.ACCOUNT.TABLE} set ${DB.ACCOUNT.FLAGS} = ( ${DB.ACCOUNT.FLAGS}&$mask) where ${DB.ACCOUNT.NAME} = '$synchronizerName'",
      )
    }
  }

  private fun startAccountActivity(synchronizerName: String) {
    val intent = Intent(this, AccountActivity::class.java)
    intent.putExtra("synchronizer", synchronizerName)
    editAccountLauncher.launch(intent)
  }

  private val connectCallback = SyncManager.Callback { synchronizerName, status ->
    if (status == Status.OK) {
      startAccountActivity(synchronizerName)
    }
  }

  private sealed class AccountListItem {
    data class AccountRow(
        val values: ContentValues,
        val showSection: Boolean,
        val sectionConnected: Boolean,
    ) : AccountListItem()

    object Footer : AccountListItem()
  }

  private inner class AccountListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = ArrayList<AccountListItem>()
    private var footerShowDisabled = false

    fun submit(rows: List<AccountListItem>, showDisabledAccounts: Boolean) {
      items.clear()
      items.addAll(rows)
      footerShowDisabled = showDisabledAccounts
      notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
          is AccountListItem.Footer -> VIEW_FOOTER
          else -> VIEW_ACCOUNT
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
          VIEW_FOOTER -> {
            val button = Button(parent.context)
            button.setTextAppearance(
                parent.context,
                androidx.appcompat.R.style.TextAppearance_AppCompat_Button,
            )
            button.setTextColor(getColor(R.color.colorText))
            button.background = null
            button.setOnClickListener {
              showDisabled = !showDisabled
              reloadAccounts()
            }
            FooterHolder(button)
          }
          else ->
              AccountHolder(
                  LayoutInflater.from(parent.context)
                      .inflate(R.layout.account_row, parent, false),
              )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      when (val item = items[position]) {
        is AccountListItem.Footer -> bindFooter(holder as FooterHolder)
        is AccountListItem.AccountRow -> bindAccount(holder as AccountHolder, item)
      }
    }

    override fun getItemCount(): Int = items.size

    private fun bindFooter(holder: FooterHolder) {
      holder.button.text =
          getString(
              if (footerShowDisabled) {
                org.runnerup.common.R.string.Hide_disabled_accounts
              } else {
                org.runnerup.common.R.string.Show_disabled_accounts
              },
          )
    }

    private fun bindAccount(holder: AccountHolder, item: AccountListItem.AccountRow) {
      CardPressHelper.clearPressState(holder.itemView)
      val values = item.values
      val syncManager = syncManager ?: return
      val synchronizer = syncManager.add(values)
      val flags = values.getAsLong(DB.ACCOUNT.FLAGS)
      val name = values.getAsString(DB.ACCOUNT.NAME)
      val configured = synchronizer != null && synchronizer.isConfigured

      holder.itemView.tag = synchronizer
      holder.itemView.setOnClickListener { view ->
        if (!isNetworkAvailable(this@AccountListActivity)) {
          Toast.makeText(
                  this@AccountListActivity,
                  org.runnerup.common.R.string.check_internet_connection,
                  Toast.LENGTH_LONG,
              )
              .show()
          return@setOnClickListener
        }
        val sync = view.tag as? Synchronizer ?: return@setOnClickListener
        if (sync.isConfigured) {
          startAccountActivity(sync.name)
        } else {
          syncManager.connect(connectCallback, sync.name)
        }
      }

      if (item.showSection) {
        holder.sectionTitle.visibility = View.VISIBLE
        holder.sectionTitle.setText(
            if (item.sectionConnected) {
              org.runnerup.common.R.string.accounts_category_connected
            } else {
              org.runnerup.common.R.string.accounts_category_unconnected
            },
        )
      } else {
        holder.sectionTitle.visibility = View.GONE
      }

      holder.nameText.text = name

      if (synchronizer == null) {
        holder.uploadBox.visibility = View.GONE
        holder.icon.visibility = View.GONE
        holder.iconText.visibility = View.GONE
        holder.nameText.paintFlags = holder.nameText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        holder.nameText.isEnabled = false
        return
      }

      holder.nameText.paintFlags = holder.nameText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
      holder.nameText.isEnabled = true
      holder.uploadBox.visibility = View.VISIBLE
      holder.icon.visibility = View.VISIBLE
      holder.iconText.visibility = View.VISIBLE

      val synchronizerIcon = synchronizer.iconId
      if (synchronizerIcon == 0) {
        val circle = AppCompatResources.getDrawable(holder.itemView.context, R.drawable.circle_40dp)
        circle?.setColorFilter(
            ContextCompat.getColor(holder.itemView.context, synchronizer.colorId),
            PorterDuff.Mode.SRC_IN,
        )
        holder.icon.setImageDrawable(circle)
        holder.iconText.text = name.substring(0, 1)
      } else {
        holder.icon.setImageDrawable(
            AppCompatResources.getDrawable(holder.itemView.context, synchronizerIcon),
        )
        holder.iconText.text = null
      }

      setCustomThumb(holder.uploadBox, R.drawable.switch_upload)
      holder.uploadBox.tag = synchronizer
      holder.uploadBox.setOnCheckedChangeListener(null)
      holder.uploadBox.setOnCheckedChangeListener { switch, checked ->
        setFlag((switch.tag as Synchronizer).name, DB.ACCOUNT.FLAG_UPLOAD, checked)
      }

      if (configured && synchronizer.checkSupport(Synchronizer.Feature.UPLOAD)) {
        holder.uploadBox.isEnabled = true
        holder.uploadBox.isChecked = Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD)
        holder.uploadBox.visibility = View.VISIBLE
      } else {
        holder.uploadBox.visibility = View.GONE
      }
    }

    private fun setCustomThumb(switchCompat: SwitchCompat, drawableId: Int) {
      switchCompat.thumbDrawable = AppCompatResources.getDrawable(this@AccountListActivity, drawableId)
      switchCompat.thumbTintList =
          AppCompatResources.getColorStateList(this@AccountListActivity, R.color.switch_thumb)
      switchCompat.thumbTintMode = PorterDuff.Mode.MULTIPLY
    }

    inner class AccountHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val sectionTitle: TextView = itemView.findViewById(R.id.account_row_section_title)
      val icon: ImageView = itemView.findViewById(R.id.account_row_icon)
      val iconText: TextView = itemView.findViewById(R.id.account_row_icon_text)
      val nameText: TextView = itemView.findViewById(R.id.account_row_name)
      val uploadBox: SwitchCompat = itemView.findViewById(R.id.account_row_upload)
    }

    inner class FooterHolder(val button: Button) : RecyclerView.ViewHolder(button)
  }

  companion object {
    private const val VIEW_ACCOUNT = 0
    private const val VIEW_FOOTER = 1
    private val ACCOUNT_PROJECTION =
        arrayOf(
            "_id",
            DB.ACCOUNT.NAME,
            DB.ACCOUNT.AUTH_CONFIG,
            DB.ACCOUNT.FORMAT,
            DB.ACCOUNT.FLAGS,
        )
  }
}
