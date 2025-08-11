package com.murari.careerpolitics.core.sidebar

import android.webkit.WebView

class SidebarController {
    private var isPageLoaded: Boolean = false
    private var pendingOpenSidebar: Boolean = false
    private var lastSidebarTriggerMs: Long = 0L

    fun onPageLoaded(webView: WebView) {
        isPageLoaded = true
        if (pendingOpenSidebar) {
            pendingOpenSidebar = false
            openWebSidebar(webView)
        }
    }

    fun requestOpen(webView: WebView) {
        val now = System.currentTimeMillis()
        if (now - lastSidebarTriggerMs < 250) return // debounce
        lastSidebarTriggerMs = now
        if (!isPageLoaded) {
            pendingOpenSidebar = true
            return
        }
        openWebSidebar(webView)
    }

    private fun openWebSidebar(webView: WebView) {
        val js = """
            (function(){
                try{if(!(window.__nativeSidebar&&__nativeSidebar.open)){throw new Error('reinjection');}}catch(_){
                    (function(){
                        if(window.__nativeSidebar&&window.__nativeSidebar.__v==1)return true;
                        window.__nativeSidebar=(function(){
                            function q(){return document.querySelector('button.js-hamburger-trigger,[data-sidebar-toggle],button[aria-label*="menu" i],button[aria-label*="navigation" i],.hamburger,.menu,.drawer-toggle,.navbar-toggle,[data-testid="menu-button"],[data-action="open-sidebar"]');}
                            function isOpen(){return document.body.classList.contains('sidebar-open')||!!document.querySelector('.sidebar.open,.drawer.open,.nav--open,.is-open,.menu-open,[aria-expanded="true"]');}
                            function open(){
                                if(isOpen())return true;
                                var el=q();
                                if(el){
                                    try{el.focus();}catch(e){}
                                    var o={bubbles:true,cancelable:true};
                                    try{el.dispatchEvent(new PointerEvent('pointerdown',o));}catch(e){}
                                    try{el.dispatchEvent(new MouseEvent('mousedown',o));}catch(e){}
                                    try{el.dispatchEvent(new TouchEvent('touchstart',o));}catch(e){}
                                    try{el.dispatchEvent(new MouseEvent('click',o));}catch(e){}
                                    try{el.dispatchEvent(new MouseEvent('mouseup',o));}catch(e){}
                                    try{el.dispatchEvent(new PointerEvent('pointerup',o));}catch(e){}
                                    return true;
                                }
                                return false;
                            }
                            return {open:open,isOpen:isOpen,__v:1};
                        })();
                    })();
                }
                try{window.__nativeSidebar&&__nativeSidebar.open&&__nativeSidebar.open();}catch(e){}
                try{window.dispatchEvent(new Event('native-open-sidebar'));}catch(e){}
                true;
            })()
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}