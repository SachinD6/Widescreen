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


    const val SETUP = """
        (function(){
          try{
            if (window.__tvweb) return '1';
            window.__tvweb = { cur: null };
            var css = '.__tvweb-focus{' +
              'outline:4px solid #0A84FF !important;' +
              'outline-offset:3px !important;' +
              'box-shadow:0 0 0 10px rgba(10,132,255,0.30) !important;' +
              'border-radius:6px !important;' +
              'transition:none !important;' +
              '}';
            function addStyle(doc){
              if (!doc || doc.getElementById('__tvweb-style')) return;
              var st = doc.createElement('style');
              st.id = '__tvweb-style';
              st.textContent = css;
              (doc.head || doc.documentElement).appendChild(st);
            }
            addStyle(document);
            var frames = document.querySelectorAll('iframe');
            for (var i = 0; i < frames.length; i++) {
              try { addStyle(frames[i].contentDocument); } catch (e) {}
            }
            return '1';
          } catch (e) { return '0'; }
        })()
    """

    fun nav(dx: Int, dy: Int): String = """
        (function(dx, dy){
          try{
            if (!window.__tvweb) return 'scroll';
            var SEL = 'a[href],button,input,select,textarea,video,summary,label,' +
                      '[role=button],[role=link],[role=option],[onclick],[tabindex]';
            var nodes = [];
            function harvest(doc){
              if (!doc) return;
              var found = doc.querySelectorAll(SEL);
              for (var i = 0; i < found.length; i++) nodes.push(found[i]);
              var frames = doc.querySelectorAll('iframe');
              for (var j = 0; j < frames.length; j++) {
                try { harvest(frames[j].contentDocument); } catch (e) {}
              }
            }
            harvest(document);

            var visible = [];
            for (var k = 0; k < nodes.length; k++) {
              var n = nodes[k];
              var r = n.getBoundingClientRect();
              if (r.width < 6 || r.height < 6) continue;
              var st = window.getComputedStyle(n);
              if (st.visibility === 'hidden' || st.display === 'none') continue;
              if (parseFloat(st.opacity || '1') < 0.05) continue;
              visible.push({ el: n, r: r });
            }
            if (!visible.length) return 'scroll';

            function setCurrent(entry){
              if (window.__tvweb.cur && window.__tvweb.cur !== entry.el) {
                window.__tvweb.cur.classList.remove('__tvweb-focus');
              }
              window.__tvweb.cur = entry.el;
              entry.el.classList.add('__tvweb-focus');
              try { entry.el.scrollIntoView({ block: 'center', inline: 'center' }); } catch (e) {}
            }

            var cur = null;
            for (var m = 0; m < visible.length; m++) {
              if (visible[m].el === window.__tvweb.cur) { cur = visible[m]; break; }
            }

            if (!cur) {
              var cx = window.innerWidth / 2, cy = window.innerHeight / 2, best = null, bd = 1e12;
              for (var p = 0; p < visible.length; p++) {
                var rr = visible[p].r;
                var d = Math.hypot(rr.left + rr.width/2 - cx, rr.top + rr.height/2 - cy);
                if (d < bd) { bd = d; best = visible[p]; }
              }
              if (best) setCurrent(best);
              return '1';
            }

            var cr = cur.r, bestE = null, bestScore = 1e12;
            for (var q = 0; q < visible.length; q++) {
              var c = visible[q];
              if (c.el === cur.el) continue;
              var a = c.r;
              var ax = a.left + a.width/2, ay = a.top + a.height/2;
              var bx = cr.left + cr.width/2, by = cr.top + cr.height/2;
              var ddx = ax - bx, ddy = ay - by;
              var primary, secondary;
              if (dx > 0) { if (ddx < 8) continue; primary = ddx; secondary = Math.max(0, Math.abs(ddy) - (cr.height + a.height)/2); }
              else if (dx < 0) { if (ddx > -8) continue; primary = -ddx; secondary = Math.max(0, Math.abs(ddy) - (cr.height + a.height)/2); }
              else if (dy > 0) { if (ddy < 8) continue; primary = ddy; secondary = Math.max(0, Math.abs(ddx) - (cr.width + a.width)/2); }
              else if (dy < 0) { if (ddy > -8) continue; primary = -ddy; secondary = Math.max(0, Math.abs(ddx) - (cr.width + a.width)/2); }
              else { continue; }
              var score = primary + secondary * 2.5;
              if (score < bestScore) { bestScore = score; bestE = c; }
            }

            if (bestE) { setCurrent(bestE); return '1'; }
            return 'scroll';
          } catch (e) { return 'scroll'; }
        })($dx, $dy)
    """

    const val ACTIVATE = """
        (function(){
          try{
            var el = window.__tvweb && window.__tvweb.cur;
            if (el && document.contains(el)) {
              var tag = (el.tagName || '').toUpperCase();
              var type = (el.type || '').toLowerCase();
              if (tag === 'VIDEO') {
                if (el.paused) { el.play(); } else { el.pause(); }
                return 'video';
              }
              if (tag === 'SELECT' || tag === 'TEXTAREA') { el.focus(); return 'focus'; }
              if (tag === 'INPUT') {
                if (/checkbox|radio|button|submit|reset|image/.test(type)) { el.click(); return 'click'; }
                el.focus();
                return 'focus';
              }
              el.click();
              return 'click';
            }
            var c = document.elementFromPoint(
              Math.floor(window.innerWidth / 2),
              Math.floor(window.innerHeight / 2)
            );
            if (c) { c.click(); return 'click'; }
            return 'none';
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
