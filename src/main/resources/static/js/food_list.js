const token = localStorage.getItem("fg_token");
if(!token) window.location.href = "/login";

async function loadData() {
  try {
    const res = await fetch("/api/food/list", {
      headers: { "Authorization": token }
    });
    if(res.status === 401) {
      window.location.href = "/login";
      return;
    }
    const result = await res.json();

    if(result.code === 200) {
      renderTable(result.data);
      document.getElementById("totalCount").textContent = result.data.length;
    }
  } catch(e) {
    console.error("加载失败", e);
  }
}

function renderTable(list) {
  const tbody = document.getElementById("tableBody");
  const emptyState = document.getElementById("emptyState");

  tbody.innerHTML = "";

  if (!list || list.length === 0) {
    emptyState.classList.remove("d-none");
    return;
  } else {
    emptyState.classList.add("d-none");
  }

  let delayIndex = 0;

  list.forEach(food => {
    const tr = document.createElement("tr");
    tr.className = "food-row";
    tr.style.animation = `fadeInUp 0.5s ease forwards ${delayIndex * 0.05}s`;
    tr.style.opacity = "0";

    let dayClass = 'badge-fresh';
    let icon = '<i class="bi bi-shield-check me-1"></i>';
    let dayText = `${food.daysLeft} 天后过期`;

    if (food.daysLeft < 0) {
      dayClass = 'badge-danger';
      icon = '<i class="bi bi-exclamation-triangle-fill me-1"></i>';
      dayText = `已过期 ${Math.abs(food.daysLeft)} 天`;
    } else if (food.daysLeft <= 3) {
      dayClass = 'badge-warning';
      icon = '<i class="bi bi-clock-history me-1"></i>';
      dayText = `剩 ${food.daysLeft} 天`;
    }

    const locIcon = {
      'FRIDGE': 'bi-snow',
      'FREEZER': 'bi-box-seam',
      'PANTRY': 'bi-sun'
    }[food.storageLocation] || 'bi-geo-alt';

    tr.innerHTML = `
      <td>
        <div class="d-flex align-items-center">
          <div class="rounded-3 bg-white d-flex align-items-center justify-content-center shadow-sm me-3" style="width:48px;height:48px; font-size:1.5rem;">
            <span role="img">🥗</span>
          </div>
          <div>
            <div class="fw-bold text-dark" style="font-size:1.05rem;">${food.name}</div>
            <small class="text-muted">${food.quantity || 1} ${food.unit || ''}</small>
          </div>
        </div>
      </td>
      <td>
        <span class="text-secondary fw-medium">
          <i class="bi ${locIcon} me-1"></i> ${food.storageLocation || '冷藏'}
        </span>
      </td>
      <td>
        <div class="d-flex flex-column">
          <span class="status-badge ${dayClass}">
            ${icon} ${dayText}
          </span>
          <small class="text-muted mt-1 ms-1" style="font-size:0.75rem">${food.expiryDate}</small>
        </div>
      </td>
      <td class="text-end">
        <button class="action-btn btn-eat" onclick="updateStatus(${food.id}, 1)" title="吃掉了">
          <i class="bi bi-check-lg"></i>
        </button>
        <button class="action-btn btn-waste" onclick="updateStatus(${food.id}, 2)" title="浪费了">
          <i class="bi bi-trash"></i>
        </button>
      </td>
    `;
    tbody.appendChild(tr);
    delayIndex++;
  });
}

function updateStatus(id, status) {
  if (confirm(status === 1 ? "确认已食用？" : "确认不得不丢弃？")) {
    fetch(`/api/food/${id}/status?status=${status}`, {
      method: "PUT",
      headers: { "Authorization": token }
    })
      .then((res) => res.json())
      .then((res) => {
        if (res.code === 200) loadData();
      });
  }
}

function filterTable() {
  const filter = document.getElementById("searchInput").value.toLowerCase();
  const rows = document.getElementById("tableBody").getElementsByTagName("tr");
  for (let row of rows) {
    const text = row.textContent.toLowerCase();
    row.style.display = text.includes(filter) ? "" : "none";
  }
}

loadData();
