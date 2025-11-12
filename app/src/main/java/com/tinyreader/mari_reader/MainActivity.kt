package com.tinyreader.mari_reader

import android.os.Bundle
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tinyreader.mari_reader.data.MariReaderDatabase
import com.tinyreader.mari_reader.data.MariReaderRepository
import com.tinyreader.mari_reader.viewmodel.MainViewModel
import com.tinyreader.mari_reader.viewmodel.MainViewModelFactory

class MainActivity : AppCompatActivity() {

    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = MariReaderDatabase.getDatabase(this)
        val repository = MariReaderRepository(database.sourceDao(), database.mangaDao(), database.chapterDao(), database.readingHistoryDao())
        val factory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        val fabMenu: FloatingActionButton = findViewById(R.id.fab_menu)
        fabMenu.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.bottom_nav_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.navigation_library -> navController.navigate(R.id.navigation_library)
                    R.id.navigation_sources -> navController.navigate(R.id.navigation_sources)
                    R.id.navigation_settings -> navController.navigate(R.id.navigation_settings)
                }
                true
            }
            popupMenu.show()
        }
    }
}