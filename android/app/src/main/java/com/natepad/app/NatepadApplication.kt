package com.natepad.app

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class NatepadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register Bouncy Castle provider, replacing Android's built-in BC stub
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
