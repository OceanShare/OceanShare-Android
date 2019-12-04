package com.oceanshare.oceanshare.authentication

import android.content.Intent
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.oceanshare.oceanshare.MainActivity
import com.oceanshare.oceanshare.R
import kotlinx.android.synthetic.main.activity_connection.*

class AuthenticationActivity : AppCompatActivity(),
        RegisterFragment.OnFragmentInteractionListener,
        LoginFragment.OnFragmentInteractionListener,
        WalkthroughFragment.OnFragmentInteractionListener,
        LoginFragment.Callback, RegisterFragment.Callback, WalkthroughFragment.Callback {

    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null

    private var fbAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)
    }

    public override fun onStart() {
        super.onStart()

        if (isUserIsAlreadyConnected(fbAuth.currentUser)) {
            redirectToHomePage()
        }

        setupSectionsPagerAdapter()
    }

    private fun redirectToHomePage() {
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        startActivity(mainActivityIntent)
        finish()
    }

    private fun isUserIsAlreadyConnected(currentUser: FirebaseUser?): Boolean {
        GoogleAuthentication.instantiateGoogleSignInClient(this)
        return FacebookAuthentication.isConnected(this) || GoogleAuthentication.isConnected(this) || currentUser != null
    }

    private fun setupSectionsPagerAdapter() {

        // Create the adapter that will return a fragment for each of the two
        // primary sections of the activity.
        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        // Set up the ViewPager with the sections adapter.
        container.adapter = mSectionsPagerAdapter

        container.clipToPadding = false
        //container.setPadding(100, 0, 100, 0)
        container.pageMargin = 0
    }

    override fun showRegistrationPage() {
        container.setCurrentItem(2, true)
    }

    override fun showLoginPage() {
        container.setCurrentItem(1, true)
    }

    //override fun showWalkthroughPage() {
    //    container.setCurrentItem(0, true)
    //}

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_connection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        if (id == R.id.action_settings) {
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onFragmentInteraction(uri: Uri) {}

    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return when (position) {
                0 -> WalkthroughFragment.newInstance()
                1 -> LoginFragment.newInstance()
                else -> RegisterFragment.newInstance()
            }
        }

        override fun getCount(): Int {
            return 3
        }
    }
}
