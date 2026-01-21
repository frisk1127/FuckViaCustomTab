package via.fuckcustomtab.frisk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomTabHookGlobal implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        hookCustomTabsIntent(lpparam);
        hookStartActivity();
        hookViaTrampoline(lpparam);
    }

    private void hookCustomTabsIntent(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> cct = XposedHelpers.findClassIfExists(
                "androidx.browser.customtabs.CustomTabsIntent",
                lpparam.classLoader
        );
        if (cct != null) {
            XposedBridge.hookAllMethods(cct, "launchUrl", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    Context ctx = (Context) param.args[0];
                    Uri uri = (Uri) param.args[1];

                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    return null;
                }
            });
        }

        Class<?> cctSupport = XposedHelpers.findClassIfExists(
                "android.support.customtabs.CustomTabsIntent",
                lpparam.classLoader
        );
        if (cctSupport != null) {
            XposedBridge.hookAllMethods(cctSupport, "launchUrl", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    Context ctx = (Context) param.args[0];
                    Uri uri = (Uri) param.args[1];

                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    return null;
                }
            });
        }
    }

    private void hookStartActivity() {
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class,
                int.class,
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;

                        if (isCustomTabIntent(intent)) {
                            intent.setComponent((ComponentName) null);
                            intent.setAction(Intent.ACTION_VIEW);
                            stripCustomTabExtras(intent);
                            param.args[0] = intent;
                        }
                    }
                }
        );
    }

    private void hookViaTrampoline(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"mark.via".equals(lpparam.packageName) && !"mark.via.gp".equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "mark.via.Trampoline",
                    lpparam.classLoader,
                    "onCreate",
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Activity act = (Activity) param.thisObject;
                            Intent intent = act.getIntent();
                            if (intent == null) return;

                            Uri url = intent.getData();
                            boolean isCustomTab = intent.getBooleanExtra("CUSTOM_TAB", false)
                                    || hasCustomTabExtras(intent);

                            if (url != null && isCustomTab) {
                                XposedBridge.log("Trampoline opened via CustomTab. Redirecting to Shell.");

                                Intent newIntent = new Intent(intent);
                                newIntent.setComponent(new ComponentName(lpparam.packageName, "mark.via.Shell"));
                                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                newIntent.setData(url);

                                act.startActivity(newIntent);
                                act.finish();
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("Trampoline hook skipped (Class not found or method changed)");
        }
    }

    private boolean isCustomTabIntent(Intent intent) {
        ComponentName component = intent.getComponent();
        if (component != null) {
            String className = component.getClassName();
            if (className != null && className.contains("CustomTab")) {
                return true;
            }
        }

        Bundle extras = intent.getExtras();
        if (extras == null) return false;

        for (String key : extras.keySet()) {
            if (key.startsWith("androidx.browser.customtabs.extra.")
                    || key.startsWith("android.support.customtabs.extra.")) {
                return true;
            }
        }
        return false;
    }

    private void stripCustomTabExtras(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;

        for (String key : extras.keySet().toArray(new String[0])) {
            if (key.startsWith("androidx.browser.customtabs.extra.")
                    || key.startsWith("android.support.customtabs.extra.")) {
                intent.removeExtra(key);
            }
        }
    }

    private boolean hasCustomTabExtras(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return false;

        for (String key : extras.keySet()) {
            if (key.startsWith("androidx.browser.customtabs.extra.")
                    || key.startsWith("android.support.customtabs.extra.")) {
                return true;
            }
        }
        return false;
    }
}
