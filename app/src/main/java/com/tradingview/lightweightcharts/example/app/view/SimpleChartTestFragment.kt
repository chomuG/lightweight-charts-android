package com.tradingview.lightweightcharts.example.app.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tradingview.lightweightcharts.api.options.models.ChartOptions
import com.tradingview.lightweightcharts.api.options.models.*
import com.tradingview.lightweightcharts.api.series.enums.SeriesType
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.chart.models.color.toIntColor
import com.tradingview.lightweightcharts.example.app.viewmodel.ProfessionalChartTestViewModel
import com.tradingview.lightweightcharts.view.ChartsView
import com.tradingview.lightweightcharts.example.app.view.util.ITitleFragment
import kotlinx.coroutines.launch

class SimpleChartTestFragment : Fragment(), ITitleFragment {

    override val fragmentTitleRes: Int = 0 // 간단한 제목

    private val viewModel: ProfessionalChartTestViewModel by viewModels()
    private var chartsView: ChartsView? = null
    
    // 차트 시리즈 참조
    private var candlestickSeries: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = ChartsView(requireContext())
        chartsView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupChart()
        observeViewModel()
        
        // 테스트 데이터 생성
        viewModel.generateTestData()
    }

    private fun setupChart() {
        // 차트 기본 설정 (다크 테마)
        val chartOptions = ChartOptions().apply {
            layout = LayoutOptions().apply {
                background = SolidColor("#1e1e1e".toIntColor())
                textColor = "#ffffff".toIntColor()
            }
            grid = GridOptions().apply {
                vertLines = GridLineOptions().apply {
                    color = "#2B2B43".toIntColor()
                }
                horzLines = GridLineOptions().apply {
                    color = "#2B2B43".toIntColor()
                }
            }
        }

        chartsView?.api?.applyOptions(chartOptions)

        // 메인 캔들스틱 시리즈 생성
        chartsView?.api?.addCandlestickSeries { series ->
            candlestickSeries = series
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                // 캔들스틱 데이터 관찰
                launch {
                    viewModel.candlestickData.collect { data ->
                        if (data.isNotEmpty()) {
                            candlestickSeries?.setData(data)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chartsView = null
    }
}