package dev.sxch.tvweb.browser

data class PageState(
    val videoCount: Int = 0,
    val activeTag: String = "",
) {
    val hasVideo: Boolean get() = videoCount > 0
}

object PageAgent {

    const val PROBE = """
        (function(){
          try{
            var v = document.querySelectorAll('video');
            var a = document.activeElement;
            var tag = (a && a.tagName) ? a.tagName.toUpperCase() : '';
            return JSON.stringify({ v: v.length, t: tag });
          } catch (e) { return '{}'; }
        })()
    """

    const val TAP = """
        (function(){
          try{
            function tagOf(n){ return (n && n.tagName) ? n.tagName.toUpperCase() : ''; }
            function isField(t){ return t==='INPUT'||t==='TEXTAREA'||t==='SELECT'; }
            function clickable(n){
              var cur = n;
              while (cur && cur !== document.body) {
                var t = tagOf(cur);
                if (t==='A'||t==='BUTTON'||isField(t)||t==='LABEL'||
                    cur.getAttribute('role')==='button'||cur.hasAttribute('onclick')||
                    cur.hasAttribute('tabindex')) return cur;
                cur = cur.parentElement;
              }
              return null;
            }
            function sameOriginDoc(frame){
              try { return frame.contentDocument; } catch (e) { return null; }
            }
            function followFrames(doc, x, y, depth){
              if (depth > 3) return { el: null, blocked: false };
              var el = doc.elementFromPoint(Math.floor(x), Math.floor(y));
              if (el && tagOf(el) === 'IFRAME') {
                var inner = sameOriginDoc(el);
                if (inner) {
                  var r = el.getBoundingClientRect();
                  return followFrames(inner, x - r.left, y - r.top, depth + 1);
                }
                return { el: el, blocked: true };
              }
              return { el: el, blocked: false };
            }

            var active = document.activeElement;
            var activeTag = tagOf(active);
            if (isField(activeTag)) {
              if (activeTag === 'SELECT') { active.focus(); return 'type'; }
              var type = (active.type || '').toLowerCase();
              if (activeTag === 'INPUT' &&
                  /checkbox|radio|button|submit|reset|range|color|file/.test(type)) {
                active.click();
                return 'click-focused';
              }
              if (active.form && active.form.requestSubmit) { active.form.requestSubmit(); return 'submit'; }
              active.focus();
              return 'type';
            }
            var focusedTarget = clickable(active);
            if (focusedTarget && active !== document.body && active !== document.documentElement) {
              focusedTarget.click();
              return 'click-focused';
            }

            var found = followFrames(
              document,
              Math.floor(window.innerWidth / 2),
              Math.floor(window.innerHeight / 2),
              0
            );
            if (found.blocked) return 'native-frame';
            var target = clickable(found.el) || found.el;
            if (!target) return 'none';
            var targetTag = tagOf(target);
            if (isField(targetTag)) { target.focus(); return 'type'; }
            target.click();
            return 'click-center';
          } catch (e) { return 'error'; }
        })()
    """

    const val FULLSCREEN_VIDEO = """
        (function(){
          try{
            var v = document.querySelectorAll('video');
            var best = null, bestScore = -1;
            for (var i = 0; i < v.length; i++) {
              var score = (v[i].paused ? 0 : 100) + v[i].clientWidth * v[i].clientHeight / 1000;
              if (score > bestScore) { bestScore = score; best = v[i]; }
            }
            if (!best) return '0';
            try { if (best.paused) best.play(); } catch (e) {}
            if (best.webkitEnterFullscreen) { best.webkitEnterFullscreen(); return '1'; }
            if (best.requestFullscreen) { best.requestFullscreen(); return '1'; }
            if (best.webkitRequestFullscreen) { best.webkitRequestFullscreen(); return '1'; }
            return '0';
          } catch (e) { return '0'; }
        })()
    """

    const val PAUSE_ALL = """
        (function(){
          try{
            var v = document.querySelectorAll('video');
            for (var i = 0; i < v.length; i++) v[i].pause();
            return '1';
          } catch (e) { return '0'; }
        })()
    """

    private val SELECTORS = listOf(
        "[id*='google_ads']", "[id^='div-gpt-ad']", "[class*='ad-slot']",
        "[class*='advert']", "[class*='ad-banner']", "[class*='ad-container']",
        "[class*='sponsored']", "ins.adsbygoogle", "iframe[src*='doubleclick']",
        "iframe[src*='googlesyndication']", "[class*='cookie-banner']",
        "[id*='cookie-banner']", "[class*='paywall']",
    )

    val COSMETIC = buildString {
        append("(function(){try{var css='")
        SELECTORS.forEach { append(it).append("{display:none !important}") }
        append("';")
        append("if(!document.getElementById('tvweb-cosmetic')){")
        append("var s=document.createElement('style');s.id='tvweb-cosmetic';s.textContent=css;")
        append("(document.head||document.documentElement).appendChild(s);}return '1';}catch(e){return '0';}})()")
    }

    fun scrollBy(px: Int): String =
        "(function(){try{" +
            "var el=document.activeElement;" +
            "while(el&&el!==document.body&&el!==document.documentElement){" +
            "var s=getComputedStyle(el);" +
            "if((s.overflowY==='auto'||s.overflowY==='scroll')&&el.scrollHeight>el.clientHeight+4){" +
            "el.scrollTop+=($px);return '1';}" +
            "el=el.parentElement;}" +
            "window.scrollBy({top:$px,left:0,behavior:'instant'});return '1';" +
            "}catch(e){return '0';}})()"
}
