(function () {
  const token = localStorage.getItem("fg_token") || sessionStorage.getItem("fg_token");
  if (!token) {
    return;
  }

  document.addEventListener("DOMContentLoaded", initExpiringAlert);

  async function initExpiringAlert() {
    injectExpiringAlert();
    await refreshExpiringAlert();
    window.setInterval(refreshExpiringAlert, 60000);
  }

  function injectExpiringAlert() {
    if (document.getElementById("globalExpiringAlert")) {
      return;
    }

    const wrapper = document.createElement("aside");
    wrapper.id = "globalExpiringAlert";
    wrapper.className = "global-expiring-alert";
    wrapper.innerHTML = `
      <button type="button" class="global-expiring-trigger" id="globalExpiringTrigger" aria-expanded="false">
        <span class="global-expiring-icon">
          <i class="bi bi-alarm-fill"></i>
        </span>
        <span class="global-expiring-copy">
          <span class="global-expiring-title">临期食品警报</span>
          <span class="global-expiring-subtitle" id="globalExpiringSubtitle">正在检查库存...</span>
        </span>
        <span class="global-expiring-count" id="globalExpiringCount">0</span>
      </button>
      <div class="global-expiring-panel" id="globalExpiringPanel" hidden>
        <div class="global-expiring-panel-head">
          <div>
            <div class="global-expiring-panel-title">最近 3 天内到期</div>
            <small class="text-muted">切换页面也会持续提醒</small>
          </div>
          <a href="/food/list-page" class="global-expiring-link">查看库存</a>
        </div>
        <div class="global-expiring-list" id="globalExpiringList">
          <div class="global-expiring-empty">正在加载...</div>
        </div>
      </div>
    `;

    document.body.appendChild(wrapper);

    const trigger = document.getElementById("globalExpiringTrigger");
    trigger.addEventListener("click", () => {
      const panel = document.getElementById("globalExpiringPanel");
      const expanded = trigger.getAttribute("aria-expanded") === "true";
      trigger.setAttribute("aria-expanded", String(!expanded));
      panel.hidden = expanded;
    });

    document.addEventListener("click", (event) => {
      if (!wrapper.contains(event.target)) {
        trigger.setAttribute("aria-expanded", "false");
        document.getElementById("globalExpiringPanel").hidden = true;
      }
    });
  }

  async function refreshExpiringAlert() {
    try {
      const response = await fetch("/api/food/expiring?days=3", {
        headers: { Authorization: token }
      });
      if (response.status === 401) {
        return;
      }

      const result = await response.json();
      if (result.code !== 200) {
        renderAlertState([], "临期提醒加载失败");
        return;
      }

      renderAlertState(result.data || []);
    } catch (error) {
      renderAlertState([], "临期提醒暂时不可用");
    }
  }

  function renderAlertState(items, customSubtitle) {
    const count = items.length;
    const countNode = document.getElementById("globalExpiringCount");
    const subtitleNode = document.getElementById("globalExpiringSubtitle");
    const listNode = document.getElementById("globalExpiringList");
    const wrapper = document.getElementById("globalExpiringAlert");
    if (!countNode || !subtitleNode || !listNode || !wrapper) {
      return;
    }

    countNode.textContent = String(count);
    wrapper.classList.toggle("is-urgent", count > 0);
    subtitleNode.textContent = customSubtitle || (count > 0 ? `${count} 种食材需要留意` : "目前没有临期压力");

    if (count === 0) {
      listNode.innerHTML = '<div class="global-expiring-empty">目前没有临期食材，状态不错。</div>';
      return;
    }

    listNode.innerHTML = items.slice(0, 5).map((item) => {
      const daysLeft = typeof item.daysLeft === "number" ? item.daysLeft : "-";
      const urgentClass = daysLeft <= 1 ? "is-danger" : "is-warning";
      return `
        <div class="global-expiring-item">
          <div>
            <div class="global-expiring-item-name">${escapeHtml(item.name || "未命名食材")}</div>
            <div class="global-expiring-item-meta">${escapeHtml(item.expiryDate || "")}</div>
          </div>
          <span class="global-expiring-days ${urgentClass}">${daysLeft} 天</span>
        </div>
      `;
    }).join("");
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("\"", "&quot;")
      .replaceAll("'", "&#39;");
  }
})();
