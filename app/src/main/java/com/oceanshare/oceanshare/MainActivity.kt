package com.oceanshare.oceanshare

import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val meteoFragment = MeteoFragment()
    private val profileFragment = ProfileFragment()

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                return@OnNavigationItemSelectedListener switchFragments(item.itemId)//loadFragment(homeFragment)
            }
            R.id.navigation_dashboard -> {
                return@OnNavigationItemSelectedListener switchFragments(item.itemId)
            }
            R.id.navigation_profile -> {
                return@OnNavigationItemSelectedListener switchFragments(item.itemId)//loadFragment(profileFragment)
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, homeFragment)
                .add(R.id.fragment_container, profileFragment)
                .add(R.id.fragment_container, meteoFragment)
                .hide(profileFragment)
                .hide(meteoFragment)
                .commit()
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
    }

    private fun switchFragments(id: Int) : Boolean {
        if (id == R.id.navigation_home) {
            supportFragmentManager.beginTransaction()
                    .show(homeFragment)
                    .hide(meteoFragment)
                    .hide(profileFragment)
                    .commit()
        } else if (id == R.id.navigation_dashboard) {
            GlobalScope.launch(Dispatchers.Main) {
                meteoFragment.fetchMeteo(homeFragment.originLocation)
            }
            supportFragmentManager.beginTransaction()
                    .show(meteoFragment)
                    .hide(homeFragment)
                    .hide(profileFragment)
                    .commit()
        } else if (id == R.id.navigation_profile) {
            supportFragmentManager.beginTransaction()
                    .show(profileFragment)
                    .hide(homeFragment)
                    .hide(meteoFragment)
                    .commit()
        }
        return true
    }
}
