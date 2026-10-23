package com.kaleedtc.nitterium.ui.common

import org.json.JSONObject

/** Never interpolate remote text as JavaScript source. */
internal fun shareTweetScript(url: String): String = """
    (function() {
        var links = document.querySelectorAll('a');
        for (var i = 0; i < links.length; i++) {
            if (links[i].href && links[i].href.includes(${JSONObject.quote(url)})) {
                var tweetItem = links[i].closest('.timeline-item, .main-thread, .reply');
                if (tweetItem) {
                    var contentEl = tweetItem.querySelector('.tweet-content');
                    if (contentEl) return contentEl.innerText;
                }
            }
        }
        return '';
    })()
""".trimIndent()
