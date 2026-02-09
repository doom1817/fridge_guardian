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

initDashboard();
