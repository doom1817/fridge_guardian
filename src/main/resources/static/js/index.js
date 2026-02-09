console.log("localStorage fg_token:", localStorage.getItem("fg_token"));
console.log("sessionStorage fg_token:", sessionStorage.getItem("fg_token"));

const token =
  localStorage.getItem("fg_token") || sessionStorage.getItem("fg_token");
const user =
  localStorage.getItem("fg_user") || sessionStorage.getItem("fg_user");

console.log("Final token:", token);
console.log("Final user:", user);

if (!token) {
  console.log("=== 没有找到 token，跳转到登录页 ===");
  alert("未登录，请先登录");
  window.location.href = "/login";
} else {
  console.log("=== Token 存在，继续执行 ===");
  document.getElementById("currentUser").textContent = user || "用户";
}

function doLogout() {
  if (confirm("确定要退出登录吗？")) {
    localStorage.removeItem("fg_token");
    window.location.href = "/login";
  }
}

async function fetchWithAuth(url) {
  const res = await fetch(url, {
    headers: { Authorization: token },
  });
  if (res.status === 401) {
    localStorage.removeItem("fg_token");
    window.location.href = "/login";
  }
  return res.json();
}

async function initDashboard() {
  try {
    const statRes = await fetchWithAuth("/api/food/statistics");
    if (statRes.code === 200) {
      const stats = statRes.data;
      animateValue("totalProcessed", 0, stats.total || 0, 1000);
      initChart(stats);
    }
  } catch (e) {
    console.error(e);
  }

  try {
    const expireRes = await fetchWithAuth("/api/food/expiring");
    if (expireRes.code === 200) {
      const list = expireRes.data;
      animateValue("expiringCount", 0, list.length, 1000);

      const tbody = document.getElementById("tableBody");
      tbody.innerHTML = "";

      if (list.length === 0) {
        document.getElementById("emptyState").style.display = "block";
        document.getElementById("expiringTable").style.display = "none";
      } else {
        list.forEach((item) => {
          const tr = document.createElement("tr");
          const isUrgent = item.daysLeft <= 1;
          const badgeClass = isUrgent
            ? "bg-danger text-white shadow-sm"
            : "bg-warning text-dark";
          const icon = isUrgent
            ? '<i class="bi bi-exclamation-circle-fill me-1"></i>'
            : "";

          tr.innerHTML = `
            <td>
              <div class="d-flex align-items-center">
                <div class="rounded-circle bg-light d-flex align-items-center justify-content-center me-3" style="width:40px;height:40px;">
                  <i class="bi bi-basket text-secondary"></i>
                </div>
                <div>
                  <div class="fw-bold text-dark">${item.name}</div>
                  <small class="text-muted" style="font-size:0.75rem">${item.storageLocation || "冷藏"}</small>
                </div>
              </div>
            </td>
            <td class="text-muted fw-medium">${item.expiryDate}</td>
            <td class="text-end">
              <span class="badge-pill ${badgeClass}">
                ${icon}${item.daysLeft} 天
              </span>
            </td>
          `;
          tbody.appendChild(tr);
        });
      }
    }
  } catch (e) {
    console.error(e);
  }
  await initAiCharts();
}

function animateValue(id, start, end, duration) {
  const obj = document.getElementById(id);
  let startTimestamp = null;
  const step = (timestamp) => {
    if (!startTimestamp) startTimestamp = timestamp;
    const progress = Math.min((timestamp - startTimestamp) / duration, 1);
    obj.innerHTML = Math.floor(progress * (end - start) + start);
    if (progress < 1) {
      window.requestAnimationFrame(step);
    }
  };
  window.requestAnimationFrame(step);
}

