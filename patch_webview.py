import re

with open("app/src/main/java/com/kaleedtc/nitterium/ui/common/NitterWebView.kt", "r") as f:
    content = f.read()

# Replace the AndroidView block and customView Dialog block
# We will find AndroidView( up to Image Viewer Overlay

start_marker = "        AndroidView("
end_marker = "        // Image Viewer Overlay"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx == -1 or end_idx == -1:
    print("Markers not found!")
    exit(1)

new_android_view = """        AndroidView(
            factory = { context ->
                val rootLayout = FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val customViewContainer = FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    visibility = View.GONE
                }

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
                                                 requestUrl.path?.contains("/pic/") == true
                                
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
                            customViewContainer.addView(view)
                            customViewContainer.visibility = View.VISIBLE
                            swipeRefreshLayout.visibility = View.GONE
                            
                            customView = view
                            customViewCallback = callback
                            fullScreenMode.value = true
                        }

                        override fun onHideCustomView() {
                            customViewContainer.removeView(customView)
                            customViewContainer.visibility = View.GONE
                            swipeRefreshLayout.visibility = View.VISIBLE
                            
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                            customViewCallback = null
                            fullScreenMode.value = false
                        }
                    }
                    onWebViewCreated(this)
                }

                webViewRef = webView
                swipeRefreshLayout.addView(webView)
                rootLayout.addView(swipeRefreshLayout)
                rootLayout.addView(customViewContainer)
                
                // Initial load
                if (url.isNotEmpty()) {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    try {
                        cookieManager.setCookie(
                            url,
                            "theme=$nitterThemeCookieValue; Path=/; SameSite=Lax"
                        )
                        cookieManager.flush()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    webView.loadUrl(url)
                }
                
                rootLayout
            },
            update = { rootLayout ->
                val swipeRefreshLayout = rootLayout.getChildAt(0) as SwipeRefreshLayout
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

        // Full Screen Video Overlay Handle Back Press
        if (customView != null) {
            BackHandler {
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                fullScreenMode.value = false
            }
        }

        // Image Viewer Overlay
"""

new_content = content[:start_idx] + new_android_view + content[end_idx + len(end_marker):]

with open("app/src/main/java/com/kaleedtc/nitterium/ui/common/NitterWebView.kt", "w") as f:
    f.write(new_content)

print("Patch applied.")
