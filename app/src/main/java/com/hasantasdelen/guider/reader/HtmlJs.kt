package com.hasantasdelen.guider.reader

import com.hasantasdelen.guider.ui.AppTheme

object HtmlJs {
    fun css(theme: AppTheme, applyTheme: Boolean): String {
        val base = """
            img{max-width:100% !important;height:auto !important;}
            table{max-width:100% !important;}
            pre{white-space:pre-wrap !important;word-wrap:break-word !important;}
            .gr-tts{background:${theme.cssHighlight()} !important;border-radius:4px;outline:2px solid ${theme.cssAccent()};}
            .gr-find{background:${theme.cssAccent()}55 !important;}
        """.trimIndent()
        if (!applyTheme) return base
        return base + """
            html,body{background:${theme.cssBg()} !important;color:${theme.cssText()} !important;}
            body *:not(img):not(svg):not(video):not(canvas){background-color:transparent !important;color:${theme.cssText()} !important;border-color:${theme.cssAccent()}44 !important;}
            a,a *{color:${theme.cssAccent()} !important;}
            .gr-tts,.gr-tts *{background:${theme.cssHighlight()} !important;}
        """.trimIndent()
    }

    /** Script injected after each page load. Exposes window.GuideR helpers used by the reader. */
    fun script(css: String): String = """
(function(){
  var st=document.getElementById('gr-style');
  if(!st){st=document.createElement('style');st.id='gr-style';(document.head||document.documentElement).appendChild(st);}
  st.textContent=${jsString(css)};
  if(!document.querySelector('meta[name=viewport]')){var m=document.createElement('meta');m.name='viewport';m.content='width=device-width, initial-scale=1';(document.head||document.documentElement).appendChild(m);}
  if(window.GuideR){return;}
  var SEL='p,li,h1,h2,h3,h4,h5,h6,pre,td,th,dt,dd,blockquote,figcaption,caption';
  var G={blocks:[],heads:[],cur:-1,tap:false};
  G.collect=function(){
    G.blocks=[];var out=[];
    var els=document.body.querySelectorAll(SEL);
    for(var i=0;i<els.length;i++){
      var el=els[i];
      if(el.querySelector(SEL))continue;
      if(el.closest('script,style,nav,noscript'))continue;
      var t=(el.innerText||'').trim();
      if(!t)continue;
      el.setAttribute('data-gr',G.blocks.length);
      G.blocks.push(el);out.push(t);
    }
    // plain divs holding text with <br> only
    if(out.length<3){
      var divs=document.body.querySelectorAll('div');
      for(var j=0;j<divs.length;j++){
        var d=divs[j];var own='';
        for(var k=0;k<d.childNodes.length;k++){var n=d.childNodes[k];if(n.nodeType===3)own+=n.textContent;}
        own=own.trim();
        if(own.length>40&&!d.hasAttribute('data-gr')){d.setAttribute('data-gr',G.blocks.length);G.blocks.push(d);out.push((d.innerText||'').trim());}
      }
    }
    return JSON.stringify(out);
  };
  G.highlight=function(i,scroll){
    if(G.cur>=0&&G.blocks[G.cur])G.blocks[G.cur].classList.remove('gr-tts');
    G.cur=i;var el=G.blocks[i];
    if(!el)return;
    el.classList.add('gr-tts');
    if(scroll){var r=el.getBoundingClientRect();if(r.top<60||r.bottom>window.innerHeight-60)el.scrollIntoView({block:'center',behavior:'smooth'});}
  };
  G.clear=function(){if(G.cur>=0&&G.blocks[G.cur])G.blocks[G.cur].classList.remove('gr-tts');G.cur=-1;};
  G.firstVisible=function(){
    if(!G.blocks.length)G.collect();
    for(var i=0;i<G.blocks.length;i++){var r=G.blocks[i].getBoundingClientRect();if(r.bottom>4&&r.height>0)return i;}
    return 0;
  };
  G.toc=function(){
    var hs=document.body.querySelectorAll('h1,h2,h3,h4');G.heads=[];var out=[];
    for(var i=0;i<hs.length;i++){
      var h=hs[i];var t=(h.innerText||'').trim();if(!t)continue;
      if(!h.id)h.id='gr-h-'+i;
      G.heads.push(h);out.push({t:t.substring(0,120),id:h.id,l:parseInt(h.tagName.substring(1))-1});
    }
    return JSON.stringify(out);
  };
  G.scrollToId=function(id){var e=document.getElementById(id);if(e)e.scrollIntoView({block:'start'});};
  G.currentHead=function(){
    var idx=-1;for(var i=0;i<G.heads.length;i++){if(G.heads[i].getBoundingClientRect().top<=90)idx=i;else break;}return idx;
  };
  G.getPos=function(){var h=document.documentElement.scrollHeight||1;return (window.scrollY||document.documentElement.scrollTop)/h;};
  G.setPos=function(p){var h=document.documentElement.scrollHeight||1;window.scrollTo(0,p*h);};
  G.scrollBy=function(d){window.scrollBy({top:d,behavior:'smooth'});};
  G.page=function(dir){window.scrollBy({top:dir*window.innerHeight*0.85,behavior:'smooth'});};
  window.GuideR=G;
  var lpTimer=null,lpX=0,lpY=0,moved=false;
  function blockOf(t){var b=t&&t.closest?t.closest('[data-gr]'):null;return b?parseInt(b.getAttribute('data-gr')):-1;}
  document.addEventListener('touchstart',function(e){
    moved=false;var t=e.target;lpX=e.touches[0].clientX;lpY=e.touches[0].clientY;
    lpTimer=setTimeout(function(){if(!G.blocks.length)G.collect();var b=blockOf(t);if(b>=0&&!moved){GuideRAndroid.onBlockLongPress(b);}},650);
  },{passive:true});
  document.addEventListener('touchmove',function(e){var x=e.touches[0].clientX,y=e.touches[0].clientY;if(Math.abs(x-lpX)>10||Math.abs(y-lpY)>10){moved=true;clearTimeout(lpTimer);}},{passive:true});
  document.addEventListener('touchend',function(){clearTimeout(lpTimer);},{passive:true});
  document.addEventListener('click',function(e){
    var t=e.target;
    if(G.tap){var b=blockOf(t);if(b>=0){GuideRAndroid.onBlockTap(b);e.preventDefault();return;}}
    if(t&&t.tagName==='IMG'&&!t.closest('a')){GuideRAndroid.onImage(t.currentSrc||t.src);e.preventDefault();}
  },true);
  var sTimer=null;
  window.addEventListener('scroll',function(){clearTimeout(sTimer);sTimer=setTimeout(function(){GuideRAndroid.onScroll(G.getPos(),G.currentHead());},250);},{passive:true});
})();
""".trimIndent()

    fun jsString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> {}
                ' ' -> sb.append("\\u2028")
                ' ' -> sb.append("\\u2029")
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
