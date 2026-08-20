package com.arthur.roottools.app

import android.app.Application

class RootToolsApp : Application() {
    internal val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
}
