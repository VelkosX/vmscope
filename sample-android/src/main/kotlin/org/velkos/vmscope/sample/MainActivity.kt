package org.velkos.vmscope.sample

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import org.velkos.vmscope.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val viewModel: SampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSuccessful.setOnClickListener { viewModel.launchSuccessful() }
        binding.buttonThrowing.setOnClickListener { viewModel.launchThrowing() }
        binding.buttonViewModelScope.setOnClickListener { viewModel.launchOnViewModelScope() }
        binding.buttonViewModelScopeThrowing.setOnClickListener { viewModel.launchThrowingOnViewModelScope() }
    }
}
