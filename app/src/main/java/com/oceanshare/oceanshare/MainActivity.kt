package com.oceanshare.oceanshare

import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val tmpFragment = TmpFragment()
    private val profileFragment = ProfileFragment()

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                return@OnNavigationItemSelectedListener switchFragments()//loadFragment(homeFragment)
            }
            /*
            R.id.navigation_dashboard -> {
                return@OnNavigationItemSelectedListener switchFragments(item.itemId)
            }
            */
            R.id.navigation_profile -> {
                return@OnNavigationItemSelectedListener switchFragments()//loadFragment(profileFragment)
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
                .hide(profileFragment)
                .commit()
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
    }

    private fun switchFragments() : Boolean {
        if (profileFragment.isHidden) {
            supportFragmentManager.beginTransaction()
                    .show(profileFragment)
                    .commit()
        } else {
            supportFragmentManager.beginTransaction()
                    .hide(profileFragment)
                    .commit()
        }
        return true
    }
}
