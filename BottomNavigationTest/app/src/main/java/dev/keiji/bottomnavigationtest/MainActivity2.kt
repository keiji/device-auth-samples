package dev.keiji.bottomnavigationtest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.keiji.bottomnavigationtest.databinding.ActivityMain2Binding

class MainActivity2 : AppCompatActivity() {
    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, MainActivity2::class.java).also {
            }
        }
    }

    private lateinit var binding: ActivityMain2Binding
    val itemId = R.id.navigation_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overridePendingTransition(0x0, 0x0);

        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navView.selectedItemId = itemId
        binding.navView.setOnItemSelectedListener { item ->
            return@setOnItemSelectedListener when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(MainActivity.newIntent(this@MainActivity2))
                    finish()
                    true
                }
                R.id.navigation_dashboard -> {
//                    startActivity(MainActivity2.newIntent(this@MainActivity2))
                    true
                }
                R.id.navigation_notifications -> {
                    startActivity(MainActivity3.newIntent(this@MainActivity2))
                    finish()
                    true
                }
                else -> {
                    startActivity(newIntent(this@MainActivity2))
                    finish()
                    false
                }
            }
        }
    }
}