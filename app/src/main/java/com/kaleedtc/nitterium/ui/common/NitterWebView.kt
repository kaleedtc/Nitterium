package com.kaleedtc.nitterium.ui.common

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.kaleedtc.nitterium.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.net.URI
import java.net.URL

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NitterWebView(
    url: String,
    isTrueBlack: Boolean,
    isSiteHeaderEnabled: Boolean,
    isBlockDirectXEnabled: Boolean,
    darkTheme: Boolean,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, WebView) -> Unit,
    onPageError: (Int, String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    isProfileView: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    // 0. State for Image Viewer
    var clickedImageState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // 0.1 State for Full Screen Video
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val fullScreenMode = LocalFullScreenMode.current
    var savedScrollX by rememberSaveable { mutableIntStateOf(0) }
    var savedScrollY by rememberSaveable { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            customView = null
        }
    }

    // 1. Capture current theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    // 2. Define CSS generation logic
    fun generateThemeCss(
        bg: Color,
        surface: Color,
        text: Color,
        textVariant: Color,
        border: Color,
        primary: Color
    ): String {
        val bgHex = "#%06X".format(0xFFFFFF and bg.toArgb())
        val surfaceHex = "#%06X".format(0xFFFFFF and surface.toArgb())
        val textHex = "#%06X".format(0xFFFFFF and text.toArgb())
        val textVariantHex = "#%06X".format(0xFFFFFF and textVariant.toArgb())
        val borderHex = "#%06X".format(0xFFFFFF and border.toArgb())
        val primaryHex = "#%06X".format(0xFFFFFF and primary.toArgb())

        return """
            :root {
                --bg-color: $bgHex !important;
                --bg-color-alt: $surfaceHex !important;
                --fg-color: $textHex !important;
                --panel-bg: $surfaceHex !important;
                --border-color: $borderHex !important;
            }
            body { 
                background-color: $bgHex !important; 
                color: $textHex !important;
            }
            .timeline-item, .reply, .card, .card-container, .quote, .quote-big,
            .profile-card, .profile-card-info, .profile-card-avatar,
            .tab-item, .fullname, .replying-to, .show-more, .top-ref,
            .profile-joindate, .profile-website, .profile-card-extra, .profile-card-extra-links,
            .profile-stat-header, .profile-stat-num,
            .photo-rail-card, .header, .tabs,
            .timeline-item:hover, .timeline-item:focus, .timeline-item:active,
            .reply:hover, .reply:focus, .reply:active,
            .card:hover, .card:focus, .card:active,
            .show-more:hover, .show-more:active, .show-more:focus,
            .more-replies, .more-replies:hover {
                border-color: $borderHex !important;
                background-color: $surfaceHex !important;
                color: $textHex !important;
            }
            .show-more a, .more-replies a {
                color: $primaryHex !important;
                background-color: transparent !important;
                font-weight: bold !important;
                text-decoration: none !important;
                display: block !important;
                width: 100% !important;
                padding: 10px 0 !important;
            }
            .username, .profile-card-username {
                color: $textVariantHex !important;
            }
            .tab-item a {
                color: $textVariantHex !important;
            }
            .main-thread { 
                background-color: $bgHex !important; 
                border-color: $borderHex !important; 
            }
            
            /* Site Header Theming */
            nav, .site-header {
                background-color: $surfaceHex !important;
                border-bottom: 1px solid $borderHex !important;
                color: $textHex !important;
            }
            .site-logo, .site-name {
                color: $textHex !important;
            }
            .search-header {
                background-color: $surfaceHex !important;
            }
            /* Search Field Theming */
            input[type="text"], .search-field, form[action*="search"] input {
                background-color: $bgHex !important;
                color: $textHex !important;
                border: 1px solid $borderHex !important;
            }
            input[type="text"]::placeholder {
                color: $textHex !important;
                opacity: 0.7;
            }
            button, .search-btn {
                background-color: $surfaceHex !important;
                color: $textHex !important;
                border: 1px solid $borderHex !important;
            }
            a, a:visited { color: #1d9bf0; } /* Twitter Blue for links is usually safe */
            
            /* Force LTR for headers and stats regardless of tweet content direction */
            .tweet-header, .tweet-stats, .tweet-name-row, .replying-to {
                direction: ltr !important;
                text-align: left !important;
            }
        """.trimIndent()
    }

    val currentThemeCss = generateThemeCss(
        backgroundColor,
        surfaceColor,
        onSurfaceColor,
        onSurfaceVariantColor,
        borderColor,
        primaryColor
    )

    // Additional True Black Override (if enabled and in dark mode)
    val trueBlackCss = if (isTrueBlack && darkTheme) {
        """
        :root {
            --bg-color: #000000 !important;
            --bg-color-alt: #000000 !important;
            --panel-bg: #000000 !important;
        }
        body, .timeline-item, .reply, .card, .main-thread, .show-more, .more-replies, .header, .tabs { background-color: #000000 !important; }
        """
    } else ""

    fun isProfileUrl(currentUrl: String?): Boolean {
        if (currentUrl.isNullOrEmpty()) return false
        return try {
            val uri = URI(currentUrl)
            val path = uri.path.trim('/')
            if (path.isEmpty()) return false

            val segments = path.split('/')
            val firstSegment = segments[0].lowercase()
            val reservedWords = setOf(
                "search", "settings", "about", "pic", "status",
                "logo.png", "favicon.ico", "robots.txt", "i", "explore"
            )

            !reservedWords.contains(firstSegment)
        } catch (_: Exception) {
            false
        }
    }

    fun getSiteHeaderCss(currentUrl: String?): String {
        if (isSiteHeaderEnabled) return ""

        // Prioritize actual URL check. If the URL is a global-reserved page (like /search),
        // we always use strict CSS to hide the search bar, even if the screen context is "Profile".
        val isProfile = if (currentUrl.isNullOrEmpty()) isProfileView else isProfileUrl(currentUrl)

        return if (isProfile) {
            "nav, .site-header, .header, .search-header { display: none !important; }"
        } else {
            "nav, .site-header, .header, .search-header, .timeline-header, .search-bar, form[action*=\"search\"], .search-field, .search-panel { display: none !important; }"
        }
    }

    fun getJsInjection(currentUrl: String?): String {
        val headerCss = getSiteHeaderCss(currentUrl)
        return """
            javascript:(function() {
                function updateStyle(id, css) {
                    var style = document.getElementById(id);
                    if (!style) {
                        style = document.createElement('style');
                        style.id = id;
                        document.head.appendChild(style);
                    }
                    if (style.innerHTML !== css) {
                        style.innerHTML = css;
                    }
                }

                updateStyle('app-theme-override', `$currentThemeCss`);
                updateStyle('true-black-override', `$trueBlackCss`);
                updateStyle('header-override', `$headerCss`);

                updateStyle('general-cleanup', `
                    body, .container, .timeline-container { padding-top: 0 !important; margin-top: 0 !important; }
                    .tabs { margin-top: 0 !important; padding-top: 0 !important; }
                    .timeline { margin-top: 0 !important; border-top: none !important; border-radius: 0 !important; }
                `);

                function fixDirection() {
                    var contentSelectors = [
                        '.tweet-content', '.quote-text', '.card-title', '.card-description',
                        '.reply-content', '.reply-text', '.tweet-text', '.comment-text'
                    ].join(',');

                    document.querySelectorAll(contentSelectors).forEach(function(el) {
                        el.setAttribute('dir', 'auto');
                        el.style.setProperty('text-align', 'start', 'important');

                        // Do not force display/width in grid views, as it breaks the grid layout
                        if (el.closest('.timeline-grid') || el.closest('.photo-rail-grid')) {
                            return;
                        }

                        if (window.getComputedStyle(el).display !== 'none') {
                            el.style.setProperty('display', 'inline-block', 'important');
                            el.style.setProperty('width', '100%', 'important');
                        }
                    });
                }

                if ($darkTheme) {
                    document.body.classList.add('dark');
                    document.documentElement.style.colorScheme = 'dark';
                } else {
                    document.body.classList.remove('dark', 'h-dark');
                    document.documentElement.style.colorScheme = 'light';
                }

                if (!window.nitterImageListenerAttached) {
                    document.addEventListener('click', function(e) {
                        var target = e.target;
                        while (target && target.tagName !== 'A') {
                            target = target.parentElement;
                        }
                        if (target && target.tagName === 'A') {
                            var isImage = /\.(jpg|jpeg|png|gif|webp)($|\?|#)/i.test(target.href) || 
                                          target.href.includes('/pic/') || 
                                          target.closest('.profile-banner') !== null || 
                                          target.closest('.profile-card-avatar') !== null;
                            if (isImage) {
                                e.preventDefault();
                                e.stopPropagation();
                                
                                var container = target.closest('.attachments') || target.closest('.gallery-row');
                                var imageLinks = [];
                                var clickedIndex = 0;
                                
                                if (container) {
                                    var links = Array.from(container.querySelectorAll('a'));
                                    links.forEach(function(link) {
                                        var linkIsImage = /\.(jpg|jpeg|png|gif|webp)($|\?|#)/i.test(link.href) || 
                                                          link.href.includes('/pic/') || 
                                                          link.closest('.profile-banner') !== null || 
                                                          link.closest('.profile-card-avatar') !== null;
                                        if (linkIsImage) {
                                            imageLinks.push(link.href);
                                            if (link.href === target.href) {
                                                clickedIndex = imageLinks.length - 1;
                                            }
                                        }
                                    });
                                } else {
                                    imageLinks.push(target.href);
                                }
                                NitterAndroid.openGallery(JSON.stringify(imageLinks), clickedIndex);
                            }
                        }
                    }, true);
                    window.nitterImageListenerAttached = true;
                }

                fixDirection();
                new MutationObserver(fixDirection).observe(document.body, { childList: true, subtree: true });
            })()
        """.trimIndent()
    }

    val nitterThemeCookieValue = remember(darkTheme, isTrueBlack) {
        when {
            darkTheme && isTrueBlack -> "Twitter Black"
            darkTheme -> "Twitter Dark"
            else -> "Twitter"
        }
    }

    val rendererGoneError = stringResource(R.string.renderer_gone_error)
    val latestJsInjection by rememberUpdatedState { url: String? -> getJsInjection(url) }
    val latestOnPageStarted by rememberUpdatedState(onPageStarted)
    val latestOnPageFinished by rememberUpdatedState(onPageFinished)
    val latestOnPageError by rememberUpdatedState(onPageError)

    // Use an array to pass the boolean reference safely to the AndroidView factory closure
    val blockDirectXState = remember { BooleanArray(1) }
    blockDirectXState[0] = isBlockDirectXEnabled

    Box(modifier = modifier.fillMaxSize()) {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        val webViewStateBundle = rememberSaveable(
            saver = Saver(
            save = {
                val bundle = Bundle()
                webViewRef?.saveState(bundle)
                bundle
            },
            restore = { it }
        )) { Bundle() }

        val lifecycleOwner = LocalLifecycleOwner.current

        // Re-inject theme when it changes to ensure it's always up to date
        // even without a page reload (e.g. when switching system theme).
        LaunchedEffect(currentThemeCss, trueBlackCss, darkTheme) {
            webViewRef?.let { wv ->
                wv.evaluateJavascript(latestJsInjection(wv.url), null)
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                    Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                    Lifecycle.Event.ON_DESTROY -> webViewRef?.destroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                webViewRef?.let { wv ->
                    wv.removeJavascriptInterface("NitterAndroid")
                    wv.removeJavascriptInterface("AndroidSearch")
                    wv.removeJavascriptInterface("AndroidProfile")
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
        }

        LaunchedEffect(url, webViewRef) {
            val wv = webViewRef
            if (wv != null && url.isNotEmpty()) {
                val currentUrl = wv.url
                // Only load if the current URL is well-defined and different from the target.
                // If it is null or about:blank, it means the WebView is either starting fresh 
                // or currently restoring its state, both of which are handled by the factory block.
                if (currentUrl != null && currentUrl != "about:blank") {
                    val normalizedCurrent = currentUrl.trimEnd('/')
                    val normalizedTarget = url.trimEnd('/')
                    if (normalizedCurrent != normalizedTarget) {
                        wv.loadUrl(url)
                    }
                }
            }
        }

        AndroidView(
            factory = { context ->
                val swipeRefreshLayout = SwipeRefreshLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setOnRefreshListener { onRefresh() }
                    setBackgroundColor(backgroundColor.toArgb())
                    setProgressBackgroundColorSchemeColor(surfaceColor.toArgb())
                    setColorSchemeColors(primaryColor.toArgb())
                }

                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                    }

                    addJavascriptInterface(NitterInterface { urls, index ->
                        clickedImageState = Pair(urls, index)
                    }, "NitterAndroid")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            view?.alpha = 0f
                            url?.let { latestOnPageStarted(it) }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (view != null && url != null) {
                                latestOnPageFinished(url, view)
                            }
                            view?.evaluateJavascript(latestJsInjection(url)) {
                                view.alpha = 1f
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: android.webkit.RenderProcessGoneDetail?
                        ): Boolean {
                            // Handle the renderer crash gracefully
                            if (view != null) {
                                latestOnPageError(-1, rendererGoneError)
                            }
                            // Returning true prevents the app from crashing.
                            // The UI will show the error screen based on latestOnPageError call.
                            return true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                latestOnPageError(
                                    error?.errorCode ?: 0,
                                    error?.description.toString()
                                )
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            if (blockDirectXState[0]) {
                                val requestHost = request?.url?.host?.lowercase()
                                if (requestHost != null) {
                                    val isTwitterDomain =
                                        requestHost == "twitter.com" || requestHost.endsWith(".twitter.com") ||
                                                requestHost == "x.com" || requestHost.endsWith(".x.com") ||
                                                requestHost == "twimg.com" || requestHost.endsWith(".twimg.com")

                                    if (isTwitterDomain) {
                                        val emptyStream = java.io.ByteArrayInputStream(ByteArray(0))
                                        val headers = mapOf(
                                            "Cache-Control" to "no-store, no-cache",
                                            "Pragma" to "no-cache"
                                        )
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            403,
                                            "Blocked",
                                            headers,
                                            emptyStream
                                        )
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val requestUrl = request?.url ?: return false
                            return try {
                                val currentHost = URL(view?.url ?: url).host
                                val requestHost = requestUrl.host

                                val isInternalHost = requestHost == currentHost ||
                                        (requestHost != null && currentHost != null &&
                                                requestHost.endsWith(".$currentHost"))

                                val urlString = requestUrl.toString().lowercase()
                                val isImageUrl = urlString.contains(".jpg") ||
                                        urlString.contains(".jpeg") ||
                                        urlString.contains(".png") ||
                                        urlString.contains(".gif") ||
                                        urlString.contains(".webp") ||
                                        requestUrl.path?.contains("/pic/") == true ||
                                        requestHost?.contains("pbs.twimg.com") == true

                                if (!isInternalHost && !isImageUrl) {
                                    val intent = Intent(Intent.ACTION_VIEW, requestUrl)
                                    context.startActivity(intent)
                                    true
                                } else {
                                    false
                                }
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            savedScrollX = this@apply.scrollX
                            savedScrollY = this@apply.scrollY
                            customView = view
                            customViewCallback = callback
                            fullScreenMode.value = true
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                            customViewCallback = null
                            fullScreenMode.value = false

                            // Restore scroll position after a short delay to allow layout changes to settle
                            this@apply.postDelayed({
                                this@apply.scrollTo(savedScrollX, savedScrollY)
                            }, 100)
                        }
                    }
                    onWebViewCreated(this)
                }

                if (!webViewStateBundle.isEmpty) {
                    webView.restoreState(webViewStateBundle)
                } else if (url.isNotEmpty()) {
                    webView.loadUrl(url)
                }

                webViewRef = webView
                swipeRefreshLayout.addView(webView)
                swipeRefreshLayout
            },
            update = { swipeRefreshLayout ->
                swipeRefreshLayout.isRefreshing = isRefreshing
                var webView: WebView? = null
                for (i in 0 until swipeRefreshLayout.childCount) {
                    val child = swipeRefreshLayout.getChildAt(i)
                    if (child is WebView) {
                        webView = child
                        break
                    }
                }

                if (webView != null) {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    try {
                        val targetUrl = if (webView.url.isNullOrEmpty()) url else webView.url
                        if (!targetUrl.isNullOrEmpty()) {
                            cookieManager.setCookie(
                                targetUrl,
                                "theme=$nitterThemeCookieValue; Path=/; SameSite=Lax"
                            )
                            cookieManager.flush()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        )

        // Full Screen Video Overlay
        if (customView != null) {
            BackHandler {
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                fullScreenMode.value = false
            }
            AndroidView(
                factory = { customView!! },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            )
        }

        // Image Viewer Overlay
        val currentImageState = clickedImageState
        if (currentImageState != null) {
            ImageViewer(
                imageUrls = currentImageState.first,
                initialIndex = currentImageState.second,
                onDismiss = { clickedImageState = null }
            )
        }
    }
}

class NitterInterface(private val onOpenGallery: (List<String>, Int) -> Unit) {
    @Suppress("unused")
    @JavascriptInterface
    fun openGallery(urlsJson: String, initialIndex: Int) {
        val urls = org.json.JSONArray(urlsJson)
        val list = mutableListOf<String>()
        for (i in 0 until urls.length()) {
            list.add(urls.getString(i))
        }
        onOpenGallery(list, initialIndex)
    }
}
