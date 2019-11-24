package uc.benkkstudio.bswebkit;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Proxy;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.Log;
import android.webkit.WebView;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import cz.msebera.android.httpclient.HttpHost;
import info.guardianproject.netcipher.proxy.OrbotHelper;

public class BSWebkit {

        private final static int REQUEST_CODE = 0;

        private final static String TAG = "OrbotHelpher";

    private BSWebkit() {
        // this is a utility class with only static methods
    }

        public static boolean setProxy(String appClass, Context ctx, WebView wView, String host, int port) throws Exception {

        setSystemProperties(host, port);

        boolean worked = false;

        if (Build.VERSION.SDK_INT < 13) {
//            worked = setWebkitProxyGingerbread(ctx, host, port);
            setProxyUpToHC(wView, host, port);
        } else if (Build.VERSION.SDK_INT < 19) {
            worked = setWebkitProxyICS(ctx, host, port);
        } else if (Build.VERSION.SDK_INT < 20) {
            worked = setKitKatProxy(appClass, ctx, host, port);

            if (!worked) //some kitkat's still use ICS browser component (like Cyanogen 11)
                worked = setWebkitProxyICS(ctx, host, port);

        } else if (Build.VERSION.SDK_INT >= 21) {
            worked = setWebkitProxyLollipop(ctx, host, port);

        }

        return worked;
    }

        private static void setSystemProperties(String host, int port) {

        System.setProperty("proxyHost", host);
        System.setProperty("proxyPort", Integer.toString(port));

        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", Integer.toString(port));

        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", Integer.toString(port));

        System.setProperty("socks.proxyHost", host);
        System.setProperty("socks.proxyPort", Integer.toString(OrbotHelper.DEFAULT_PROXY_SOCKS_PORT));

        System.setProperty("socksProxyHost", host);
        System.setProperty("socksProxyPort", Integer.toString(OrbotHelper.DEFAULT_PROXY_SOCKS_PORT));
    }

        private static void resetSystemProperties() {

        System.setProperty("proxyHost", "");
        System.setProperty("proxyPort", "");

        System.setProperty("http.proxyHost", "");
        System.setProperty("http.proxyPort", "");

        System.setProperty("https.proxyHost", "");
        System.setProperty("https.proxyPort", "");

        System.setProperty("socks.proxyHost", "");
        System.setProperty("socks.proxyPort", Integer.toString(OrbotHelper.DEFAULT_PROXY_SOCKS_PORT));

        System.setProperty("socksProxyHost", "");
        System.setProperty("socksProxyPort", Integer.toString(OrbotHelper.DEFAULT_PROXY_SOCKS_PORT));
    }

        /**
         * Override WebKit Proxy settings
         *
         * @param ctx  Android ApplicationContext
         * @param host
         * @param port
         * @return true if Proxy was successfully set
         */
        private static boolean setWebkitProxyGingerbread(Context ctx, String host, int port)
            throws Exception {

        boolean ret = false;

        Object requestQueueObject = getRequestQueue(ctx);
        if (requestQueueObject != null) {
            // Create Proxy config object and set it into request Q
            HttpHost httpHost = new HttpHost(host, port, "http");
            setDeclaredField(requestQueueObject, "mProxyHost", httpHost);
            return true;
        }
        return false;

    }


