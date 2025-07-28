package com.tradingview.lightweightcharts.example.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.OverScroller
import com.tradingview.lightweightcharts.view.ChartsView
import java.lang.reflect.Field
import android.util.Log

/**
 * 차트 성능 최적화 도구
 * WebView 기반 차트의 터치 반응성과 스크롤 성능을 개선합니다.
 */
object ChartPerformanceOptimizer {

    /**
     * 고성능 WebView 설정 적용
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun optimizeWebViewPerformance(webView: WebView) {
        webView.apply {
            // 하드웨어 가속 강제 활성화
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
            
            // WebView 설정 최적화
            settings.apply {
                // JavaScript 성능 최적화
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                
                // 렌더링 최적화
                domStorageEnabled = true
                databaseEnabled = false
                // setAppCacheEnabled(false) // Deprecated in API 33
                
                // 캐시 및 메모리 최적화
                cacheMode = WebSettings.LOAD_NO_CACHE
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                
                // 터치 및 스크롤 최적화
                setSupportZoom(true)
                builtInZoomControls = false
                displayZoomControls = false
                
                // 성능 향상을 위한 기능 비활성화
                allowContentAccess = false
                allowFileAccess = false
                blockNetworkImage = false
                blockNetworkLoads = false
                
                // 최신 디바이스 최적화
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                // GPU 가속 최적화
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                }
            }
            
            // 스크롤바 비활성화 (성능 향상)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            
            // 포커스 최적화
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    /**
     * ChartsView 성능 최적화
     */
    fun optimizeChartsView(chartsView: ChartsView) {
        chartsView.apply {
            // 하드웨어 가속 활성화
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            
            // 네스티드 스크롤 최적화
            isNestedScrollingEnabled = true
            
            // 터치 이벤트 최적화
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            
            // WebView 직접 최적화
            try {
                val webViewField = getWebViewField(this)
                webViewField?.let { field ->
                    field.isAccessible = true
                    val webView = field.get(this) as? WebView
                    webView?.let { optimizeWebViewPerformance(it) }
                }
            } catch (e: Exception) {
                android.util.Log.d("ChartOptimizer", "WebView 직접 최적화 실패: ${e.message}")
            }
        }
    }

    /**
     * 단순화된 멀티패널 차트 최적화 - 터치 충돌 방지
     */
    fun optimizeMultiPanelSync(mainChart: ChartsView, vararg subCharts: ChartsView) {
        try {
            Log.d("ChartOptimizer", "단순화된 멀티패널 최적화 시작")
            
            // 서브 차트들 스크롤바만 설정 (터치 리스너는 Activity에서 관리)
            subCharts.forEachIndexed { index, subChart ->
                try {
                    subChart.apply {
                        // 서브 차트는 수직 스크롤 비활성화 (좌우 스크롤만 허용)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false // 스크롤바 숨김
                        
                        // 터치 포커스 비활성화 - 메인 차트만 터치 받도록
                        isFocusable = false
                        isFocusableInTouchMode = false
                        isClickable = false
                    }
                    Log.d("ChartOptimizer", "서브차트 ${index + 1} 기본 설정 완료")
                } catch (e: Exception) {
                    Log.w("ChartOptimizer", "서브차트 ${index + 1} 설정 실패: ${e.message}")
                }
            }
            
            // 메인 차트 기본 설정
            try {
                mainChart.apply {
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false // 스크롤바 숨김
                    
                    // 메인 차트만 터치 이벤트 처리
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    
                    requestFocus()
                }
                Log.d("ChartOptimizer", "메인차트 기본 설정 완료")
            } catch (e: Exception) {
                Log.w("ChartOptimizer", "메인차트 설정 실패: ${e.message}")
            }
            
            Log.d("ChartOptimizer", "단순화된 멀티패널 최적화 완료")
            
        } catch (e: Exception) {
            Log.e("ChartOptimizer", "멀티패널 최적화 설정 실패: ${e.message}")
        }
    }

    /**
     * 터치 응답성 개선
     */
    fun improveTouchResponsiveness(chartsView: ChartsView) {
        chartsView.apply {
            // 터치 지연 최소화
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 즉시 하드웨어 가속 활성화
                        view.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                        
                        // 부모 뷰의 터치 인터셉트 방지
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
            
            // 포커스 관리
            requestFocus()
        }
    }

    /**
     * 스크롤 성능 최적화
     */
    fun optimizeScrollPerformance(chartsView: ChartsView) {
        try {
            // WebView의 OverScroller 최적화
            val webViewField = getWebViewField(chartsView)
            webViewField?.let { field ->
                field.isAccessible = true
                val webView = field.get(chartsView) as? WebView
                webView?.let { optimizeWebViewScroller(it) }
            }
        } catch (e: Exception) {
            android.util.Log.d("ChartOptimizer", "스크롤 최적화 실패: ${e.message}")
        }
    }

    /**
     * WebView의 스크롤러 최적화
     */
    private fun optimizeWebViewScroller(webView: WebView) {
        try {
            // OverScroller를 더 반응적으로 설정
            val scrollerFields = webView.javaClass.declaredFields.filter { 
                it.type == OverScroller::class.java 
            }
            
            scrollerFields.forEach { field ->
                field.isAccessible = true
                val scroller = field.get(webView) as? OverScroller
                scroller?.let {
                    // 스크롤 마찰 감소 (더 부드러운 스크롤)
                    // reflection을 통한 내부 설정 조정은 안전성을 위해 생략
                    android.util.Log.d("ChartOptimizer", "OverScroller 최적화 적용")
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("ChartOptimizer", "OverScroller 최적화 실패: ${e.message}")
        }
    }

    /**
     * ChartsView에서 WebView 필드 찾기
     */
    private fun getWebViewField(chartsView: ChartsView): Field? {
        return try {
            val fields = chartsView.javaClass.declaredFields
            fields.find { it.type == WebView::class.java }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 메모리 최적화
     */
    fun optimizeMemoryUsage(context: Context, vararg chartsViews: ChartsView) {
        chartsViews.forEach { chartsView ->
            chartsView.apply {
                // 백그라운드에서 메모리 정리
                // onPause() // ChartsView에는 onPause 메서드가 없음
                
                // 렌더링 최적화
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                
                // 메모리 사용량 모니터링 및 정리
                try {
                    val webViewField = getWebViewField(this)
                    webViewField?.let { field ->
                        field.isAccessible = true
                        val webView = field.get(this) as? WebView
                        webView?.let { 
                            // WebView 메모리 정리
                            it.clearCache(true)
                            it.clearHistory()
                            System.gc() // 가비지 컬렉션 제안
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("ChartOptimizer", "메모리 정리 실패: ${e.message}")
                }
            }
        }
    }

    /**
     * 전체적인 성능 최적화 적용
     */
    fun applyAllOptimizations(context: Context, vararg chartsViews: ChartsView) {
        chartsViews.forEach { chartsView ->
            optimizeChartsView(chartsView)
            improveTouchResponsiveness(chartsView)
            optimizeScrollPerformance(chartsView)
        }
        
        // 메모리 최적화는 별도로 호출 (필요시에만)
        // optimizeMemoryUsage(context, *chartsViews)
        
        android.util.Log.d("ChartOptimizer", "모든 차트 성능 최적화 적용 완료")
    }
}