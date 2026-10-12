package org.zarroboogs.maps.ui.maps

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.zarroboogs.maps.R
import org.zarroboogs.maps.ui.BaseActivity
import org.zarroboogs.maps.ui.home.HomeFragment
import org.zarroboogs.maps.ui.settings.SettingsActionListener
import org.zarroboogs.maps.ui.settings.SettingsContainerFragment
import org.zarroboogs.maps.ui.hiking.HikingContainerFragment

class MapsMainActivity : BaseActivity(),
    MapsFragment.OnFragmentInteractionListener,
    SettingsActionListener {

    private var homeFragment: HomeFragment? = null
    private var mapsFragment: MapsFragment? = null
    private var hikingFragment: HikingContainerFragment? = null
    private var settingsFragment: SettingsContainerFragment? = null
    private var activeFragment: Fragment? = null

    private lateinit var bottomNavigation: BottomNavigationView

    companion object {
        private const val TAG_HOME = "fragment_home"
        private const val TAG_MAP = "fragment_map"
        private const val TAG_HIKING = "fragment_hiking"
        private const val TAG_SETTINGS = "fragment_settings"
        private const val REQUEST_LOCATION_PERMISSION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_bottom_nav)

        checkLocationPermission()

        bottomNavigation = findViewById(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            mapsFragment = MapsFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_fragment, mapsFragment!!, TAG_MAP)
                .hide(mapsFragment!!)
                .add(R.id.nav_host_fragment, homeFragment!!, TAG_HOME)
                .commit()
            activeFragment = homeFragment
        } else {
            // 恢复 Fragment 引用
            homeFragment = supportFragmentManager.findFragmentByTag(TAG_HOME) as? HomeFragment
            mapsFragment = supportFragmentManager.findFragmentByTag(TAG_MAP) as? MapsFragment
            hikingFragment = supportFragmentManager.findFragmentByTag(TAG_HIKING) as? HikingContainerFragment
            settingsFragment = supportFragmentManager.findFragmentByTag(TAG_SETTINGS) as? SettingsContainerFragment

            // 找到当前可见的 Fragment
            activeFragment = listOfNotNull(homeFragment, mapsFragment, hikingFragment, settingsFragment)
                .firstOrNull { !it.isHidden } ?: homeFragment
        }

        @Suppress("DEPRECATION")
        bottomNavigation.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_page1 -> {
                    switchToFragment(homeFragment ?: HomeFragment().also { homeFragment = it }, TAG_HOME)
                    true
                }
                R.id.navigation_page2 -> {
                    switchToFragment(mapsFragment ?: MapsFragment().also { mapsFragment = it }, TAG_MAP)
                    true
                }
                R.id.navigation_page3 -> {
                    switchToFragment(hikingFragment ?: HikingContainerFragment().also { hikingFragment = it }, TAG_HIKING)
                    true
                }
                R.id.navigation_page4 -> {
                    switchToFragment(settingsFragment ?: SettingsContainerFragment().also { settingsFragment = it }, TAG_SETTINGS)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchToFragment(target: Fragment, tag: String) {
        if (target === activeFragment) return

        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        // 隐藏当前 Fragment
        activeFragment?.let { transaction.hide(it) }

        // 如果目标 Fragment 还没 add，先 add
        if (!target.isAdded) {
            transaction.add(R.id.nav_host_fragment, target, tag)
        } else {
            transaction.show(target)
        }

        transaction.commit()
        activeFragment = target
    }

    // ===== 徒步导航全屏切换 =====

    /** 隐藏底部 Tab 栏（徒步导航全屏模式） */
    fun hideTabBar() {
        bottomNavigation.animate()
            .translationY(bottomNavigation.height.toFloat())
            .alpha(0f)
            .setDuration(300)
            .withEndAction { bottomNavigation.visibility = android.view.View.GONE }
            .start()
    }

    /** 显示底部 Tab 栏（退出全屏） */
    fun showTabBar() {
        bottomNavigation.visibility = android.view.View.VISIBLE
        bottomNavigation.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    // MapsFragment 向后兼容：原 DrawerLayout 相关方法保留为空实现
    fun openLeftDrawer() { /* no-op, drawer removed */ }
    fun closeLeftDrawer() { /* no-op, drawer removed */ }

    // MapsFragment.OnFragmentInteractionListener
    override fun onFragmentInteraction(uri: Uri) { /* no-op */ }

    // SettingsActionListener: 设置页 → MapsFragment 通信
    override fun onSatelliteToggled() {
        mapsFragment?.onLeftDrawerViewClick(R.id.left_drawer_satellite)
    }

    override fun onBackPressed() {
        val home = homeFragment
        if (home != null && activeFragment === home && home.onBackPressedFromChild()) return

        val maps = mapsFragment
        if (maps != null && activeFragment === maps) {
            if (maps.isInSearch) {
                maps.exitSearch()
                return
            }
            if (maps.isFilterWindowShowing) {
                maps.hideCameraFilterWindow()
                return
            }
        }
        super.onBackPressed()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mapsFragment?.let {
                    it.init()
                    it.startLocation()
                }
            }
        }
    }
}
