package com.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        handleNavigationIntent(intent, navController)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        handleNavigationIntent(intent, navHostFragment.navController)
    }

    private fun handleNavigationIntent(intent: android.content.Intent, navController: androidx.navigation.NavController) {
        val navigateTo = intent.getStringExtra("EXTRA_NAVIGATE_TO")
        if (navigateTo == "CHANGE_DETAILS") {
            val ruleId = intent.getIntExtra("EXTRA_RULE_ID", -1)
            if (ruleId != -1) {
                val bundle = android.os.Bundle()
                bundle.putInt("ruleId", ruleId)
                navController.navigate(R.id.changeDetailsFragment, bundle)
            }
        }
    }
}
