// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.hughes.android.dictionary;

import java.io.File;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.hughes.android.util.PersistentObjectCache;

public class DictionaryManagerActivity extends ListActivity {

  static final String LOG = "QuickDic";
  
  QuickDicConfig quickDicConfig;
  
  
  public void onCreate(Bundle savedInstanceState) {
    //((DictionaryApplication)getApplication()).applyTheme(this);

    super.onCreate(savedInstanceState);
    Log.d(LOG, "onCreate:" + this);

    // UI init.
    setContentView(R.layout.list_activity);

    getListView().setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int index,
          long id) {
        onClick(index);
      }
    });

    // ContextMenu.
    registerForContextMenu(getListView());

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    final String thanksForUpdatingLatestVersion = getString(R.string.thanksForUpdatingVersion);
    if (!prefs.getString(C.THANKS_FOR_UPDATING_VERSION, "").equals(thanksForUpdatingLatestVersion)) {
      final AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setCancelable(false);
      final WebView webView = new WebView(getApplicationContext());
      webView.loadData(getString(R.string.thanksForUpdating), "text/html", "utf-8");
      builder.setView(webView);
      builder.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
               dialog.cancel();
          }
      });
      final AlertDialog alert = builder.create();
      WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
      layoutParams.copyFrom(alert.getWindow().getAttributes());
      layoutParams.width = WindowManager.LayoutParams.FILL_PARENT;
      layoutParams.height = WindowManager.LayoutParams.FILL_PARENT;
      alert.show();
      alert.getWindow().setAttributes(layoutParams);
      prefs.edit().putString(C.THANKS_FOR_UPDATING_VERSION, thanksForUpdatingLatestVersion).commit();
    }
  }
  
  private void onClick(int dictIndex) {
    final Intent intent = DictionaryActivity.getIntent(this, dictIndex, 0, "");
    startActivity(intent);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    
    if (PreferenceActivity.prefsMightHaveChanged) {
      PreferenceActivity.prefsMightHaveChanged = false;
      finish();
      startActivity(getIntent());
    }
    
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    if (prefs.contains(C.DICT_INDEX) && prefs.contains(C.INDEX_INDEX)) {
      Log.d(LOG, "Skipping Dictionary List, going straight to dictionary.");
      startActivity(DictionaryActivity.getIntent(this, prefs.getInt(C.DICT_INDEX, 0), prefs.getInt(C.INDEX_INDEX, 0), prefs.getString(C.SEARCH_TOKEN, "")));
      //finish();
      return;
    }

    quickDicConfig = PersistentObjectCache.init(this).read(C.DICTIONARY_CONFIGS, QuickDicConfig.class);
    if (quickDicConfig == null) {
      quickDicConfig = new QuickDicConfig(this);
    } else {
      quickDicConfig.addDefaultDictionaries(this);
    }
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, quickDicConfig);

    Log.d(LOG, "DictionaryList: " + quickDicConfig.dictionaryInfos);
    setListAdapter(new Adapter());
  }

  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuItem about = menu.add(getString(R.string.about));
    about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        final Intent intent = new Intent().setClassName(AboutActivity.class
            .getPackage().getName(), AboutActivity.class.getCanonicalName());
        startActivity(intent);
        return false;
      }
    });
    
    final MenuItem preferences = menu.add(getString(R.string.preferences));
    preferences.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(final MenuItem menuItem) {
        PreferenceActivity.prefsMightHaveChanged = true;
        startActivity(new Intent(DictionaryManagerActivity.this,
            PreferenceActivity.class));
        return false;
      }
    });
    
    return true;
  }
  

  @Override
  public void onCreateContextMenu(final ContextMenu menu, final View view,
      final ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, view, menuInfo);
    
    final AdapterContextMenuInfo adapterContextMenuInfo = (AdapterContextMenuInfo) menuInfo;
    
    if (adapterContextMenuInfo.position > 0) {
      final MenuItem moveToTopMenuItem = menu.add(R.string.moveToTop);
      moveToTopMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
          final DictionaryInfo dictionaryConfig = quickDicConfig.dictionaryInfos.remove(adapterContextMenuInfo.position);
          quickDicConfig.dictionaryInfos.add(0, dictionaryConfig);
          dictionaryConfigsChanged();
          return true;
        }
      });
    }

    final MenuItem deleteMenuItem = menu.add(R.string.deleteDictionary);
    deleteMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        quickDicConfig.dictionaryInfos.remove(adapterContextMenuInfo.position);
        dictionaryConfigsChanged();
        return true;
      }
    });

  }

  private void dictionaryConfigsChanged() {
    PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, quickDicConfig);
    setListAdapter(getListAdapter());
  }

  class Adapter extends BaseAdapter {

    @Override
    public int getCount() {
      return quickDicConfig.dictionaryInfos.size();
    }

    @Override
    public DictionaryInfo getItem(int position) {
      return quickDicConfig.dictionaryInfos.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final DictionaryInfo dictionaryConfig = getItem(position);
      final TableLayout tableLayout = new TableLayout(parent.getContext());
      final TextView view = new TextView(parent.getContext());
      
      String name = dictionaryConfig.name;
      if (!new File(dictionaryConfig.localFile).canRead()) {
        name = getString(R.string.notOnDevice, dictionaryConfig.name);
      }

      view.setText(name);
      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
      tableLayout.addView(view);

      return tableLayout;
    }
    
  }

  public static Intent getIntent(final Context context) {
    DictionaryActivity.clearDictionaryPrefs(context);
    final Intent intent = new Intent();
    intent.setClassName(DictionaryManagerActivity.class.getPackage().getName(),
        DictionaryManagerActivity.class.getName());
    return intent;
  }

}
