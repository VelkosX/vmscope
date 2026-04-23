package org.velkos.vmscope.sample.kmp

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import org.velkos.vmscope.sample.SharedSampleViewModel
import org.velkos.vmscope.sample.kmp.databinding.ActivityMainBinding

/**
 * Four-button launcher wired to [SharedSampleViewModel] — the same ViewModel that drives the
 * iOS app. Demonstrates that consumer Android code reduces to standard `by viewModels()`
 * delegation; there's no KMP-specific indirection on this side.
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: SharedSampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSuccessful.setOnClickListener { viewModel.launchSuccessful() }
        binding.buttonThrowing.setOnClickListener { viewModel.launchThrowing() }
        binding.buttonViewModelScope.setOnClickListener { viewModel.launchOnViewModelScopeEquivalent() }
        binding.buttonViewModelScopeThrowing.setOnClickListener { viewModel.launchThrowingOnViewModelScope() }
    }
}
