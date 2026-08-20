package com.arthur.roottools.app

import android.content.Context

internal val Context.rootToolsContainer: AppContainer
    get() = (applicationContext as RootToolsApp).container
