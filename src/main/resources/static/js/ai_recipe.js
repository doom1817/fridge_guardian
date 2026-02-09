const token = localStorage.getItem("fg_token");
if (!token) window.location.href = "/login";

fetch("/api/food/list", {
  headers: { "Authorization": token }
})
  .then((res) => {
    if (res.status === 401) window.location.href = "/login";
    return res.json();
  })
  .then((res) => {
    const list = document.getElementById("foodSelectionList");
    list.innerHTML = "";

    if (!res.data || res.data.length === 0) {
      list.innerHTML = `<div class="text-center text-muted py-5">冰箱空空如也<br><a href="/food/add-page" class="text-success">去买菜</a></div>`;
      return;
    }

    res.data.forEach((food) => {
      let daysText = "";
      let daysClass = "text-muted";

      if (food.daysLeft < 0) {
        daysText = `已过期 ${Math.abs(food.daysLeft)} 天`;
        daysClass = "text-danger fw-bold";
      } else if (food.daysLeft === 0) {
        daysText = "今天内过期 ⚠️";
        daysClass = "text-danger fw-bold";
      } else if (food.daysLeft === 1) {
        daysText = "明天过期";
        daysClass = "text-warning fw-bold";
      } else if (food.daysLeft === 2) {
        daysText = "后天过期";
        daysClass = "text-warning";
      } else {
        daysText = `${food.daysLeft} 天后过期`;
      }

      const item = document.createElement("div");
      item.className = "check-card";
      item.innerHTML = `
        <div class="check-icon"><i class="bi bi-check-lg"></i></div>
        <div class="flex-grow-1">
          <div class="fw-bold text-dark">${food.name}</div>
          <small class="${daysClass}" style="font-size:0.75rem">
            ${daysText} · ${food.quantity}${food.unit||''}
          </small>
        </div>
        <input type="checkbox" value="${food.id}">
      `;

      item.onclick = (e) => {
        if (e.target.tagName === 'INPUT') return;

        const checkbox = item.querySelector("input");
        checkbox.checked = !checkbox.checked;

        if (checkbox.checked) {
          item.classList.add("selected");
        } else {
          item.classList.remove("selected");
        }
      };

      list.appendChild(item);
    });
  });

function generateRecipe() {
  const ids = Array.from(document.querySelectorAll("input:checked")).map(i => i.value);

  if (ids.length === 0) {
    const btn = document.querySelector('.btn-magic');
    btn.classList.add('bg-danger');
    btn.innerText = "请至少选一个食材！";
    setTimeout(() => {
      btn.classList.remove('bg-danger');
      btn.innerHTML = '<i class="bi bi-magic me-2"></i> 开始生成食谱';
    }, 1500);
    return;
  }

  document.getElementById("idleState").classList.add("d-none");
  document.getElementById("recipeResult").classList.add("d-none");
  document.getElementById("loadingState").classList.remove("d-none");

  fetch("/api/ai/generate-recipe", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": token
    },
    body: JSON.stringify(ids),
  })
    .then((res) => res.json())
    .then((res) => {
      document.getElementById("loadingState").classList.add("d-none");

      if (res.code === 200) {
        const resultBox = document.getElementById("recipeResult");
        resultBox.classList.remove("d-none");
        resultBox.innerHTML = marked.parse(res.data.content);
      } else {
        alert("生成失败：" + res.message);
        document.getElementById("idleState").classList.remove("d-none");
      }
    })
    .catch(err => {
      alert("网络错误，请稍后再试");
      document.getElementById("loadingState").classList.add("d-none");
      document.getElementById("idleState").classList.remove("d-none");
    });
}
