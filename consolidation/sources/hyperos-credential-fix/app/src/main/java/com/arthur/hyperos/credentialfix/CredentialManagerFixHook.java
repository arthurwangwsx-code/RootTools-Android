package com.arthur.hyperos.credentialfix;

import android.os.Process;

import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * HyperOS 3's MiuiCredentialManager ships both the full Android credential UI and a
 * Xiaomi-specific get UI. The Xiaomi UI treats PRIMARY_SELECTION with only an
 * AuthenticationEntry as an illegal empty state, which breaks Google ID login in XSpace.
 *
 * This module is deliberately scoped to the clone profile (user 999). HyperOS' custom UI
 * crashes the flow when Google returns an AuthenticationEntry but no ordinary credential
 * entries. In that exact state we feed the AuthenticationEntry back into the existing
 * CredentialSelectorViewModel and launch its provider UI directly. Every other state keeps
 * using the OEM implementation unchanged.
 */
public final class CredentialManagerFixHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.android.credentialmanager";
    private static final int XSPACE_USER_ID = 999;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        // Android allocates app UIDs in 100000-wide per-user ranges. Avoid depending on
        // hidden UserHandle APIs so the tiny module can be compiled against the public SDK.
        if ((Process.myUid() / 100000) != XSPACE_USER_ID) {
            return;
        }

        final Class<?> miuiScreen = XposedHelpers.findClass(
                "com.android.credentialmanager.getflow.GetMiCredentialComponentsKt",
                lpparam.classLoader);
        XposedBridge.hookAllMethods(miuiScreen, "GetMiCredentialScreen", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    final Object viewModel = param.args[0];
                    final Object getCredentialUiState = param.args[1];
                    final Object providerActivityLauncher = param.args[2];

                    final Object requestDisplayInfo = XposedHelpers.callMethod(
                            getCredentialUiState,
                            "getRequestDisplayInfo");
                    final String appName = String.valueOf(XposedHelpers.callMethod(
                            requestDisplayInfo,
                            "getAppName"));
                    if (!appName.contains("ChatGPT")) {
                        return;
                    }

                    final Object currentScreenState = XposedHelpers.callMethod(
                            getCredentialUiState,
                            "getCurrentScreenState");

                    final Object providerDisplayInfo = XposedHelpers.callMethod(
                            getCredentialUiState,
                            "getProviderDisplayInfo");
                    final List<?> credentialEntries = (List<?>) XposedHelpers.callMethod(
                            providerDisplayInfo,
                            "getCredentialEntryInfoList");
                    final List<?> authenticationEntries = (List<?>) XposedHelpers.callMethod(
                            providerDisplayInfo,
                            "getAuthenticationEntryList");
                    final Object remoteEntry = XposedHelpers.callMethod(
                            providerDisplayInfo,
                            "getRemoteEntry");

                    XposedBridge.log(
                            "HyperOsCredentialFix: ChatGPT state="
                                    + currentScreenState
                                    + " entries="
                                    + credentialEntries.size()
                                    + " auth="
                                    + authenticationEntries.size()
                                    + " remote="
                                    + (remoteEntry != null));

                    if (!credentialEntries.isEmpty()) {
                        return;
                    }

                    final Object candidateEntry;
                    final String candidateType;
                    if (!authenticationEntries.isEmpty()) {
                        candidateEntry = authenticationEntries.get(0);
                        candidateType = "authentication";
                    } else if (remoteEntry != null) {
                        candidateEntry = remoteEntry;
                        candidateType = "remote";
                    } else {
                        return;
                    }

                    XposedBridge.log(
                            "HyperOsCredentialFix: bridging " + candidateType + " entry for ChatGPT");

                    XposedHelpers.callStaticMethod(
                            viewModel.getClass(),
                            "getFlowOnEntrySelected$default",
                            viewModel,
                            candidateEntry,
                            null,
                            2,
                            null);
                    XposedHelpers.callMethod(
                            viewModel,
                            "launchProviderUi",
                            providerActivityLauncher);

                    // Skip the broken OEM PRIMARY_SELECTION branch for this frame. The
                    // provider result drives the normal view-model state machine afterwards.
                    param.setResult(null);
                } catch (Throwable error) {
                    XposedBridge.log(
                            "HyperOsCredentialFix: authentication-entry bridge failed; using OEM implementation");
                    XposedBridge.log(error);
                }
            }
        });

        XposedBridge.log("HyperOsCredentialFix: hook installed for user " + XSPACE_USER_ID);
    }
}
