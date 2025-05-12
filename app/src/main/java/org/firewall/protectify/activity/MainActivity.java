package org.firewall.protectify.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import org.firewall.protectify.BuildConfig;
import org.firewall.protectify.Daedalus;
import org.firewall.protectify.R;
import org.firewall.protectify.fragment.*;
import org.firewall.protectify.service.DaedalusVpnService;
import org.firewall.protectify.util.Logger;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "DMainActivity";

    public static final String LAUNCH_ACTION = "org.firewall.protectify.activity.MainActivity.LAUNCH_ACTION";
    public static final int LAUNCH_ACTION_NONE = 0;
    public static final int LAUNCH_ACTION_ACTIVATE = 1;
    public static final int LAUNCH_ACTION_DEACTIVATE = 2;
    public static final int LAUNCH_ACTION_SERVICE_DONE = 3;

    public static final String LAUNCH_FRAGMENT = "org.firewall.protectify.activity.MainActivity.LAUNCH_FRAGMENT";
    public static final int FRAGMENT_NONE = -1;
    public static final int FRAGMENT_HOME = 0;
    public static final int FRAGMENT_DNS_TEST = 1;
    public static final int FRAGMENT_SETTINGS = 2;
    public static final int FRAGMENT_RULES = 4;
    public static final int FRAGMENT_DNS_SERVERS = 5;

    public static final String LAUNCH_NEED_RECREATE = "org.firewall.protectify.activity.MainActivity.LAUNCH_NEED_RECREATE";

    private static MainActivity instance = null;
    private ToolbarFragment currentFragment;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Daedalus.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark_NoActionBar_TransparentStatusBar);
        }
        super.onCreate(savedInstanceState);

        instance = this;

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);

        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_nav_version))
                .setText(getString(R.string.nav_version) + " " + BuildConfig.VERSION_NAME);
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.textView_nav_git_commit))
                .setText(getString(R.string.nav_git_commit) + " " + BuildConfig.GIT_COMMIT);

        updateUserInterface(getIntent());
    }

    private void switchFragment(Class fragmentClass) {
        if (currentFragment == null || fragmentClass != currentFragment.getClass()) {
            try {
                ToolbarFragment fragment = (ToolbarFragment) fragmentClass.newInstance();
                getSupportFragmentManager().beginTransaction().replace(R.id.id_content, fragment).commit();
                currentFragment = fragment;
            } catch (Exception e) {
                Logger.logException(e);
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (!(currentFragment instanceof HomeFragment)) {
            switchFragment(HomeFragment.class);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
        currentFragment = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        updateUserInterface(intent);
    }

    public void activateService() {
        Intent intent = VpnService.prepare(Daedalus.getInstance());
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, Activity.RESULT_OK, null);
        }

        long activateCounter = Daedalus.configurations.getActivateCounter();
        if (activateCounter == -1) {
            return;
        }
        activateCounter++;
        Daedalus.configurations.setActivateCounter(activateCounter);
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result == Activity.RESULT_OK) {
            Daedalus.activateService(Daedalus.getInstance());
            updateMainButton(R.string.button_text_deactivate);
            Daedalus.updateShortcut(getApplicationContext());
        }
    }

    private void updateMainButton(int id) {
        if (currentFragment instanceof HomeFragment) {
            Button button = currentFragment.getView().findViewById(R.id.button_activate);
            button.setText(id);
        }
    }

    private void updateUserInterface(Intent intent) {
        int launchAction = intent.getIntExtra(LAUNCH_ACTION, LAUNCH_ACTION_NONE);
        Log.d(TAG, "Updating user interface with Launch Action " + launchAction);

        if (launchAction == LAUNCH_ACTION_ACTIVATE) {
            this.activateService();
        } else if (launchAction == LAUNCH_ACTION_DEACTIVATE) {
            Daedalus.deactivateService(getApplicationContext());
        } else if (launchAction == LAUNCH_ACTION_SERVICE_DONE) {
            Daedalus.updateShortcut(getApplicationContext());
            if (DaedalusVpnService.isActivated()) {
                updateMainButton(R.string.button_text_deactivate);
            } else {
                updateMainButton(R.string.button_text_activate);
            }
        }

        int fragment = intent.getIntExtra(LAUNCH_FRAGMENT, FRAGMENT_NONE);

        if (intent.getBooleanExtra(LAUNCH_NEED_RECREATE, false)) {
            finish();
            overridePendingTransition(R.anim.start, R.anim.end);
            if (fragment != FRAGMENT_NONE) {
                startActivity(new Intent(this, MainActivity.class)
                        .putExtra(LAUNCH_FRAGMENT, fragment));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            return;
        }

        if (fragment == FRAGMENT_DNS_SERVERS) {
            switchFragment(DnsServersFragment.class);
        } else if (fragment == FRAGMENT_DNS_TEST) {
            switchFragment(DnsTestFragment.class);
        } else if (fragment == FRAGMENT_HOME) {
            switchFragment(HomeFragment.class);
        } else if (fragment == FRAGMENT_RULES) {
            switchFragment(RulesFragment.class);
        } else if (fragment == FRAGMENT_SETTINGS) {
            switchFragment(SettingsFragment.class);
        }

        if (currentFragment == null) {
            switchFragment(HomeFragment.class);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dns_server) {
            switchFragment(DnsServersFragment.class);
        } else if (id == R.id.nav_dns_test) {
            switchFragment(DnsTestFragment.class);
        } else if (id == R.id.nav_home) {
            switchFragment(HomeFragment.class);
        } else if (id == R.id.nav_rules) {
            switchFragment(RulesFragment.class);
        } else if (id == R.id.nav_settings) {
            switchFragment(SettingsFragment.class);
        }

        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(R.id.id_content).getWindowToken(), 0);
        return true;
    }
}