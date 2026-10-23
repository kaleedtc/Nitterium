package com.kaleedtc.nitterium.ui.common

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import com.kaleedtc.nitterium.data.repository.CookieInstanceOptionsStore
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.kaleedtc.nitterium.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kaleedtc.nitterium.data.frontend.FrontendType
import com.kaleedtc.nitterium.data.frontend.FrontendAdapter
import com.kaleedtc.nitterium.data.frontend.InstanceSettingsCookies
import java.net.URI
import com.kaleedtc.nitterium.data.security.UrlPolicy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NitterWebView(
    url: String,
    selectedInstanceUrl: String,
    isTrueBlack: Boolean,
    isSiteHeaderEnabled: Boolean,
    isBlockDirectXEnabled: Boolean,
    darkTheme: Boolean,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, WebView) -> Unit,
    onPageError: (Int, String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier,
    frontend: FrontendType = FrontendType.NITTER,
    onFrontendDetected: (String, FrontendType) -> Unit = { _, _ -> },
    isRefreshing: Boolean = false,
    isProfileView: Boolean = false,
    isFeedView: Boolean = false,
    onRefresh: () -> Unit = {},
    onAvatarFound: (String, String) -> Unit = { _, _ -> },
) {
    // 0. State for Image Viewer
    var clickedImageState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // 0.1 State for Full Screen Video
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val fullScreenMode = LocalFullScreenMode.current
    var savedScrollX by rememberSaveable { mutableIntStateOf(0) }
    var savedScrollY by rememberSaveable { mutableIntStateOf(0) }

    var longPressedUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val positionScript = remember { context.assets.open("reading-position.js").bufferedReader().use { it.readText() } }

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
                --bg_color: $bgHex !important;
                --bg_panel: $surfaceHex !important;
                --bg_elements: $surfaceHex !important;
                --bg_overlays: $surfaceHex !important;
                --bg_hover: $surfaceHex !important;
                --fg_color: $textHex !important;
                --fg_dark: $textHex !important;
                --fg_nav: $textHex !important;
                --fg_faded: $textVariantHex !important;
                --grey: $textVariantHex !important;
                --border_grey: $borderHex !important;
                --dark_grey: $borderHex !important;
                --darker_grey: $borderHex !important;
                --darkest_grey: $surfaceHex !important;
                --accent: $primaryHex !important;
                --accent_light: $primaryHex !important;
                --accent_border: $borderHex !important;
                --tab: $textVariantHex !important;
                --tab_selected: $primaryHex !important;
                --profile_stat: $textHex !important;
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
        body { --bg_color: #000000 !important; --bg_panel: #000000 !important; --bg_elements: #000000 !important; }
        """
    } else ""

    fun isProfileUrl(currentUrl: String?): Boolean {
        return !currentUrl.isNullOrEmpty() && try {
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
        val isActuallyFeedRoot = isFeedView && currentUrl != null && try {
            FrontendAdapter.sameOrigin(url, currentUrl) &&
                URI(url).path.trimEnd('/') == URI(currentUrl).path.trimEnd('/')
        } catch (_: Exception) {
            false
        }
        // The native feed toolbar already identifies this timeline. Teapot renders
        // a separate account-list heading outside the shared Nitter header classes.
        val feedHeadingCss = if (isActuallyFeedRoot) {
            ".multi-user-timeline > h2 { display: none !important; }"
        } else ""
        if (isSiteHeaderEnabled) return feedHeadingCss

        // Prioritize actual URL check. If the URL is a global-reserved page (like /search),
        // we always use strict CSS to hide the search bar, even if the screen context is "Profile".
        val isProfile = if (currentUrl.isNullOrEmpty()) isProfileView else isProfileUrl(currentUrl)

        var css = if (isProfile) {
            "nav, .site-header, .header, .search-header"
        } else {
            "nav, .site-header, .header, .search-header, .timeline-header, .search-bar, form[action*=\"search\"], .search-field, .search-panel"
        }
        
        if (isActuallyFeedRoot) {
            css += ", .profile-card, .profile-tabs, .profile-banner, .profile-header, .timeline-header, ul.tab"
        }

        return "$css { display: none !important; } $feedHeadingCss"
    }

    fun getJsInjection(currentUrl: String?): String {
        val headerCss = getSiteHeaderCss(currentUrl)
        val instanceOptions = InstanceSettingsCookies.read(
            runCatching { CookieManager.getInstance().getCookie(currentUrl ?: url).orEmpty() }.getOrDefault(""), frontend
        )
        val squareAvatars = instanceOptions.squareAvatars == true
        val stickyProfile = instanceOptions.stickyProfile == true
        return """
            javascript:(function() {
                function updateStyle(id, css) {
                    var style = document.getElementById(id);
                    if (!style) {
                        style = document.createElement('style');
                        style.id = id;
                        var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
                        if (head) {
                            head.appendChild(style);
                        }
                    }
                    if (style && style.innerHTML !== css) {
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
                    .tweet-published { display: none !important; }
                `);

                function fixDirection() {
                    // History snapshots and newly inserted posts may contain
                    // avatar classes from before the instance options changed.
                    document.querySelectorAll('img.avatar').forEach(function(el) {
                        el.classList.toggle('round', ${!squareAvatars});
                    });
                    document.querySelectorAll('.profile-tab').forEach(function(el) {
                        el.classList.toggle('sticky', $stickyProfile);
                    });
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
                    if (document.body) document.body.classList.add('dark');
                    if (document.documentElement) document.documentElement.style.colorScheme = 'dark';
                } else {
                    if (document.body) document.body.classList.remove('dark', 'h-dark');
                    if (document.documentElement) document.documentElement.style.colorScheme = 'light';
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
                                if (window.NitterAndroid) NitterAndroid.openGallery(JSON.stringify(imageLinks), clickedIndex);
                            }
                        }
                    }, true);
                    window.nitterImageListenerAttached = true;
                }

                if (!window.nitterContextMenuListenerAttached) {
                    document.addEventListener('contextmenu', function(e) {
                        var target = e.target;
                        while (target && target.tagName !== 'A') {
                            target = target.parentElement;
                        }
                        if (target && target.tagName === 'A' && target.href) {
                            if (window.NitterAndroid && NitterAndroid.handleLongPress) {
                                NitterAndroid.handleLongPress(target.href);
                            }
                        }
                    }, true);
                    window.nitterContextMenuListenerAttached = true;
                }

                fixDirection();
                if (document.body) {
                    new MutationObserver(fixDirection).observe(document.body, { childList: true, subtree: true });
                }
            })()
        """.trimIndent()
    }

    val nitterThemeCookieValue = remember(frontend, darkTheme, isTrueBlack) {
        FrontendAdapter.forType(frontend).themeCookie(darkTheme, isTrueBlack)
    }

    val instanceOptions = remember { CookieInstanceOptionsStore() }

    val rendererGoneError = stringResource(R.string.renderer_gone_error)
    val instanceUnavailableError = stringResource(R.string.instance_unavailable_error)
    val latestJsInjection by rememberUpdatedState { url: String? -> getJsInjection(url) }
    val latestOnFrontendDetected by rememberUpdatedState(onFrontendDetected)
    val latestOnPageStarted by rememberUpdatedState(onPageStarted)
    val latestOnPageFinished by rememberUpdatedState(onPageFinished)
    val latestOnPageError by rememberUpdatedState(onPageError)
    val latestInstance by rememberUpdatedState(selectedInstanceUrl)
    val latestOnAvatarFound by rememberUpdatedState(onAvatarFound)

    // Request interception runs on a background thread.
    val blockDirectXState = remember { AtomicBoolean(isBlockDirectXEnabled) }
    blockDirectXState.set(isBlockDirectXEnabled)
    val selectedInstanceState = remember { AtomicReference(selectedInstanceUrl) }
    selectedInstanceState.set(selectedInstanceUrl)
    val pageBridge = remember {
        SecurePageBridge(
            selectedInstance = { latestInstance },
            blockDirectX = { blockDirectXState.get() },
            onGallery = { urls, index -> clickedImageState = urls to index },
            onLongPress = { longPressedUrl = it }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        val webViewStateBundle = rememberSaveable(
            saver = Saver(
                save = { saved ->
                    Bundle(saved).also { bundle -> webViewRef?.saveReadingState(bundle) }
                },
                restore = { it }
            )
        ) { Bundle() }

        val lifecycleOwner = LocalLifecycleOwner.current

        // Re-inject theme when it changes to ensure it's always up to date
        // even without a page reload (e.g. when switching system theme).
        LaunchedEffect(currentThemeCss, trueBlackCss, darkTheme, frontend, isSiteHeaderEnabled) {
            webViewRef?.let { wv ->
                if (UrlPolicy.sameOrigin(latestInstance, wv.url)) {
                    wv.evaluateJavascript(latestJsInjection(wv.url), null)
                }
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                    Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                webViewRef?.let { wv ->
                    // Save while the browser is still alive. Navigation may request
                    // the saveable state after this composition has been disposed.
                    wv.saveReadingState(webViewStateBundle)
                    pageBridge.close()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.removeAllViews()
                    wv.destroy()
                }
                webViewRef = null
            }
        }

        LaunchedEffect(url, selectedInstanceUrl, webViewRef) {
            val wv = webViewRef
            if (wv != null && url.isNotEmpty()) {
                if (!UrlPolicy.sameOrigin(latestInstance, url)) {
                    pageBridge.close()
                    wv.stopLoading()
                    latestOnPageError(-1, "Blocked navigation outside the selected instance")
                    return@LaunchedEffect
                }
                instanceOptions.ensureProxyDefaults(url)
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
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        val defaultUA = userAgentString
                        userAgentString = defaultUA.replace("; wv", "")
                            .replace(Regex("Version/\\d+\\.\\d+\\s+"), "")
                    }

                    // Enable third party cookies for better instance compatibility
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    setOnLongClickListener { v ->
                        val result = (v as WebView).hitTestResult
                        if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                            val urlResult = result.extra
                            if (!urlResult.isNullOrEmpty()) {
                                val finalUrl = try {
                                    val baseUri = URI(v.url ?: url)
                                    baseUri.resolve(urlResult).toString()
                                } catch (_: Exception) {
                                    urlResult
                                }
                                if (!UrlPolicy.sameOrigin(latestInstance, v.url)) return@setOnLongClickListener false
                                longPressedUrl = UrlPolicy.httpsUrl(finalUrl)?.toString() ?: return@setOnLongClickListener false
                                return@setOnLongClickListener true
                            }
                        }
                        false
                    }

                    // Leave HTTP error documents in the WebView so browser-verification
                    // scripts and redirects can finish using the default HTTP handling.
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            pageBridge.close()
                            clickedImageState = null
                            longPressedUrl = null
                            if (!UrlPolicy.sameOrigin(latestInstance, url)) {
                                view?.stopLoading()
                                latestOnPageError(-1, "Blocked navigation outside the selected instance")
                                return
                            }
                            view?.alpha = 0f
                            url?.let { latestOnPageStarted(it) }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (view == null || url == null || view.url != url ||
                                !UrlPolicy.sameOrigin(latestInstance, url)) return
                            pageBridge.attach(view, url)
                            view.evaluateJavascript("""
                                (function() {
                                    // A verification redirect may have replaced the finished document.
                                    if (window.location.href !== ${org.json.JSONObject.quote(url)}) return null;
                                    var errors = Array.from(document.querySelectorAll('.error-panel, .error-message, .error'))
                                        .filter(function(element) { return !element.closest('.timeline-item, .main-tweet'); });
                                    if (!document.querySelector('.timeline-item, .main-tweet') && document.body) {
                                        errors.push(document.body);
                                    }
                                    var unavailable = errors.find(function(element) {
                                        var text = (element.innerText || '').replace(/\s+/g, ' ');
                                        return /instance has been rate limited|instance has no auth tokens,? or is fully rate limited/i.test(text);
                                    });
                                    if (unavailable) return {instanceUnavailable: (unavailable.innerText || '').trim()};
                                    if (document.querySelector('link[href*="/css/clean.css"]')) return "TEAPOT";
                                    if (document.querySelector('.timeline, .profile-card, link[href*="/css/style.css"]')) return "NITTER";
                                    return null;
                                })()
                            """.trimIndent()) { detected ->
                                if (view.url != url || !UrlPolicy.sameOrigin(latestInstance, url)) return@evaluateJavascript
                                if (detected.startsWith('{')) {
                                    val message = if (detected.length <= 65_536) runCatching {
                                        org.json.JSONObject(detected).optString("instanceUnavailable").takeIf { it.isNotBlank() }
                                    }.getOrNull() else instanceUnavailableError
                                    if (message == null) return@evaluateJavascript
                                    latestOnPageError(503, message)
                                    return@evaluateJavascript
                                }
                                when (detected) {
                                    "\"TEAPOT\"" -> latestOnFrontendDetected(url, FrontendType.TEAPOT)
                                    "\"NITTER\"" -> latestOnFrontendDetected(url, FrontendType.NITTER)
                                }
                                latestOnPageFinished(url, view)
                                // Read the main document directly; no avatar interface is exposed to frames.
                                view.evaluateJavascript("""
                                    (function() {
                                        if (location.href !== ${org.json.JSONObject.quote(url)}) return null;
                                        var avatar = document.querySelector('a.profile-card-avatar');
                                        if (avatar) return avatar.href;
                                        var image = document.querySelector('.main-tweet .tweet-avatar img, .tweet-avatar img');
                                        if (image) return image.src;
                                        var meta = document.querySelector('meta[property="og:image"]');
                                        return location.pathname.indexOf('/status/') === -1 && meta ? meta.content : null;
                                    })()
                                """.trimIndent()) avatarResult@{ result ->
                                    if (view.url != url || !UrlPolicy.sameOrigin(latestInstance, url) ||
                                        result.length > 65_536 || !result.startsWith('"')) return@avatarResult
                                    val value = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()
                                    val avatar = UrlPolicy.mediaUrl(value, blockDirectXState.get()) ?: return@avatarResult
                                    latestOnAvatarFound(url, avatar)
                                }
                            }
                            view.evaluateJavascript(latestJsInjection(url)) {
                                if (view.url != url || !UrlPolicy.sameOrigin(latestInstance, url)) return@evaluateJavascript
                                view.evaluateJavascript(positionScript, null)
                                if (webViewStateBundle.containsKey("readingY")) {
                                    val x = webViewStateBundle.getInt("readingX")
                                    val y = webViewStateBundle.getInt("readingY")
                                    webViewStateBundle.remove("readingY")
                                    view.post { view.scrollTo(x, y) }
                                }
                                view.alpha = 1f
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: android.webkit.RenderProcessGoneDetail?
                        ): Boolean {
                            if (view != null) {
                                latestOnPageError(-1, rendererGoneError)
                            }
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

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: android.webkit.SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            // User privacy is first: do not proceed on SSL errors.
                            // Cancel the connection and trigger an error state.
                            handler?.cancel()
                            
                            val errorMessage = error?.let {
                                when (it.primaryError) {
                                    android.net.http.SslError.SSL_EXPIRED -> "SSL Certificate Expired"
                                    android.net.http.SslError.SSL_IDMISMATCH -> "SSL Hostname Mismatch"
                                    android.net.http.SslError.SSL_UNTRUSTED -> "SSL Certificate Untrusted"
                                    android.net.http.SslError.SSL_DATE_INVALID -> "SSL Date Invalid"
                                    android.net.http.SslError.SSL_NOTYETVALID -> "SSL Certificate Not Yet Valid"
                                    else -> "SSL Error"
                                }
                            } ?: "Unknown SSL Error"
                            
                            latestOnPageError(-3, errorMessage)
                        }

                        override fun shouldInterceptRequest(

                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            if (request?.isForMainFrame == true &&
                                !UrlPolicy.sameOrigin(selectedInstanceState.get(), request.url.toString())) {
                                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                                    mapOf("Cache-Control" to "no-store"), java.io.ByteArrayInputStream(ByteArray(0)))
                            }
                            if (blockDirectXState.get()) {
                                val requestHost = request?.url?.host?.lowercase()
                                if (requestHost != null) {
                                    val isTwitterDomain = UrlPolicy.isXHost(requestHost)

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
                            val requestUrl = request?.url ?: return true
                            val target = UrlPolicy.httpsUrl(requestUrl.toString()) ?: return true
                            if (blockDirectXState.get() && UrlPolicy.isXHost(target.host)) return true
                            if (UrlPolicy.sameOrigin(latestInstance, target.toString())) return false
                            if (request.isForMainFrame && request.hasGesture()) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, requestUrl)) }
                            }
                            return true
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

                if (url.isNotEmpty() && UrlPolicy.sameOrigin(latestInstance, url)) {
                    instanceOptions.ensureProxyDefaults(url)
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    try {
                        cookieManager.setCookie(
                            url,
                            "theme=$nitterThemeCookieValue; Path=/; SameSite=Lax"
                        )
                        cookieManager.flush()
                    } catch (_: Exception) {}
                }
                // A nonempty bundle can contain scroll coordinates without valid
                // browser history (for example if the page was still starting).
                val restored = webView.restoreState(webViewStateBundle)
                val safeHistory = restored != null && (0 until restored.size).all {
                    UrlPolicy.sameOrigin(latestInstance, restored.getItemAtIndex(it).url)
                }
                if (!safeHistory) {
                    webView.stopLoading()
                    webView.clearHistory()
                    if (restored != null) webViewStateBundle.clear()
                    if (url.isNotEmpty() && UrlPolicy.sameOrigin(latestInstance, url)) webView.loadUrl(url)
                    else if (url.isNotEmpty()) latestOnPageError(-1, "Invalid instance URL")
                }

                webViewRef = webView
                swipeRefreshLayout.addView(webView)
                swipeRefreshLayout
            },
            update = { swipeRefreshLayout ->
                swipeRefreshLayout.isRefreshing = isRefreshing
                swipeRefreshLayout.setBackgroundColor(backgroundColor.toArgb())
                swipeRefreshLayout.setProgressBackgroundColorSchemeColor(surfaceColor.toArgb())
                swipeRefreshLayout.setColorSchemeColors(primaryColor.toArgb())
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
                        if (!targetUrl.isNullOrEmpty() && UrlPolicy.sameOrigin(latestInstance, targetUrl)) {
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
            key(currentImageState) {
                ImageViewer(
                    imageUrls = currentImageState.first,
                    initialIndex = currentImageState.second,
                    onDismiss = { clickedImageState = null }
                )
            }
        }

        // Link Options Dialog
        longPressedUrl?.let { urlToCopy ->
            val linkCopiedMsg = stringResource(R.string.link_copied)
            val shareLinkTitle = stringResource(R.string.share_link)
            val shareContentStr = stringResource(R.string.share_content)
            val contentExtErrStr = stringResource(R.string.content_extraction_error)
            val isTweetUrl = urlToCopy.contains("/status/")
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

            ModalBottomSheet(
                onDismissRequest = { longPressedUrl = null },
                sheetState = sheetState
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = urlToCopy,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    
                    if (isTweetUrl) {
                        ListItem(
                            modifier = Modifier.clickable {
                                val jsCode = shareTweetScript(urlToCopy)
                                webViewRef?.takeIf { UrlPolicy.sameOrigin(latestInstance, it.url) }?.evaluateJavascript(jsCode) { result ->
                                    try {
                                        require(result.length <= 65_536 && result.startsWith('"'))
                                        val extractedText = org.json.JSONObject("{\"t\":$result}").getString("t")
                                        if (extractedText.isNotEmpty()) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, extractedText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, shareContentStr))
                                        } else {
                                            Toast.makeText(context, contentExtErrStr, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, contentExtErrStr, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                longPressedUrl = null
                            }
                        ) {
                            Text(shareContentStr)
                        }
                    }
                    ListItem(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, urlToCopy)
                            }
                            context.startActivity(Intent.createChooser(intent, shareLinkTitle))
                            longPressedUrl = null
                        }
                    ) {
                        Text(stringResource(R.string.share_link))
                    }
                    ListItem(
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Copied Link", urlToCopy)
                            // The SDK's nullable getter and non-null setter do not form a writable Kotlin property.
                            @Suppress("UsePropertyAccessSyntax")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, linkCopiedMsg, Toast.LENGTH_SHORT).show()
                            longPressedUrl = null
                        }
                    ) {
                        Text(stringResource(R.string.copy_link))
                    }
                }
            }
        }
    }
}

private fun WebView.saveReadingState(destination: Bundle) {
    // Preserve a pending restoration if state is requested before the restored
    // document has finished loading and applied its saved scroll coordinates.
    if (destination.containsKey("readingY")) return
    val snapshot = Bundle()
    if (saveState(snapshot) != null) {
        snapshot.putInt("readingX", scrollX)
        snapshot.putInt("readingY", scrollY)
        destination.clear()
        destination.putAll(snapshot)
    }
}
