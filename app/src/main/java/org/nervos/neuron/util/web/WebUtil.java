package org.nervos.neuron.util.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nervos.neuron.event.AppCollectEvent;
import org.nervos.neuron.item.AppItem;
import org.nervos.neuron.item.ChainItem;
import org.nervos.neuron.item.TokenItem;
import org.nervos.neuron.item.WalletItem;
import org.nervos.neuron.service.EthRpcService;
import org.nervos.neuron.service.NervosHttpService;
import org.nervos.neuron.service.NervosRpcService;
import org.nervos.neuron.util.LogUtil;
import org.nervos.neuron.util.db.DBAppUtil;
import org.nervos.neuron.util.db.DBChainUtil;
import org.nervos.neuron.util.db.DBWalletUtil;
import org.nervos.web3j.protocol.core.methods.response.EthMetaData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WebUtil {

    private static ChainItem chainItem;

    /**
     * get manifest path from html link tag
     * @param webView
     * @param url    the web url from the third part
     */
    public static void getHtmlManifest(WebView webView, String url) {
        new Thread(){
            @Override
            public void run() {
                super.run();
                try {
                    Document doc = Jsoup.connect(url).get();
                    Elements elements = doc.getElementsByTag("link");
                    for(Element element: elements) {
                        if ("manifest".equals(element.attr("ref"))) {
                            getHttpManifest(webView, url, element.attr("href"));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


    /**
     * get manifest.json from manifest path which contains host and path
     * @param webView
     * @param url           the web url from the third part
     * @param path          manifest path
     */
    private static void getHttpManifest(WebView webView, String url, String path) {
        URI uri = URI.create(url);
        String manifestUrl = uri.getScheme() + "://" + uri.getAuthority() + path;
        Request request = new Request.Builder().url(manifestUrl).build();
        Call call = NervosHttpService.getHttpClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String result = response.body().string();
                    LogUtil.d("manifest result: " + result);
                    chainItem = new Gson().fromJson(result, ChainItem.class);
                    if (chainItem.chainId >= 0 && !TextUtils.isEmpty(chainItem.httpProvider)) {
                        webView.loadUrl(getInjectNervosWeb3());
                        getMetaData(webView.getContext(), chainItem.httpProvider);

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    /**
     * get metadata from cita blockchain
     * @param context
     * @param httpProvider
     */
    private static void getMetaData(Context context, String httpProvider) {
        NervosRpcService.init(context, httpProvider);
        EthMetaData.EthMetaDataResult ethMetaData = NervosRpcService.getMetaData().getEthMetaDataResult();
        if (ethMetaData != null && ethMetaData.chainId == chainItem.chainId) {
            chainItem.tokenName = ethMetaData.tokenName;
            chainItem.tokenSymbol = ethMetaData.tokenSymbol;
            chainItem.tokenAvatar = ethMetaData.tokenAvatar;
            DBChainUtil.saveChain(context, chainItem);
            if (!TextUtils.isEmpty(chainItem.tokenName)) {
                TokenItem tokenItem = new TokenItem(chainItem);
                DBWalletUtil.addTokenToAllWallet(context, tokenItem);
            }
        }
    }


    public static boolean isCollectApp(Context context) {
        if (chainItem != null && !TextUtils.isEmpty(chainItem.entry)) {
            return DBAppUtil.findApp(context, chainItem.entry);
        }
        return false;
    }

    public static void collectApp(Context context) {
        if (chainItem != null && !TextUtils.isEmpty(chainItem.entry)) {
            AppItem appItem = new AppItem(chainItem.entry,
                    chainItem.icon, chainItem.name, chainItem.provider);
            DBAppUtil.saveDbApp(context, appItem);
            EventBus.getDefault().post(new AppCollectEvent(true, appItem));
            Toast.makeText(context, "收藏成功", Toast.LENGTH_SHORT).show();
        }
    }

    public static void cancelCollectApp(Context context) {
        if (chainItem != null && !TextUtils.isEmpty(chainItem.entry)) {
            DBAppUtil.deleteApp(context, chainItem.entry);
            AppItem appItem = new AppItem(chainItem.entry,
                    chainItem.icon, chainItem.name, chainItem.provider);
            EventBus.getDefault().post(new AppCollectEvent(false, appItem));
            Toast.makeText(context, "取消收藏", Toast.LENGTH_SHORT).show();
        }
    }

    public static ChainItem getChainItem() {
        return chainItem;
    }

    /**
     * read String content of rejected JavaScript file from assets
     * @param fileName    the name of JavaScript file
     * @return  the content of JavaScript file
     */
    private static String getInjectedJsFile(Context context, String fileName) {
        AssetManager am = context.getAssets();
        try {
            InputStream in = am.open(fileName);
            byte buff[] = new byte[1024];
            ByteArrayOutputStream fromFile = new ByteArrayOutputStream();
            do {
                int num = in.read(buff);
                if (num <= 0) break;
                fromFile.write(buff, 0, num);
            } while (true);
            return fromFile.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

//    public static boolean checkWeb3() {
//
//    }

    public static String getInjectEthWeb3(Context context) {
        WalletItem walletItem = DBWalletUtil.getCurrentWallet(context);
        return "javascript: web3.setProvider(new Web3.providers.HttpProvider('"
                + EthRpcService.ETH_NODE_IP + "')); web3.currentProvider.isMetaMask = true; web3.eth.accounts[0] = '"
                + walletItem.address + "'";
    }

    private static String getInjectNervosWeb3() {
        return "javascript: web3.setProvider('" + chainItem.httpProvider + "'); web3.currentProvider.isMetaMask = true;";
    }

    public static String getInjectTransactionJs() {
        return "javascript: web3.eth.sendTransaction = function(tx) {appHybrid.showTransaction(JSON.stringify(tx));console.log(JSON.stringify(tx));}";
    }

    public static String getInjectSignJs() {
        return "javascript: web3.eth.signTransaction = function(tx) {appHybrid.signTransaction(JSON.stringify(tx));console.log(JSON.stringify(tx));}";
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void initWebSettings(WebSettings webSettings) {
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setAppCacheEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setLoadsImagesAutomatically(true);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setBuiltInZoomControls(false);

        WebView.setWebContentsDebuggingEnabled(true);
    }


    public static boolean isHttpUrl(String urlString) {
        URI uri = null;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return false;
        }

        if(uri.getHost() == null){
            return false;
        }
        if(uri.getScheme().equalsIgnoreCase("http")
                || uri.getScheme().equalsIgnoreCase("https")){
            return true;
        }
        return false;
    }

}