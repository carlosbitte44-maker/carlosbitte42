package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.LabelRepository
import com.example.service.FirebaseService
import com.example.ui.LogisticsViewModel
import com.example.ui.LogisticsViewModelFactory
import com.example.ui.screens.AppNavigation
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase and Cloud Services
        FirebaseService.init(this)
        
        // Initialize Room Database Components
        val database = AppDatabase.getDatabase(this)
        val repository = LabelRepository(database.labelDao())
        
        // Initialize ViewModel using Factory
        val viewModel = ViewModelProvider(
            this,
            LogisticsViewModelFactory(repository)
        )[LogisticsViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}