function initChart(stats) {
  const chart = echarts.init(document.getElementById("statisticsChart"));
  const option = {
    color: ["#00b894", "#ff7675"],
    tooltip: {
      trigger: "item",
      backgroundColor: "rgba(255, 255, 255, 0.9)",
      borderWidth: 0,
      textStyle: { color: "#2d3436" },
      formatter: "{b}: <b>{c}</b> ({d}%)",
    },
    legend: { bottom: "0", itemGap: 20 },
    series: [
      {
        name: "处理状态",
        type: "pie",
        radius: ["50%", "75%"],
        center: ["50%", "45%"],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: "#fff",
          borderWidth: 2,
        },
        label: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 10,
          label: {
            show: true,
            fontSize: 16,
            fontWeight: "bold",
            color: "#2d3436",
          },
        },
        data: [
          { value: stats.consumed || 0, name: "健康食用" },
          { value: stats.wasted || 0, name: "遗憾浪费" },
        ],
      },
    ],
  };
  chart.setOption(option);
  window.addEventListener("resize", () => chart.resize());
}
// ================= 新增：AI 图表逻辑 =================
async function initAiCharts() {
  // 1. 获取 Token 趋势数据 (渲染堆叠柱状图)
  try {
    const res = await fetchWithAuth("/api/ai/token-trend?days=7");
    if (res.code === 200) {
      renderTokenChart(res.data);
    }
  } catch (e) { console.error("加载AI趋势失败", e); }

  // 2. 获取统计概览 (渲染仪表盘 & 更新数字)
  try {
    const res = await fetchWithAuth("/api/ai/statistics");
    if (res.code === 200) {
      const data = res.data;
      // 更新右上角的总消耗徽章
      const total = data.totalTokens || 0;
      document.getElementById("totalTokenBadge").innerText = "总消耗: " + total.toLocaleString();

      // 更新底部文字指标
      document.getElementById("statLatency").innerText = Math.round(data.avgLatency || 0) + "ms";
      document.getElementById("statFail").innerText = data.failedCalls || 0;

      // 渲染仪表盘
      renderGaugeChart(data.successRate || 0);
    }
  } catch (e) { console.error("加载AI统计失败", e); }
}

// 渲染 Token 堆叠柱状图
function renderTokenChart(dataList) {
  const chartDom = document.getElementById("tokenTrendChart");
  if (!chartDom) return; // 防止页面没加载完报错
  const chart = echarts.init(chartDom);

  const dates = dataList.map(item => item.date.substring(5)); // 只显示 MM-dd
  const promptTokens = dataList.map(item => item.promptTokens);
  const completionTokens = dataList.map(item => item.completionTokens);

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }, // 鼠标悬停显示阴影
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      textStyle: { color: '#2d3436' }
    },
    legend: { bottom: 0, data: ['提问消耗', '回答消耗'] },
    grid: { left: '3%', right: '4%', bottom: '10%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#b2bec3' } }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#eee' } }
    },
    series: [
      {
        name: '提问消耗',
        type: 'bar',
        stack: 'total', // 堆叠标识，必须一致
        itemStyle: { color: '#74b9ff' },
        data: promptTokens
      },
      {
        name: '回答消耗',
        type: 'bar',
        stack: 'total', // 堆叠标识，必须一致
        itemStyle: { color: '#a29bfe', borderRadius: [5, 5, 0, 0] }, // 只有最上面的圆角
        data: completionTokens
      }
    ]
  };
  chart.setOption(option);
  window.addEventListener("resize", () => chart.resize());
}

// 渲染成功率仪表盘
function renderGaugeChart(rate) {
  const chartDom = document.getElementById("successRateChart");
  if (!chartDom) return;
  const chart = echarts.init(chartDom);

  // 颜色逻辑：>90%绿色，>60%黄色，其余红色
  const color = rate >= 90 ? '#00b894' : (rate >= 60 ? '#fdcb6e' : '#ff7675');

  const option = {
    series: [{
      type: 'gauge',
      startAngle: 180, // 半圆仪表盘
      endAngle: 0,
      min: 0,
      max: 100,
      splitNumber: 1,
      radius: '100%',
      center: ['50%', '75%'], // 调整位置防止底部留白太多
      axisLine: {
        lineStyle: {
          width: 12,
          color: [[1, '#dfe6e9']] // 背景灰色
        }
      },
      progress: {
        show: true,
        width: 12,
        itemStyle: { color: color } // 动态颜色
      },
      pointer: { show: false }, // 不显示指针，只显示进度条
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      detail: {
        valueAnimation: true,
        offsetCenter: [0, '-15%'],
        fontSize: 28,
        fontWeight: 'bold',
        formatter: '{value}%',
        color: '#2d3436'
      },
      data: [{ value: rate }]
    }]
  };
  chart.setOption(option);
  window.addEventListener("resize", () => chart.resize());
}
initDashboard();