        /**
         * Set Proxy for Android 3.2 and below.
         */
        @SuppressWarnings("all")
        private static boolean setProxyUpToHC(WebView webview, String host, int port) {
        Log.d(TAG, "Setting proxy with <= 3.2 API.");

        HttpHost proxyServer = new HttpHost(host, port);
        // Getting network
        Class networkClass = null;
        Object network = null;
        try {
            networkClass = Class.forName("android.webkit.Network");
            if (networkClass == null) {
                Log.e(TAG, "failed to get class for android.webkit.Network");
                return false;
            }
            Method getInstanceMethod = networkClass.getMethod("getInstance", Context.class);
            if (getInstanceMethod == null) {
                Log.e(TAG, "failed to get getInstance method");
            }
            network = getInstanceMethod.invoke(networkClass, new Object[]{webview.getContext()});
        } catch (Exception ex) {
            Log.e(TAG, "error getting network: " + ex);
            return false;
        }
        if (network == null) {
            Log.e(TAG, "error getting network: network is null");
            return false;
        }
        Object requestQueue = null;
        try {
            Field requestQueueField = networkClass
                    .getDeclaredField("mRequestQueue");
            requestQueue = getFieldValueSafely(requestQueueField, network);
        } catch (Exception ex) {
            Log.e(TAG, "error getting field value");
            return false;
        }
        if (requestQueue == null) {
            Log.e(TAG, "Request queue is null");
            return false;
        }
        Field proxyHostField = null;
        try {
            Class requestQueueClass = Class.forName("android.net.http.RequestQueue");
            proxyHostField = requestQueueClass
                    .getDeclaredField("mProxyHost");
        } catch (Exception ex) {
            Log.e(TAG, "error getting proxy host field");
            return false;
        }

        boolean temp = proxyHostField.isAccessible();
        try {
            proxyHostField.setAccessible(true);
            proxyHostField.set(requestQueue, proxyServer);
        } catch (Exception ex) {
            Log.e(TAG, "error setting proxy host");
        } finally {
            proxyHostField.setAccessible(temp);
        }

        Log.d(TAG, "Setting proxy with <= 3.2 API successful!");
        return true;
    }


        private static Object getFieldValueSafely(Field field, Object classInstance) throws IllegalAccessException {
        boolean oldAccessibleValue = field.isAccessible();
        field.setAccessible(true);
        Object result = field.get(classInstance);
        field.setAccessible(oldAccessibleValue);
        return result;
    }

        private static boolean setWebkitProxyICS(Context ctx, String host, int port) {

        // PSIPHON: added support for Android 4.x WebView proxy
        try {
            Class webViewCoreClass = Class.forName("android.webkit.WebViewCore");

            Class proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
            if (webViewCoreClass != null && proxyPropertiesClass != null) {
                Method m = webViewCoreClass.getDeclaredMethod("sendStaticMessage", Integer.TYPE,
                        Object.class);
                Constructor c = proxyPropertiesClass.getConstructor(String.class, Integer.TYPE,
                        String.class);

                if (m != null && c != null) {
                    m.setAccessible(true);
                    c.setAccessible(true);
                    Object properties = c.newInstance(host, port, null);

                    // android.webkit.WebViewCore.EventHub.PROXY_CHANGED = 193;
                    m.invoke(null, 193, properties);


                    return true;
                }


            }
        } catch (Exception e) {
            Log.e("ProxySettings",
                    "Exception setting WebKit proxy through android.net.ProxyProperties: "
                            + e.toString());
        } catch (Error e) {
            Log.e("ProxySettings",
                    "Exception setting WebKit proxy through android.webkit.Network: "
                            + e.toString());
        }

        return false;

    }

        @TargetApi(19)
        public static boolean resetKitKatProxy(String appClass, Context appContext) {

        return setKitKatProxy(appClass, appContext, null, 0);
    }

