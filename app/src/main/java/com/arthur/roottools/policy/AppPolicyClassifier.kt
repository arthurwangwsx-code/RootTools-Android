package com.arthur.roottools.policy

import com.arthur.roottools.model.AppPolicyCategory

object AppPolicyClassifier {
    fun categoryFor(packageName: String): AppPolicyCategory = when (packageName) {
        in PROTECTED -> AppPolicyCategory.PROTECTED
        in FREEZE -> AppPolicyCategory.FREEZE
        in ON_DEMAND -> AppPolicyCategory.ON_DEMAND
        in RARE -> AppPolicyCategory.RARE
        else -> AppPolicyCategory.NORMAL
    }

    val PROTECTED = PackagePolicyController.PROTECTED_PACKAGES
    val FREEZE = setOf("com.tencent.android.qqdownloader")
    val ON_DEMAND = setOf("com.apextuner.app.debug", "io.appium.settings", "com.omarea.vtools", "net.dinglisch.android.taskerm")
    val RARE = setOf("com.bilibili.app.in", "com.facebook.katana", "com.esuper.file.explorer", "com.google.android.apps.photos")
}
