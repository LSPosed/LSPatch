package org.lsposed.lspatch.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import org.lsposed.lspatch.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}