package com.penn.myapplication

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.penn.myapplication.ui.home.HomeFragment
import com.penn.myapplication.ui.me.MeFragment
import com.penn.myapplication.ui.todo.TodoFragment
import com.penn.myapplication.ui.tools.ToolsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var homeFragment: HomeFragment
    private lateinit var toolsFragment: ToolsFragment
    private lateinit var todoFragment: TodoFragment
    private lateinit var meFragment: MeFragment

    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupFragments(savedInstanceState)
        setupBottomNavigation()
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        val fm = supportFragmentManager

        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            toolsFragment = ToolsFragment()
            todoFragment = TodoFragment()
            meFragment = MeFragment()

            fm.beginTransaction().apply {
                add(R.id.fragment_container, homeFragment, "home").show(homeFragment)
                add(R.id.fragment_container, toolsFragment, "tools").hide(toolsFragment)
                add(R.id.fragment_container, todoFragment, "todo").hide(todoFragment)
                add(R.id.fragment_container, meFragment, "me").hide(meFragment)
                commit()
            }
            activeFragment = homeFragment
        } else {
            homeFragment = fm.findFragmentByTag("home") as? HomeFragment ?: HomeFragment()
            toolsFragment = fm.findFragmentByTag("tools") as? ToolsFragment ?: ToolsFragment()
            todoFragment = fm.findFragmentByTag("todo") as? TodoFragment ?: TodoFragment()
            meFragment = fm.findFragmentByTag("me") as? MeFragment ?: MeFragment()

            activeFragment = homeFragment
        }
    }

    private fun setupBottomNavigation() {
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        navView.setOnItemSelectedListener { item ->
            val targetFragment: Fragment = when (item.itemId) {
                R.id.navigation_home -> homeFragment
                R.id.navigation_tools -> toolsFragment
                R.id.navigation_todo -> todoFragment
                R.id.navigation_me -> meFragment
                else -> homeFragment
            }

            if (targetFragment != activeFragment) {
                supportFragmentManager.beginTransaction()
                    .hide(activeFragment!!)
                    .show(targetFragment)
                    .commit()
                activeFragment = targetFragment
            }
            true
        }
    }
}