        @TargetApi(19)
        private static boolean setKitKatProxy(String appClass, Context appContext, String host, int port) {
        //Context appContext = webView.getContext().getApplicationContext();

        try {
            Class applictionCls = Class.forName(appClass);
            Field loadedApkField = applictionCls.getField("mLoadedApk");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(appContext);
            Class loadedApkCls = Class.forName("android.app.LoadedApk");
            Field receiversField = loadedApkCls.getDeclaredField("mReceivers");
            receiversField.setAccessible(true);
            ArrayMap receivers = (ArrayMap) receiversField.get(loadedApk);
            for (Object receiverMap : receivers.values()) {
                for (Object rec : ((ArrayMap) receiverMap).keySet()) {
                    Class clazz = rec.getClass();
                    if (clazz.getName().contains("ProxyChangeListener")) {
                        Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                        Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);

                        if (host != null) {
                            /*********** optional, may be need in future *************/
                            final String CLASS_NAME = "android.net.ProxyProperties";
                            Class cls = Class.forName(CLASS_NAME);
                            Constructor constructor = cls.getConstructor(String.class, Integer.TYPE, String.class);
                            constructor.setAccessible(true);
                            Object proxyProperties = constructor.newInstance(host, port, null);
                            intent.putExtra("proxy", (Parcelable) proxyProperties);
                            /*********** optional, may be need in future *************/
                        }

                        onReceiveMethod.invoke(rec, appContext, intent);
                    }
                }
            }
            return true;
        } catch (ClassNotFoundException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            Log.v(TAG, e.getMessage());
            Log.v(TAG, exceptionAsString);
        } catch (NoSuchFieldException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            Log.v(TAG, e.getMessage());
            Log.v(TAG, exceptionAsString);
        } catch (IllegalAccessException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            Log.v(TAG, e.getMessage());
            Log.v(TAG, exceptionAsString);
        } catch (IllegalArgumentException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            Log.v(TAG, e.getMessage());
            Log.v(TAG, exceptionAsString);
        } catch (NoSuchMethodException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            Log.v(TAG, e.getMessage());
            Log.v(TAG, exceptionAsString);
        } catch (InvocationTargetException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            Log.v(TAG, e.getMessage());
            Log.v(TAG, exceptionAsString);
        } catch (InstantiationException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            Log.v(TAG, e.getMessage());
            Log.v(TAG, exceptionAsString);
        }
        return false;
    }

        @TargetApi(21)
        public static boolean resetLollipopProxy(String appClass, Context appContext) {

        return setWebkitProxyLollipop(appContext, null, 0);
    }

        // http://stackanswers.com/questions/25272393/android-webview-set-proxy-programmatically-on-android-l
        @TargetApi(21) // for android.util.ArrayMap methods
        @SuppressWarnings("rawtypes")
        private static boolean setWebkitProxyLollipop(Context appContext, String host, int port) {

        try {
            Class applictionClass = Class.forName("android.app.Application");
            Field mLoadedApkField = applictionClass.getDeclaredField("mLoadedApk");
            mLoadedApkField.setAccessible(true);
            Object mloadedApk = mLoadedApkField.get(appContext);
            Class loadedApkClass = Class.forName("android.app.LoadedApk");
            Field mReceiversField = loadedApkClass.getDeclaredField("mReceivers");
            mReceiversField.setAccessible(true);
            ArrayMap receivers = (ArrayMap) mReceiversField.get(mloadedApk);
            for (Object receiverMap : receivers.values()) {
                for (Object receiver : ((ArrayMap) receiverMap).keySet()) {
                    Class clazz = receiver.getClass();
                    if (clazz.getName().contains("ProxyChangeListener")) {
                        Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                        Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);
                        Object proxyInfo = null;
                        if (host != null) {
                            final String CLASS_NAME = "android.net.ProxyInfo";
                            Class cls = Class.forName(CLASS_NAME);
                            Method buildDirectProxyMethod = cls.getMethod("buildDirectProxy", String.class, Integer.TYPE);
                            proxyInfo = buildDirectProxyMethod.invoke(cls, host, port);
                        }
                        intent.putExtra("proxy", (Parcelable) proxyInfo);
                        onReceiveMethod.invoke(receiver, appContext, intent);
                    }
                }
            }
            return true;
        } catch (ClassNotFoundException e) {
            Log.d("ProxySettings", "Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        } catch (NoSuchFieldException e) {
            Log.d("ProxySettings", "Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        } catch (IllegalAccessException e) {
            Log.d("ProxySettings", "Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        } catch (NoSuchMethodException e) {
            Log.d("ProxySettings", "Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        } catch (InvocationTargetException e) {
            Log.d("ProxySettings", "Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        }
        return false;
    }

        private static boolean sendProxyChangedIntent(Context ctx, String host, int port) {

        try {
            Class proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
            if (proxyPropertiesClass != null) {
                Constructor c = proxyPropertiesClass.getConstructor(String.class, Integer.TYPE,
                        String.class);

                if (c != null) {
                    c.setAccessible(true);
                    Object properties = c.newInstance(host, port, null);

                    Intent intent = new Intent(android.net.Proxy.PROXY_CHANGE_ACTION);
                    intent.putExtra("proxy", (Parcelable) properties);
                    ctx.sendBroadcast(intent);

                }

            }
        } catch (Exception e) {
            Log.e("ProxySettings",
                    "Exception sending Intent ", e);
        } catch (Error e) {
            Log.e("ProxySettings",
                    "Exception sending Intent ", e);
        }

        return false;

    }

        public static boolean resetProxy(String appClass, Context ctx) throws Exception {

        resetSystemProperties();

        if (Build.VERSION.SDK_INT < 14) {
            return resetProxyForGingerBread(ctx);
        } else if (Build.VERSION.SDK_INT < 19) {
            return resetProxyForICS();
        } else if (Build.VERSION.SDK_INT < 20) {
            return resetKitKatProxy(appClass, ctx);
        } else if (Build.VERSION.SDK_INT >= 21) {
            return resetLollipopProxy(appClass, ctx);
        }
        return false;
    }

        private static boolean resetProxyForICS() throws Exception {
        try {
            Class webViewCoreClass = Class.forName("android.webkit.WebViewCore");
            Class proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
            if (webViewCoreClass != null && proxyPropertiesClass != null) {
                Method m = webViewCoreClass.getDeclaredMethod("sendStaticMessage", Integer.TYPE,
                        Object.class);

                if (m != null) {
                    m.setAccessible(true);

                    // android.webkit.WebViewCore.EventHub.PROXY_CHANGED = 193;
                    m.invoke(null, 193, null);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e("ProxySettings",
                    "Exception setting WebKit proxy through android.net.ProxyProperties: "
                            + e.toString());
            throw e;
        } catch (Error e) {
            Log.e("ProxySettings",
                    "Exception setting WebKit proxy through android.webkit.Network: "
                            + e.toString());
            throw e;
        }
    }

        private static boolean resetProxyForGingerBread(Context ctx) throws Exception {
        Object requestQueueObject = getRequestQueue(ctx);
        if (requestQueueObject != null) {
            setDeclaredField(requestQueueObject, "mProxyHost", null);
            return true;
        }
        return false;
    }

        @Nullable
        public static Object getRequestQueue(Context ctx) throws Exception {
        Object ret = null;
        Class networkClass = Class.forName("android.webkit.Network");
        if (networkClass != null) {
            Object networkObj = invokeMethod(networkClass, "getInstance", new Object[]{
                    ctx
            }, Context.class);
            if (networkObj != null) {
                ret = getDeclaredField(networkObj, "mRequestQueue");
            }
        }
        return ret;
    }

        private static Object getDeclaredField(Object obj, String name)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

        private static void setDeclaredField(Object obj, String name, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

        private static Object invokeMethod(Object object, String methodName, Object[] params,
            Class... types) throws Exception {
        Object out = null;
        Class c = object instanceof Class ? (Class) object : object.getClass();
        if (types != null) {
            Method method = c.getMethod(methodName, types);
            out = method.invoke(object, params);
        } else {
            Method method = c.getMethod(methodName);
            out = method.invoke(object);
        }
        return out;
    }

        public static Socket getSocket(Context context, String proxyHost, int proxyPort)
            throws IOException {
        Socket sock = new Socket();

        sock.connect(new InetSocketAddress(proxyHost, proxyPort), 10000);

        return sock;
    }

        public static Socket getSocket(Context context) throws IOException {
        return getSocket(context, OrbotHelper.DEFAULT_PROXY_HOST, OrbotHelper.DEFAULT_PROXY_SOCKS_PORT);

    }

        @Nullable
        public static AlertDialog initOrbot(Activity activity,
                                            CharSequence stringTitle,
                                            CharSequence stringMessage,
                                            CharSequence stringButtonYes,
                                            CharSequence stringButtonNo,
                                            CharSequence stringDesiredBarcodeFormats) {
        Intent intentScan = new Intent("org.torproject.android.START_TOR");
        intentScan.addCategory(Intent.CATEGORY_DEFAULT);

        try {
            activity.startActivityForResult(intentScan, REQUEST_CODE);
            return null;
        } catch (ActivityNotFoundException e) {
            return showDownloadDialog(activity, stringTitle, stringMessage, stringButtonYes,
                    stringButtonNo);
        }
    }

        private static AlertDialog showDownloadDialog(final Activity activity,
        CharSequence stringTitle,
        CharSequence stringMessage,
        CharSequence stringButtonYes,
        CharSequence stringButtonNo) {
        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(activity);
        downloadDialog.setTitle(stringTitle);
        downloadDialog.setMessage(stringMessage);
        downloadDialog.setPositiveButton(stringButtonYes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Uri uri = Uri.parse("market://search?q=pname:org.torproject.android");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                activity.startActivity(intent);
            }
        });
        downloadDialog.setNegativeButton(stringButtonNo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        return downloadDialog.show();
    }
}
