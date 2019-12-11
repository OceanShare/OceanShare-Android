package com.oceanshare.oceanshare.authentication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.oceanshare.oceanshare.MainActivity
import com.oceanshare.oceanshare.R
import kotlinx.android.synthetic.main.activity_connection.*
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent


class AuthenticationActivity : AppCompatActivity(),
        RegisterFragment.OnFragmentInteractionListener,
        LoginFragment.OnFragmentInteractionListener,
        WalkthroughFragment.OnFragmentInteractionListener,
        LoginFragment.Callback, RegisterFragment.Callback, WalkthroughFragment.Callback {

    companion object {
        const val REQUEST_LOCATION = 20
    }

    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null

    private var fbAuth = FirebaseAuth.getInstance()

    private var locationAlertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocationPermissions()
        setContentView(R.layout.activity_connection)
    }

    public override fun onStart() {
        super.onStart()

        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && isUserIsAlreadyConnected(fbAuth.currentUser)) {
            redirectToHomePage()
        } else {
            showPermissionRequestDialog()
        }
        setupSectionsPagerAdapter()
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (isOpen) {
                dotsIndicator.visibility = View.GONE
            } else {
                dotsIndicator.visibility = View.VISIBLE
            }
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                showPermissionRequestDialog()
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            }
        } else {
            if (isUserIsAlreadyConnected(fbAuth.currentUser)) {
                redirectToHomePage()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    if (isUserIsAlreadyConnected(fbAuth.currentUser)) {
                        redirectToHomePage()
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    showPermissionRequestDialog()
                }
                return
            }
        }
    }

    private fun setupLocationAlertDialog(): AlertDialog {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(resources.getString(R.string.dialog_we_have_problem))
        builder.setMessage(resources.getString(R.string.dialog_we_need_location))

        builder.setPositiveButton(android.R.string.yes) { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
            locationAlertDialog = null
        }

        builder.setNegativeButton(android.R.string.no) { _, _ ->
            locationAlertDialog = null
            Handler().postDelayed({
                showPermissionRequestDialog()
            }, 1000)
        }
        return builder.create()
    }

    private fun showPermissionRequestDialog() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && locationAlertDialog == null) {
            locationAlertDialog = setupLocationAlertDialog()
            locationAlertDialog?.show()
        }
    }

    private fun redirectToHomePage() {
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        startActivity(mainActivityIntent)
        finish()
    }

    private fun isUserIsAlreadyConnected(currentUser: FirebaseUser?): Boolean {
        GoogleAuthentication.instantiateGoogleSignInClient(this)
        if (currentUser != null) {
            if (!currentUser.isEmailVerified) {
                return false
            }
        }
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

        dotsIndicator.setViewPager(container)
        container.adapter?.registerDataSetObserver(dotsIndicator.dataSetObserver)
    }

    override fun showRegistrationPage() {
        container.setCurrentItem(2, true)
    }

    override fun showLoginPage() {
        container.setCurrentItem(1, true)
    }

    override fun toggleDotIndicatorVisibility() {
        if (dotsIndicator.visibility == View.VISIBLE) {
            dotsIndicator.visibility = View.GONE
        } else {
            dotsIndicator.visibility = View.VISIBLE
        }
    }

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
