package dev.keiji.sample.biometricsample

import android.app.Application
import dev.keiji.sample.biometricsample.CryptoAuthManager
import dev.keiji.sample.biometricsample.ICryptoAuthManager

class MainApplication : Application() {

    val cryptoAuthManager: ICryptoAuthManager = CryptoAuthManager()
}
