package com.vknxworks.elementctrl

import android.content.Context
import com.vknxworks.elementctrl.matrix.AppGraph

/** The app's singletons, the same way the other Ctrl apps reach theirs (the shared app lock uses it). */
val Context.container: AppGraph
    get() = ElementCtrlApp.graph
