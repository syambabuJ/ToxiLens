package com.toxilens.yttoxicitychecker.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.toxilens.yttoxicitychecker.R
import com.toxilens.yttoxicitychecker.databinding.ActivityMainBinding
import com.toxilens.yttoxicitychecker.ui.auth.AuthViewModel
import com.toxilens.yttoxicitychecker.ui.auth.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // Check if user is logged in
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_analysis,
                R.id.navigation_analytics,
                R.id.navigation_history,
                R.id.navigation_channel
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        setupBottomNavAnimation()
        observeDeleteAccountMessage()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_delete_account -> {
                showDeleteAccountDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteAccountDialog() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val email = currentUser.email ?: ""

        // Create password input field
        val passwordInput = TextInputEditText(this).apply {
            hint = "Enter your password"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("⚠️ WARNING: This action is PERMANENT!\n\nAccount: $email\n\nAll your saved history and data will be lost forever.\n\nEnter your password to confirm deletion.")
            .setView(passwordInput)
            .setPositiveButton("Delete Account") { _, _ ->
                val password = passwordInput.text.toString().trim()
                if (password.isNotEmpty()) {
                    authViewModel.deleteAccount(password) {
                        // Navigate to login after successful deletion
                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_LONG).show()
                        finishAffinity()
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                } else {
                    Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeDeleteAccountMessage() {
        authViewModel.deleteAccountMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                authViewModel.clearDeleteAccountMessage()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun setupBottomNavAnimation() {
        binding.navView.setOnNavigationItemSelectedListener { item ->
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            when (item.itemId) {
                R.id.navigation_home -> {
                    navController.navigate(R.id.navigation_home)
                    animateIcon(item)
                    true
                }
                R.id.navigation_analysis -> {
                    navController.navigate(R.id.navigation_analysis)
                    animateIcon(item)
                    true
                }
                R.id.navigation_analytics -> {
                    navController.navigate(R.id.navigation_analytics)
                    animateIcon(item)
                    true
                }
                R.id.navigation_history -> {
                    navController.navigate(R.id.navigation_history)
                    animateIcon(item)
                    true
                }
                R.id.navigation_channel -> {
                    navController.navigate(R.id.navigation_channel)
                    animateIcon(item)
                    true
                }
                else -> false
            }
        }
    }

    private fun animateIcon(item: MenuItem) {
        val view = binding.navView.findViewById<android.view.View>(item.itemId)
        view.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(200)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }
}
